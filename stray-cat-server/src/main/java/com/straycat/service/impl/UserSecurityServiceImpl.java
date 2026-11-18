package com.straycat.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.ResultCode;
import com.straycat.entity.SysUser;
import com.straycat.entity.UserLoginSecurity;
import com.straycat.entity.UserSecurityEvent;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.SysUserMapper;
import com.straycat.mapper.UserLoginSecurityMapper;
import com.straycat.mapper.UserSecurityEventMapper;
import com.straycat.service.SecurityMailSender;
import com.straycat.service.UserSecurityService;
import com.straycat.vo.SecurityStatusVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 账号安全核心实现。
 *
 * <p>计数维度为 username，账号不存在也照常计数，避免枚举账号时返回差异化提示。
 * 并发下用「INSERT IGNORE + SELECT ... FOR UPDATE」MySQL 行锁保证计数不丢失。
 * 失败计数写入后要抛业务异常把错误码带给前端，因此相关方法都声明
 * noRollbackFor = BusinessException，让计数与锁定状态随异常一起提交。
 */
@Slf4j
@Service
public class UserSecurityServiceImpl implements UserSecurityService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int FIRST_CYCLE_LIMIT = 5;
    private static final int POST_UNLOCK_LIMIT = 3;
    private static final int DAY_CODE_LIMIT = 10;
    private static final DateTimeFormatter LOCK_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private UserLoginSecurityMapper loginSecurityMapper;

    @Resource
    private UserSecurityEventMapper eventMapper;

    @Resource
    private SysUserMapper userMapper;

    // 注意：不能命名为 mailSender——引入 spring-boot-starter-mail 后 Spring Boot 会注册同名
    // JavaMailSenderImpl bean，@Resource 按字段名匹配会撞车导致启动失败（BeanNotOfRequiredTypeException）。
    @Resource
    private SecurityMailSender securityMailSender;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "redisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    // ------------------------------------------------------------------ 登录锁定状态机

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public void checkPasswordLoginAllowed(String username) {
        UserLoginSecurity row = lockRow(username, null, false);
        if (row == null) {
            return;
        }
        int stage = stageOf(row);
        if (stage == 2) {
            throw permanentLocked();
        }
        if (stage == 1) {
            LocalDateTime until = row.getLockUntil();
            if (until != null && until.isAfter(LocalDateTime.now())) {
                throw tempLocked(until);
            }
            // 临时锁已到期：自动降级为正常并清零，本次尝试作为新周期起点。
            // 用 wrapper 显式 SET NULL：updateById 不会更新实体里的 null 字段，
            // 否则 lock_until 会残留旧值，后台列表与 security-status 会拿到过期时间。
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<UserLoginSecurity> downgrade = new LambdaUpdateWrapper<>();
            downgrade.eq(UserLoginSecurity::getId, row.getId())
                    .set(UserLoginSecurity::getLockStage, 0)
                    .set(UserLoginSecurity::getFailedCount, 0)
                    .set(UserLoginSecurity::getLockUntil, null)
                    .set(UserLoginSecurity::getUnlockedAt, now)
                    .set(UserLoginSecurity::getUpdatedAt, now);
            loginSecurityMapper.update(null, downgrade);
        }
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public void checkWechatLoginAllowed(String username) {
        UserLoginSecurity row = findByUsername(username);
        if (row != null && stageOf(row) == 2) {
            throw permanentLocked();
        }
        // 临时锁期间微信登录是独立安全通道，放行（Q2）
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public void recordFailure(String username, Long userId) {
        UserLoginSecurity row = lockRow(username, userId, true);
        if (row == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int stage = stageOf(row);
        if (stage == 2) {
            throw permanentLocked();
        }

        // unlocked_at 非空表示账号经历过解锁，进入「三振」周期，容忍 3 次而非 5 次
        boolean postUnlock = row.getUnlockedAt() != null;
        int limit = postUnlock ? POST_UNLOCK_LIMIT : FIRST_CYCLE_LIMIT;
        int count = row.getFailedCount() == null ? 0 : row.getFailedCount();

        if (stage == 1) {
            LocalDateTime until = row.getLockUntil();
            if (until != null && until.isAfter(now)) {
                // 锁定中重复尝试不再增加失败次数
                throw tempLocked(until);
            }
            // 到期：先在新周期里重置（lock_until 显式置 NULL），再把本次失败作为第 1 次写入
            LambdaUpdateWrapper<UserLoginSecurity> reset = new LambdaUpdateWrapper<>();
            reset.eq(UserLoginSecurity::getId, row.getId())
                    .set(UserLoginSecurity::getLockStage, 0)
                    .set(UserLoginSecurity::getFailedCount, 0)
                    .set(UserLoginSecurity::getLockUntil, null)
                    .set(UserLoginSecurity::getUnlockedAt, now)
                    .set(UserLoginSecurity::getUpdatedAt, now);
            loginSecurityMapper.update(null, reset);
            count = 0;
            postUnlock = true;
            limit = POST_UNLOCK_LIMIT;
        }

        int newCount = count + 1;
        // 写库统一走 wrapper：实体 updateById 会忽略 null，无法清空 lock_until。
        LambdaUpdateWrapper<UserLoginSecurity> update = new LambdaUpdateWrapper<>();
        update.eq(UserLoginSecurity::getId, row.getId())
                .set(UserLoginSecurity::getFailedCount, newCount)
                .set(UserLoginSecurity::getLastFailedAt, now)
                .set(UserLoginSecurity::getUpdatedAt, now);
        if (userId != null) {
            update.set(UserLoginSecurity::getUserId, userId);
        }
        if (count == 0 || row.getFirstFailedAt() == null) {
            update.set(UserLoginSecurity::getFirstFailedAt, now);
        }

        if (newCount < limit) {
            loginSecurityMapper.update(null, update);
            throw new BusinessException(ResultCode.LOGIN_FAILED.getCode(),
                    "账号或密码错误，还可尝试 " + (limit - newCount) + " 次");
        }

        if (!postUnlock) {
            // 首周期第 5 次失败：临时锁 24 小时
            LocalDateTime until = now.plusHours(24);
            update.set(UserLoginSecurity::getLockStage, 1);
            update.set(UserLoginSecurity::getLockUntil, until);
            loginSecurityMapper.update(null, update);
            writeEvent(userId, username, "TEMP_LOCK",
                    "连续 " + newCount + " 次密码校验失败，锁至 " + until.format(LOCK_TIME_FMT));
            sendToUser(userId, "【猫遇猫舍】安全通知：账号已临时保护 24 小时",
                    "您的账号因连续 " + newCount + " 次密码输入错误，已暂停密码登录至 "
                            + until.format(LOCK_TIME_FMT) + "。保护期内可使用微信一键登录。如非本人操作，请留意账号安全。");
            throw tempLocked(until);
        }

        // 新周期第 3 次失败：永久锁
        update.set(UserLoginSecurity::getLockStage, 2);
        update.set(UserLoginSecurity::getLockUntil, null);
        loginSecurityMapper.update(null, update);
        writeEvent(userId, username, "PERMANENT_LOCK", "解锁后连续 " + newCount + " 次密码校验失败");
        sendToUser(userId, "【猫遇猫舍】安全通知：账号已被锁定",
                "您的账号在保护期结束后再次连续 " + newCount + " 次密码输入错误，已被锁定，"
                        + "密码登录与微信登录均暂不可用。请在小程序提交「忘记密码」申请或联系管理员处理。");
        throw permanentLocked();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordSuccess(String username, Long userId) {
        UserLoginSecurity row = findByUsername(username);
        if (row == null) {
            return;
        }
        // 永久锁只能由管理员恢复，不因一次密码验证成功而自解锁
        if (stageOf(row) == 2) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // 关键：unlocked_at 兼作「已进入三振观察周期」标记。一次成功验证代表观察期结束，
        // 必须显式清空，否则 24h 自动解锁 / 管理员解锁后用户正常登录过，
        // 之后只要偶尔输错 3 次就会被永久锁（误伤）。审计由 ADMIN_UNLOCK 事件独立保留。
        // 用 wrapper 才能让 null 真正写进库（updateById 会忽略实体的 null 字段）。
        LambdaUpdateWrapper<UserLoginSecurity> update = new LambdaUpdateWrapper<>();
        update.eq(UserLoginSecurity::getId, row.getId())
                .set(UserLoginSecurity::getFailedCount, 0)
                .set(UserLoginSecurity::getLockStage, 0)
                .set(UserLoginSecurity::getLockUntil, null)
                .set(UserLoginSecurity::getFirstFailedAt, null)
                .set(UserLoginSecurity::getLastFailedAt, null)
                .set(UserLoginSecurity::getUnlockedAt, null)
                .set(UserLoginSecurity::getUpdatedAt, now);
        if (userId != null) {
            update.set(UserLoginSecurity::getUserId, userId);
        }
        loginSecurityMapper.update(null, update);
    }

    // ------------------------------------------------------------------ 改密 / 邮箱

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public void changePassword(Long userId, String currentPassword, String newPassword, String confirmPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        String username = user.getUsername();
        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BusinessException(ResultCode.PASSWORD_CONFIRM_MISMATCH);
        }
        if (newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "新密码长度需为6-20位");
        }

        String oldHash = user.getPassword();
        if (oldHash == null || currentPassword == null || !BCrypt.checkpw(currentPassword, oldHash)) {
            // 改密输错当前密码与登录共用一套失败计数（Q3）
            recordFailure(username, userId);
            return;
        }
        if (BCrypt.checkpw(newPassword, oldHash)) {
            throw new BusinessException(ResultCode.PASSWORD_SAME);
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(BCrypt.hashpw(newPassword));
        update.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(update);

        recordSuccess(username, userId);
        kickOut(userId);
        writeEvent(userId, username, "PWD_CHANGE", "用户自主修改密码，已踢下线");
        sendToUser(userId, "【猫遇猫舍】安全通知：密码已修改",
                "您的账号于 " + LocalDateTime.now().format(LOCK_TIME_FMT)
                        + " 完成密码修改，所有设备已退出登录。如非本人操作，请立即联系管理员处理。");
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public SecurityStatusVO securityStatus(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        UserLoginSecurity security = loginSecurityMapper.selectOne(
                new LambdaQueryWrapper<UserLoginSecurity>().eq(UserLoginSecurity::getUserId, userId));

        SecurityStatusVO vo = new SecurityStatusVO();
        vo.setLockStage(security == null ? 0 : stageOf(security));
        vo.setLockUntil(security == null ? null : security.getLockUntil());
        vo.setEmail(maskEmail(user.getEmail()));
        vo.setEmailVerified(user.getEmailVerified() == null ? 0 : user.getEmailVerified());
        vo.setHasPassword(user.getPassword() != null && !user.getPassword().isBlank());
        vo.setUsername(user.getUsername());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public void sendEmailCode(Long userId, String email, String scene) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String limitKey = "email:code:limit:" + normalized;
        String dayKey = "email:code:day:" + normalized;

        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            throw new BusinessException(ResultCode.EMAIL_SEND_TOO_FAST);
        }
        String dayValue = stringRedisTemplate.opsForValue().get(dayKey);
        if (dayValue != null) {
            try {
                if (Integer.parseInt(dayValue) >= DAY_CODE_LIMIT) {
                    throw new BusinessException(ResultCode.EMAIL_SEND_TOO_FAST.getCode(),
                            "该邮箱今日验证码发送次数已达上限，请明天再试");
                }
            } catch (NumberFormatException ignored) {
                // 脏数据按 0 次处理，不阻断用户
            }
        }
        Long dayCount = stringRedisTemplate.opsForValue().increment(dayKey);
        if (dayCount != null && dayCount == 1L) {
            stringRedisTemplate.expire(dayKey, Duration.ofHours(24));
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set("emailcode:" + normalized, code, Duration.ofMinutes(10));
        stringRedisTemplate.opsForValue().set(limitKey, "1", Duration.ofSeconds(60));

        SysUser user = userMapper.selectById(userId);
        writeEvent(userId, user == null ? null : user.getUsername(), "EMAIL_CODE_SEND",
                "请求邮箱验证码（scene=" + (scene == null ? "BIND" : scene) + "）");

        try {
            securityMailSender.send(normalized, "【猫遇猫舍】安全通知：邮箱验证验证码",
                    "您正在绑定安全邮箱，验证码为：" + code
                            + "。10 分钟内有效。如非本人操作，请忽略本邮件，不要将验证码告知任何人。");
        } catch (Exception e) {
            // 发信失败不允许把用户卡在 60 秒限频里，清掉本次验证码与限频键，允许立即重发
            stringRedisTemplate.delete("emailcode:" + normalized);
            stringRedisTemplate.delete(limitKey);
            log.warn("验证码邮件发送失败 email={}: {}", normalized, e.getMessage());
            throw new BusinessException(ResultCode.ERROR.getCode(), "验证码邮件发送失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public void bindEmail(Long userId, String email, String code) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String stored = stringRedisTemplate.opsForValue().get("emailcode:" + normalized);
        if (stored == null || !stored.equals(code == null ? null : code.trim())) {
            throw new BusinessException(ResultCode.EMAIL_CODE_INVALID);
        }
        stringRedisTemplate.delete("emailcode:" + normalized);

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setEmail(normalized);
        update.setEmailVerified(1);
        update.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(update);

        writeEvent(userId, user.getUsername(), "EMAIL_BIND", "绑定安全邮箱成功");
        try {
            securityMailSender.send(normalized, "【猫遇猫舍】安全通知：邮箱绑定成功",
                    "您的账号已成功绑定安全邮箱，后续密码修改、账号锁定等重要通知将发送至本邮箱。本邮件不包含任何密码信息。");
        } catch (Exception e) {
            log.warn("邮箱绑定通知发送失败 email={}: {}", normalized, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public void unbindEmail(Long userId, String password) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        String oldHash = user.getPassword();
        if (oldHash == null || password == null || !BCrypt.checkpw(password, oldHash)) {
            recordFailure(user.getUsername(), userId);
            return;
        }
        recordSuccess(user.getUsername(), userId);

        SysUser update = new SysUser();
        update.setId(userId);
        update.setEmail(null);
        update.setEmailVerified(0);
        update.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(update);
        writeEvent(userId, user.getUsername(), "EMAIL_UNBIND", "解绑安全邮箱");
    }

    // ------------------------------------------------------------------ 管理端

    /** 邮件重置码：TTL 30 分钟、错 5 次作废、单账号 24h 最多发 3 次 */
    private static final int RESET_CODE_TRIES_LIMIT = 5;
    private static final int RESET_CODE_DAY_LIMIT = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String issuePasswordResetCode(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getEmail() == null || user.getEmail().isBlank()
                || !Integer.valueOf(1).equals(user.getEmailVerified())) {
            throw new BusinessException(ResultCode.EMAIL_NOT_BOUND);
        }
        String username = user.getUsername();
        String dayKey = "pwdreset:day:" + username;
        String dayValue = stringRedisTemplate.opsForValue().get(dayKey);
        if (dayValue != null) {
            try {
                if (Integer.parseInt(dayValue) >= RESET_CODE_DAY_LIMIT) {
                    throw new BusinessException(ResultCode.EMAIL_SEND_TOO_FAST.getCode(),
                            "该账号今日重置码申请已达上限，请明天再试或改用临时密码方式");
                }
            } catch (NumberFormatException ignored) {
                // 脏数据按 0 次处理
            }
        }
        Long dayCount = stringRedisTemplate.opsForValue().increment(dayKey);
        if (dayCount != null && dayCount == 1L) {
            stringRedisTemplate.expire(dayKey, Duration.ofHours(24));
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set("pwdreset:" + username, code, Duration.ofMinutes(30));
        // 新码重置错误尝试计数
        stringRedisTemplate.delete("pwdreset:tries:" + username);

        writeEvent(userId, username, "ADMIN_PWD_RESET_CODE",
                "管理员发起邮件重置，重置码已发送至已验证邮箱");
        try {
            securityMailSender.send(user.getEmail(), "【猫遇猫舍】安全通知：密码重置验证码",
                    "管理员已为您的账号发起密码重置。重置验证码为：" + code
                            + "，30 分钟内有效。请在小程序「登录页 → 邮箱验证码重置密码」中使用，"
                            + "验证码仅供您本人设置新密码使用，请勿告知任何人。如非本人操作，请忽略本邮件并联系管理员。");
        } catch (Exception e) {
            // 发信失败清掉码与当日计数，允许立即重新发起
            stringRedisTemplate.delete("pwdreset:" + username);
            stringRedisTemplate.delete(dayKey);
            log.warn("重置码邮件发送失败 userId={}: {}", userId, e.getMessage());
            throw new BusinessException(ResultCode.ERROR.getCode(), "重置码邮件发送失败，请稍后重试");
        }
        return maskEmail(user.getEmail());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public void resetPasswordByCode(String username, String code, String newPassword) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请填写账号");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "新密码长度需为6-20位");
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username.trim());
        SysUser user = userMapper.selectOne(wrapper);
        // 不暴露账号是否存在：账号不存在 / 未设密码 / 未发码统一按验证码错误处理
        if (user == null || user.getPassword() == null || user.getPassword().isBlank()) {
            throw new BusinessException(ResultCode.EMAIL_CODE_INVALID);
        }

        String codeKey = "pwdreset:" + user.getUsername();
        String triesKey = "pwdreset:tries:" + user.getUsername();
        String stored = stringRedisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            throw new BusinessException(ResultCode.EMAIL_CODE_INVALID.getCode(),
                    "重置码已过期或不存在，请联系管理员重新发送");
        }
        String triesValue = stringRedisTemplate.opsForValue().get(triesKey);
        int tries = 0;
        if (triesValue != null) {
            try {
                tries = Integer.parseInt(triesValue);
            } catch (NumberFormatException ignored) {
            }
        }
        if (stored.equals(code == null ? null : code.trim())) {
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(triesKey);
        } else {
            tries++;
            if (tries >= RESET_CODE_TRIES_LIMIT) {
                stringRedisTemplate.delete(codeKey);
                stringRedisTemplate.delete(triesKey);
                throw new BusinessException(ResultCode.EMAIL_CODE_INVALID.getCode(),
                        "重置码错误次数过多已作废，请联系管理员重新发送");
            }
            stringRedisTemplate.opsForValue().set(triesKey, String.valueOf(tries), Duration.ofMinutes(30));
            throw new BusinessException(ResultCode.EMAIL_CODE_INVALID.getCode(),
                    "重置码错误，还可尝试 " + (RESET_CODE_TRIES_LIMIT - tries) + " 次");
        }

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(BCrypt.hashpw(newPassword));
        update.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(update);

        // 自动解锁：未锁账号不打三振标记（clearLock 已处理）
        UserLoginSecurity row = lockRow(user.getUsername(), user.getId(), true);
        boolean wasLocked = row != null && stageOf(row) > 0;
        clearLock(row, user.getId());

        kickOut(user.getId());
        writeEvent(user.getId(), user.getUsername(), "PWD_RESET_BY_CODE",
                "用户凭邮件重置码自助设置新密码" + (wasLocked ? "，锁定已自动解除" : ""));
        sendToUser(user.getId(), "【猫遇猫舍】安全通知：密码重置成功",
                "您的账号已通过邮件重置验证码完成密码设置，所有设备已退出登录"
                        + (wasLocked ? "，账号锁定状态已自动解除" : "") + "。如非本人操作，请立即联系管理员处理。");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminUnlock(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        String username = user.getUsername();
        UserLoginSecurity row = lockRow(username, userId, true);
        clearLock(row, userId);
        writeEvent(userId, username, "ADMIN_UNLOCK", "管理员手动解除锁定");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetUnlock(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        String username = user.getUsername();
        UserLoginSecurity row = lockRow(username, userId, true);
        boolean wasLocked = row != null && stageOf(row) > 0;
        clearLock(row, userId);
        if (wasLocked) {
            writeEvent(userId, username, "ADMIN_PWD_RESET", "管理员重置密码并自动解除锁定");
        }
        sendToUser(userId, "【猫遇猫舍】安全通知：密码已被管理员重置",
                "您的账号密码已由管理员重置" + (wasLocked ? "，账号锁定状态已自动解除" : "")
                        + "。请使用管理员转告的临时密码登录后立即修改。"
                        + "本邮件不包含密码信息，请勿向任何人转述验证码。");
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public Page<UserSecurityEvent> pageEvents(String userKey, String eventType, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<UserSecurityEvent> wrapper = new LambdaQueryWrapper<>();
        if (userKey != null && !userKey.isBlank()) {
            String key = userKey.trim();
            if (key.matches("\\d+")) {
                wrapper.eq(UserSecurityEvent::getUserId, Long.valueOf(key));
            } else {
                wrapper.like(UserSecurityEvent::getUsername, key);
            }
        }
        if (eventType != null && !eventType.isBlank()) {
            wrapper.eq(UserSecurityEvent::getEventType, eventType.trim());
        }
        wrapper.orderByDesc(UserSecurityEvent::getCreatedAt);
        int size = (pageSize == null || pageSize <= 0) ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        return eventMapper.selectPage(new Page<>(pageNum == null || pageNum < 1 ? 1 : pageNum, size), wrapper);
    }

    // ------------------------------------------------------------------ 内部工具

    /** 先占位再对行加排他锁，保证同一账号并发失败不丢计数 */
    private UserLoginSecurity lockRow(String username, Long userId, boolean createIfMissing) {
        if (username == null || username.isBlank()) {
            return null;
        }
        if (createIfMissing) {
            loginSecurityMapper.insertIgnore(username, userId);
        }
        return loginSecurityMapper.selectForUpdate(username);
    }

    private UserLoginSecurity findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return loginSecurityMapper.selectOne(
                new LambdaQueryWrapper<UserLoginSecurity>().eq(UserLoginSecurity::getUsername, username));
    }

    private void clearLock(UserLoginSecurity row, Long userId) {
        if (row == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<UserLoginSecurity> update = new LambdaUpdateWrapper<>();
        update.eq(UserLoginSecurity::getId, row.getId())
                .set(UserLoginSecurity::getLockStage, 0)
                .set(UserLoginSecurity::getFailedCount, 0)
                .set(UserLoginSecurity::getLockUntil, null)
                .set(UserLoginSecurity::getUpdatedAt, now);
        // 只有确实处于锁定状态的账号，解锁后才进入「三振观察周期」。
        // 普通重置密码 / 找回申请（账号本来正常）不能把用户降级成只容忍 3 次失败。
        if (stageOf(row) > 0) {
            update.set(UserLoginSecurity::getUnlockedAt, now);
        } else {
            update.set(UserLoginSecurity::getUnlockedAt, null);
        }
        if (userId != null) {
            update.set(UserLoginSecurity::getUserId, userId);
        }
        loginSecurityMapper.update(null, update);
    }

    private static int stageOf(UserLoginSecurity row) {
        return row.getLockStage() == null ? 0 : row.getLockStage();
    }

    private static BusinessException tempLocked(LocalDateTime until) {
        return new BusinessException(ResultCode.LOGIN_TEMP_LOCKED.getCode(),
                "账号已临时锁定，请于 " + until.format(LOCK_TIME_FMT) + " 后重试");
    }

    private static BusinessException permanentLocked() {
        return new BusinessException(ResultCode.LOGIN_PERMANENT_LOCKED.getCode(),
                "账号已被锁定，请联系管理员重置密码");
    }

    private void writeEvent(Long userId, String username, String eventType, String detail) {
        UserSecurityEvent event = new UserSecurityEvent();
        event.setUserId(userId);
        event.setUsername(username);
        event.setEventType(eventType);
        event.setDetail(detail);
        event.setIp(currentIp());
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    /** 仅对已验证邮箱发送安全通知；发信失败不影响主流程 */
    private void sendToUser(Long userId, String subject, String content) {
        if (userId == null) {
            return;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()
                || !Integer.valueOf(1).equals(user.getEmailVerified())) {
            return;
        }
        try {
            securityMailSender.send(user.getEmail(), subject, content);
        } catch (Exception e) {
            log.warn("安全邮件发送失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 复用 token 反向索引踢掉该用户所有会话 */
    private void kickOut(Long userId) {
        try {
            String setKey = "user:tokens:" + userId;
            Set<Object> tokens = redisTemplate.opsForSet().members(setKey);
            if (tokens != null && !tokens.isEmpty()) {
                for (Object token : tokens) {
                    if (token != null) {
                        redisTemplate.delete("token:" + token);
                    }
                }
            }
            redisTemplate.delete(setKey);
        } catch (Exception e) {
            log.warn("踢下线失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String name = email.substring(0, at);
        String head = name.substring(0, Math.min(3, name.length()));
        return head + "***@" + email.substring(at + 1);
    }

    private static String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
