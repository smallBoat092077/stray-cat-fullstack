package com.straycat.controller;

import com.straycat.common.Result;
import com.straycat.dto.ShareLinkRequest;
import com.straycat.entity.ShareLink;
import com.straycat.service.ShareLinkService;
import com.straycat.vo.ShareLinkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "渠道归因")
@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkService shareLinkService;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 免登 POST（WebConfig 白名单）。带有效 token 时把 token 反查到的 userId 记为邀请人，
     * 匿名请求生成官方短码、不带邀请人。app.js 不自动 wx.login，匿名分享也必须能归因。
     */
    @Operation(summary = "签发渠道短码")
    @PostMapping("/link")
    public Result<Map<String, Object>> link(@RequestBody(required = false) ShareLinkRequest body,
                                            HttpServletRequest request) {
        if (!shareLinkService.tryAcquireQuota(clientIp(request))) {
            return Result.error(429, "操作太频繁，请稍后再试");
        }
        ShareLinkRequest req = body == null ? new ShareLinkRequest() : body;
        ShareLink link = shareLinkService.createLink(
                req.getChannel(), req.getCampaign(), req.getBizType(), req.getBizId(),
                req.getLandingPage(), optionalUserId(request));

        Map<String, Object> data = new HashMap<>();
        data.put("sid", link.getSid());
        data.put("path", buildPath(link));
        // getUnlimited 的 scene 上限 32 可见字符，只放短码，其余参数由 resolve 补
        data.put("scene", "s=" + link.getSid());
        return Result.success(data);
    }

    /** 免登 GET：无效 / 过期 / 停用短码返回 data=null，小程序端据此降级为 direct */
    @Operation(summary = "解析渠道短码")
    @GetMapping("/resolve")
    public Result<ShareLinkVO> resolve(@RequestParam(required = false) String sid,
                                       HttpServletRequest request) {
        ShareLink link = shareLinkService.resolve(sid);
        if (link != null) {
            Long userId = optionalUserId(request);
            if (userId != null) {
                // 老用户已登录，不会再走 /user/login；在这里补一条幂等的访问记录。
                // 不触碰 sys_user，首触来源保持不变
                shareLinkService.recordVisit(link.getSid(), userId, false, "resolve", null);
            }
        }
        return Result.success(ShareLinkVO.of(link));
    }

    private static String buildPath(ShareLink link) {
        String page = link.getLandingPage();
        if (page == null || page.isBlank()) {
            return "/pages/index/index?sid=" + link.getSid();
        }
        StringBuilder path = new StringBuilder("/").append(page).append('?');
        if (link.getBizId() != null) {
            String idKey = "adoption".equals(link.getBizType()) ? "catId" : "id";
            path.append(idKey).append('=').append(link.getBizId()).append('&');
        }
        return path.append("sid=").append(link.getSid()).toString();
    }

    /** 白名单路径不经过 AuthInterceptor，这里显式按同一套 token 规则反查，失效 token 当匿名处理 */
    private Long optionalUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            Object value = redisTemplate.opsForValue().get("token:" + header.substring(7));
            return value == null ? null : Long.valueOf(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
