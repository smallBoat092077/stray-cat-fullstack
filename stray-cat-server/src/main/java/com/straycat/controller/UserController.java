package com.straycat.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.SysUser;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.SysUserService;
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

@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserService sysUserService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ContentSecurityService contentSecurityService;

    @Operation(summary = "微信登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestParam String code) {
        SysUser user = sysUserService.wxLogin(code);
        if (user == null) {
            return Result.error("登录失败");
        }
        return Result.success(issueToken(user));
    }

    @Operation(summary = "账号密码注册")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody SysUser form) {
        SysUser user = sysUserService.register(form.getUsername(), form.getPassword());
        return Result.success(issueToken(user));
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login/password")
    public Result<Map<String, Object>> loginByPassword(@RequestBody SysUser form) {
        SysUser user = sysUserService.loginByPassword(form.getUsername(), form.getPassword());
        return Result.success(issueToken(user));
    }

    private Map<String, Object> issueToken(SysUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("token:" + token, user.getId(), 7, TimeUnit.DAYS);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return data;
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/current")
    public Result<SysUser> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }

    @Operation(summary = "获取用户公开信息")
    @GetMapping("/info")
    public Result<SysUser> getInfo(@RequestParam Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.success(null);
        }
        // 对外只暴露展示字段，手机号/证件号等即使将来被写入也不会从这里流出
        SysUser safe = new SysUser();
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
        return Result.success(sysUserService.getById(userId));
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
}
