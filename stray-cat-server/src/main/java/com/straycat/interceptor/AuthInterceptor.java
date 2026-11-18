package com.straycat.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;

    public static final String USER_ID_ATTR = "userId";

    /**
     * 允许匿名提交的写接口：测评提交要「未登录可答题、登录则落库」，
     * 这里放行但上方仍会解析 token，所以带 token 的用户照常拿到 userId。
     */
    private static final Set<String> ANONYMOUS_WRITE_PATHS =
            Set.of("/wiki/quiz/submit", "/user/forgot-password/apply", "/user/reset-password-by-code");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        // 有 token 时验证并注入 userId
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Object userId = redisTemplate.opsForValue().get("token:" + token);
            if (userId == null) {
                writeError(response, 1004, "Token已过期");
                return false;
            }
            request.setAttribute(USER_ID_ATTR, Long.valueOf(userId.toString()));
            return true;
        }

        // 无 token：写操作需要登录，GET 允许匿名
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            if (ANONYMOUS_WRITE_PATHS.contains(request.getServletPath())) {
                return true;
            }
            writeError(response, 1006, "未授权");
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\"}");
    }
}
