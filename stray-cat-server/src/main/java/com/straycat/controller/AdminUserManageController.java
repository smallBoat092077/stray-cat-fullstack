package com.straycat.controller;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.AdminOperationLog;
import com.straycat.entity.PasswordResetRequest;
import com.straycat.entity.SysUser;
import com.straycat.entity.UserSecurityEvent;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.mapper.AdminOperationLogMapper;
import com.straycat.mapper.PasswordResetRequestMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.UserSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 后台用户新增 / 重置密码 / 找回申请核实，全部 @RequireRole(3)。
 * 所有写操作落 admin_operation_log；临时密码只在响应出现一次，绝不落库、绝不记日志。
 */
@Slf4j
@Tag(name = "后台用户管理（管理员）")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserManageController {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,50}$");

    /** 大写无 O、小写无 l、数字无 0/1，避免管理员线下转告时念错 */
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper sysUserMapper;
    private final AdminOperationLogMapper operationLogMapper;
    private final PasswordResetRequestMapper resetRequestMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserSecurityService userSecurityService;

    @Operation(summary = "新增用户（管理员）")
    @PostMapping("/create")
    @RequireRole(3)
    public Result<Long> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        String nickname = str(body.get("nickname"));
        Integer role = intVal(body.get("role"));

        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            return Result.error("用户名需为3-50位字母、数字或下划线");
        }
        if (password == null || password.length() < 6 || password.length() > 20) {
            return Result.error("密码长度需为6-20位");
        }
        if (role == null || (role != 0 && role != 1 && role != 3)) {
            return Result.error("角色取值不合法");
        }
        LambdaQueryWrapper<SysUser> dup = new LambdaQueryWrapper<>();
        dup.eq(SysUser::getUsername, username);
        if (sysUserMapper.selectCount(dup) > 0) {
            return Result.error("用户名已存在");
        }
        if (nickname == null || nickname.isBlank()) {
            nickname = username;
        }
        nickname = nickname.trim();
        if (nickname.length() > 50) {
            nickname = nickname.substring(0, 50);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password));
        user.setNickname(nickname);
        user.setRole(role);
        user.setCreditScore(100);
        user.setCoinBalance(0);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);

        String detail = role == 3 ? "新增管理员" : "角色=" + (role == 1 ? "志愿者" : "普通用户");
        writeLog(request, "CREATE_USER", user.getId(), username, detail);
        return Result.success(user.getId());
    }

    @Operation(summary = "重置用户密码（delivery=manual 临时密码一次性返回；delivery=email 发邮件重置验证码，用户自助设新密码）")
    @PostMapping("/{id}/reset-password")
    @RequireRole(3)
    public Result<ResetPasswordVO> resetPassword(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> body,
                                                 HttpServletRequest request) {
        Long operatorId = operatorId(request);
        if (operatorId != null && operatorId.equals(id)) {
            return Result.error(ResultCode.FORBIDDEN.getCode(), "不能重置自己的密码");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        boolean byEmail = body != null && "email".equalsIgnoreCase(str(body.get("delivery")));

        if (byEmail) {
            // 方案 C：只发一次性重置码，不生成临时密码、不改密码、不踢会话。
            // 密码在用户凭验证码自助设置时才变更（resetPasswordByCode 内自动解锁）。
            String maskedEmail = userSecurityService.issuePasswordResetCode(id);
            writeLog(request, "RESET_PASSWORD_EMAIL", id, user.getUsername(),
                    "发送邮件重置验证码至已验证邮箱");
            return Result.success("重置验证码已发送至用户邮箱，30 分钟内有效",
                    new ResetPasswordVO(id, user.getUsername(), null, "email", maskedEmail));
        }

        String temporaryPassword = generateTempPassword();
        updatePassword(id, temporaryPassword);
        kickOut(id);
        // Q7：重置密码时自动解锁，否则用户拿临时密码仍登不上
        userSecurityService.resetUnlock(id);
        writeLog(request, "RESET_PASSWORD", id, user.getUsername(), "重置密码并自动解锁");
        return Result.success("密码已重置，该操作会同时解除账号锁定",
                new ResetPasswordVO(id, user.getUsername(), temporaryPassword, "manual", null));
    }

    @Operation(summary = "解除账号锁定（不改密码、不恢复管理员禁用状态）")
    @PostMapping("/{id}/unlock")
    @RequireRole(3)
    public Result<?> unlock(@PathVariable Long id, HttpServletRequest request) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        userSecurityService.adminUnlock(id);
        writeLog(request, "ADMIN_UNLOCK", id, user.getUsername(), "管理员手动解除账号锁定");
        return Result.success("已解除账号锁定", null);
    }

    @Operation(summary = "安全事件分页查询（操作日志 → 安全事件 Tab）")
    @GetMapping("/security-events")
    @RequireRole(3)
    public Result<Page<UserSecurityEvent>> securityEvents(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(userSecurityService.pageEvents(userId, eventType, pageNum, pageSize));
    }

    @Operation(summary = "操作日志分页查询")
    @GetMapping("/operation-log")
    @RequireRole(3)
    public Result<Page<AdminOperationLog>> operationLog(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LambdaQueryWrapper<AdminOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            wrapper.eq(AdminOperationLog::getAction, action.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String key = keyword.trim();
            wrapper.and(w -> w.like(AdminOperationLog::getOperatorName, key)
                    .or().like(AdminOperationLog::getTargetUsername, key));
        }
        wrapper.orderByDesc(AdminOperationLog::getCreatedAt);
        return Result.success(operationLogMapper.selectPage(page(pageNum, pageSize), wrapper));
    }

    @Operation(summary = "密码找回申请分页查询")
    @GetMapping("/reset-requests")
    @RequireRole(3)
    public Result<Page<PasswordResetRequest>> resetRequests(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LambdaQueryWrapper<PasswordResetRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(PasswordResetRequest::getStatus, status);
        }
        wrapper.orderByDesc(PasswordResetRequest::getCreatedAt);
        return Result.success(resetRequestMapper.selectPage(page(pageNum, pageSize), wrapper));
    }

    @Operation(summary = "核实并重置(status=1) / 忽略(status=2) 找回申请")
    @PostMapping("/reset-request/{id}/handle")
    @RequireRole(3)
    public Result<HandleResultVO> handleResetRequest(@PathVariable Long id,
                                                     @RequestParam Integer status,
                                                     @RequestParam(required = false) String remark,
                                                     HttpServletRequest request) {
        if (status == null || (status != 1 && status != 2)) {
            return Result.error("处理状态不合法");
        }
        PasswordResetRequest req = resetRequestMapper.selectById(id);
        if (req == null) {
            return Result.error("申请不存在");
        }
        if (req.getStatus() != null && req.getStatus() != 0) {
            return Result.error("该申请已处理");
        }

        String temporaryPassword = null;
        if (status == 1) {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUser::getUsername, req.getUsername());
            SysUser user = sysUserMapper.selectOne(wrapper);
            if (user == null) {
                return Result.error("账号不存在");
            }
            temporaryPassword = generateTempPassword();
            updatePassword(user.getId(), temporaryPassword);
            kickOut(user.getId());
            // 核实找回申请并重置时同样自动解锁（Q7）
            userSecurityService.resetUnlock(user.getId());
        }

        Long operatorId = operatorId(request);
        PasswordResetRequest update = new PasswordResetRequest();
        update.setId(id);
        update.setStatus(status);
        update.setOperatorId(operatorId);
        update.setOperatorName(operatorName(operatorId));
        update.setHandledAt(LocalDateTime.now());
        resetRequestMapper.updateById(update);

        String detail = (status == 1 ? "核实重置并电话转告" : "忽略申请")
                + (remark == null || remark.isBlank() ? "" : "：" + remark.trim());
        writeLog(request, "RESET_REQUEST_HANDLE", id, req.getUsername(), detail);
        return Result.success(new HandleResultVO(temporaryPassword, req.getUsername()));
    }

    /** 覆盖 BCrypt 哈希并推送版本时间；明文只在调用方响应里出现 */
    private void updatePassword(Long userId, String temporaryPassword) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(BCrypt.hashpw(temporaryPassword));
        update.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(update);
    }

    /**
     * 踢下线：取反向索引里该用户所有 token，逐个删正向映射，再删 Set。
     * Redis 异常只记日志，不阻塞重置主流程。
     */
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

    private void writeLog(HttpServletRequest request, String action, Long targetUserId,
                          String targetUsername, String detail) {
        Long operatorId = operatorId(request);
        AdminOperationLog logEntity = new AdminOperationLog();
        logEntity.setOperatorId(operatorId);
        logEntity.setOperatorName(operatorName(operatorId));
        logEntity.setAction(action);
        logEntity.setTargetUserId(targetUserId);
        logEntity.setTargetUsername(targetUsername);
        logEntity.setDetail(detail);
        logEntity.setIp(clientIp(request));
        logEntity.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(logEntity);
    }

    private Long operatorId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
    }

    private String operatorName(Long operatorId) {
        if (operatorId == null) {
            return "system";
        }
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null) {
            return String.valueOf(operatorId);
        }
        return operator.getUsername() == null ? String.valueOf(operatorId) : operator.getUsername();
    }

    private static String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer intVal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private <T> Page<T> page(Integer pageNum, Integer pageSize) {
        int size = (pageSize == null || pageSize <= 0) ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        return new Page<>(pageNum == null || pageNum < 1 ? 1 : pageNum, size);
    }

    @Data
    @AllArgsConstructor
    public static class ResetPasswordVO {
        private Long userId;
        private String username;
        /** manual 方式才有值；email 方式为 null（密码由用户凭验证码自助设置） */
        private String temporaryPassword;
        /** email | manual */
        private String delivery;
        /** email 方式返回脱敏邮箱，供后台展示 */
        private String email;
    }

    @Data
    @AllArgsConstructor
    public static class HandleResultVO {
        private String temporaryPassword;
        private String username;
    }
}
