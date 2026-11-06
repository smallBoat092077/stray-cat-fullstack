package com.straycat.interceptor;

import com.straycat.annotation.RequireRole;
import com.straycat.common.ResultCode;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 角色鉴权。必须排在 AuthInterceptor 之后，它依赖 AuthInterceptor 注入的 userId。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleInterceptor implements HandlerInterceptor {

    public static final String ROLE_ATTR = "role";

    private static final String ROLE_CACHE_PREFIX = "user:role:";

    private static final Duration ROLE_CACHE_TTL = Duration.ofMinutes(10);

    private final SysUserMapper sysUserMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    public static String cacheKey(Long userId) {
        return ROLE_CACHE_PREFIX + userId;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireRole requireRole = method.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            return true;
        }
        Object userId = request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        int role = currentRole(Long.valueOf(userId.toString()));
        if (role < requireRole.value()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        request.setAttribute(ROLE_ATTR, role);
        return true;
    }

    private int currentRole(Long userId) {
        Object cached = redisTemplate.opsForValue().get(cacheKey(userId));
        if (cached != null) {
            return Integer.parseInt(cached.toString());
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        int role = user.getRole() == null ? 0 : user.getRole();
        redisTemplate.opsForValue().set(cacheKey(userId), role, ROLE_CACHE_TTL);
        return role;
    }
}
