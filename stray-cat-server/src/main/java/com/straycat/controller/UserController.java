package com.straycat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.dto.AttributionParam;
import com.straycat.entity.PasswordResetRequest;
import com.straycat.entity.SysUser;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.mapper.PasswordResetRequestMapper;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.ShareLinkService;
import com.straycat.service.SysUserService;
import com.straycat.service.UserSecurityService;
import com.straycat.vo.SecurityStatusVO;
import com.straycat.vo.UserProfileVO;
import com.straycat.vo.UserPublicVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    private final SysUserService sysUserService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ContentSecurityService contentSecurityService;
    private final ShareLinkService shareLinkService;
    private final PasswordResetRequestMapper passwordResetRequestMapper;
    private final UserSecurityService userSecurityService;

    @Operation(summary = "微信登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(
            @RequestParam String code,
            @RequestParam(required = false) String sid,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String campaign,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String sceneValue) {
        SysUser user = sysUserService.wxLogin(code,
                AttributionParam.of(sid, channel, campaign, entryType, sceneValue));
        if (user == null) {
            return Result.error("登录失败");
        }
        return Result.success(issueToken(user));
    }

    @Operation(summary = "账号密码注册")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(
            @RequestBody SysUser form,
            @RequestParam(required = false) String sid,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String campaign,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String sceneValue) {
        SysUser user = sysUserService.register(form.getUsername(), form.getPassword(),
                AttributionParam.of(sid, channel, campaign, entryType, sceneValue));
        return Result.success(issueToken(user));
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login/password")
    public Result<Map<String, Object>> loginByPassword(@RequestBody SysUser form) {
        SysUser user = sysUserService.loginByPassword(form.getUsername(), form.getPassword());
        return Result.success(issueToken(user));
    }

    @Operation(summary = "忘记密码：提交找回申请（免登、限频、不暴露账号是否存在）")
    @PostMapping("/forgot-password/apply")
    public Result<?> applyPasswordReset(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String username = str(body.get("username"));
        String nickname = str(body.get("nickname"));
        String phone = str(body.get("phone"));
        String reason = str(body.get("reason"));

        if (username == null || username.isBlank()
                || nickname == null || nickname.isBlank()
                || phone == null || phone.isBlank()) {
            return Result.error("请填写账号、昵称和手机号");
        }
        if (!PHONE_PATTERN.matcher(phone.trim()).matches()) {
            return Result.error("手机号格式不正确");
        }
        if (reason != null && reason.length() > 300) {
            return Result.error("情况说明不能超过300字");
        }

        // 限频：同手机号 24 小时内最多 3 次（idx_phone_created）
        LambdaQueryWrapper<PasswordResetRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PasswordResetRequest::getPhone, phone.trim())
                .ge(PasswordResetRequest::getCreatedAt, LocalDateTime.now().minusHours(24));
        if (passwordResetRequestMapper.selectCount(wrapper) >= 3) {
            return Result.error("提交过于频繁，请 24 小时后重试");
        }

        PasswordResetRequest apply = new PasswordResetRequest();
        apply.setUsername(username.trim());
        apply.setNickname(nickname.trim());
        apply.setPhone(phone.trim());
        apply.setReason(reason == null ? null : reason.trim());
        apply.setStatus(0);
        apply.setIp(clientIp(request));
        apply.setCreatedAt(LocalDateTime.now());
        passwordResetRequestMapper.insert(apply);

        // 统一响应，不查库校验 username，避免枚举账号
        return Result.success("申请已提交，请等待联系", null);
    }

    @Operation(summary = "邮箱验证码自助重置密码（免登；管理员在后台发码后使用）")
    @PostMapping("/reset-password-by-code")
    public Result<?> resetPasswordByCode(@RequestBody Map<String, Object> body) {
        userSecurityService.resetPasswordByCode(
                str(body.get("username")),
                str(body.get("code")),
                str(body.get("newPassword")));
        return Result.success("密码已设置，请使用新密码登录", null);
    }

    @Operation(summary = "账号密码用户绑定微信（code 来自 wx.login）")
    @PostMapping("/bindWechat")
    public Result<UserProfileVO> bindWechat(
            @RequestParam String code,
            HttpServletRequest request,
            @RequestParam(required = false) String sid,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String sceneValue) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        SysUser user = sysUserService.bindWechat(userId, code);
        // 老账号绑定微信不覆盖首触来源，只补一条可归属的访问记录
        if (sid != null && !sid.isBlank()) {
            shareLinkService.recordVisit(sid, userId, false, entryType, sceneValue);
        }
        return Result.success(UserProfileVO.of(user));
    }

    @Operation(summary = "账号安全：自主修改密码（成功后踢掉全部会话）")
    @PostMapping("/change-password")
    public Result<?> changePassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        userSecurityService.changePassword(userId, str(body.get("currentPassword")),
                str(body.get("newPassword")), str(body.get("confirmPassword")));
        return Result.success("密码已修改，请重新登录", null);
    }

    @Operation(summary = "账号安全：查询当前账号的安全状态（邮箱脱敏）")
    @GetMapping("/security-status")
    public Result<SecurityStatusVO> securityStatus(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(userSecurityService.securityStatus(userId));
    }

    @Operation(summary = "账号安全：发送邮箱验证码（60 秒 1 次、同邮箱 24 小时 10 次）")
    @PostMapping("/email/send-code")
    public Result<?> sendEmailCode(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        userSecurityService.sendEmailCode(userId, str(body.get("email")), str(body.get("scene")));
        return Result.success("验证码已发送", null);
    }

    @Operation(summary = "账号安全：校验验证码并绑定安全邮箱")
    @PostMapping("/email/bind")
    public Result<?> bindEmail(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        userSecurityService.bindEmail(userId, str(body.get("email")), str(body.get("code")));
        return Result.success("邮箱绑定成功", null);
    }

    @Operation(summary = "账号安全：校验当前密码后解绑安全邮箱")
    @PostMapping("/email/unbind")
    public Result<?> unbindEmail(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        userSecurityService.unbindEmail(userId, str(body.get("password")));
        return Result.success("邮箱已解绑", null);
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
    }

    private Map<String, Object> issueToken(SysUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("token:" + token, user.getId(), 7, TimeUnit.DAYS);
        // token 反向索引：重置密码时按 userId 取出全部 token 逐个删除实现踢下线；与正向映射同为 7 天过期
        String tokenSetKey = "user:tokens:" + user.getId();
        redisTemplate.opsForSet().add(tokenSetKey, token);
        redisTemplate.expire(tokenSetKey, 7, TimeUnit.DAYS);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        // 只下发白名单字段，实体上的 openid/unionId/时间戳等不要跟着一起出去
        data.put("user", UserProfileVO.of(user));
        return data;
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/current")
    public Result<UserProfileVO> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(UserProfileVO.of(user));
    }

    @Operation(summary = "获取用户公开信息")
    @GetMapping("/info")
    public Result<UserPublicVO> getInfo(@RequestParam Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.success(null);
        }
        // 用独立 VO 做字段白名单：对外只暴露展示字段，手机号/证件号等即使将来被写入也不会从这里流出
        UserPublicVO safe = new UserPublicVO();
        safe.setId(user.getId());
        safe.setNickname(user.getNickname());
        safe.setAvatarUrl(user.getAvatarUrl());
        safe.setRole(user.getRole());
        return Result.success(safe);
    }

    @Operation(summary = "更新当前用户资料")
    @PutMapping("/info")
    public Result<?> updateInfo(@RequestBody SysUser form, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        // 只允许改昵称和头像，role/status/coinBalance 一律由服务端掌控
        String nickname = trimTo(form.getNickname(), 30);
        contentSecurityService.check(userId, ContentSecurityService.SCENE_PROFILE, nickname);

        SysUser update = new SysUser();
        update.setId(userId);
        update.setNickname(nickname);
        update.setAvatarUrl(trimTo(form.getAvatarUrl(), 255));
        update.setUpdatedAt(LocalDateTime.now());
        sysUserService.updateById(update);
        return Result.success(UserProfileVO.of(sysUserService.getById(userId)));
    }

    @Operation(summary = "授予或撤销角色")
    @PutMapping("/{userId}/role")
    @RequireRole(3)
    public Result<?> grantRole(@PathVariable Long userId, @RequestBody SysUser form, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        sysUserService.grantRole(userId, operatorId, form.getRole());
        return Result.success();
    }

    @Operation(summary = "注销账号")
    @PostMapping("/deactivate")
    public Result<?> deactivate(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        if (sysUserService.getById(userId) == null) {
            return Result.error(ResultCode.USER_NOT_FOUND);
        }
        // 保留投喂/打赏等行为记录用于对账，只清除可识别个人信息并禁用账号
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, userId)
                .set(SysUser::getOpenid, null)
                .set(SysUser::getUsername, null)
                .set(SysUser::getPassword, null)
                .set(SysUser::getSessionKey, null)
                .set(SysUser::getUnionId, null)
                .set(SysUser::getNickname, "已注销用户")
                .set(SysUser::getAvatarUrl, null)
                .set(SysUser::getPhone, null)
                .set(SysUser::getRealName, null)
                .set(SysUser::getIdCard, null)
                .set(SysUser::getStatus, 0)
                .set(SysUser::getUpdatedAt, LocalDateTime.now());
        sysUserService.update(wrapper);

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            redisTemplate.delete("token:" + authHeader.substring(7));
        }
        return Result.success("账号已注销", null);
    }

    private String trimTo(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
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
}
