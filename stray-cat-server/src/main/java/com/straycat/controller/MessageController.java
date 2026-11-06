package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.MessageType;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.config.WxSubscribeProperties;
import com.straycat.entity.UserMessage;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.NotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "消息中心")
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final NotifyService notifyService;

    private final WxSubscribeProperties subscribeProperties;

    /**
     * 消息只能看自己的，GET 在拦截器里是允许匿名通过的，所以这里必须自己卡住未登录
     */
    @Operation(summary = "我的消息分页")
    @GetMapping("/list")
    public Result<Page<UserMessage>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long userId = requireUserId(request);
        return Result.success(notifyService.listMine(userId, pageNum, pageSize));
    }

    @Operation(summary = "未读条数")
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(HttpServletRequest request) {
        Long userId = requireUserId(request);
        return Result.success(Map.of("count", notifyService.unreadCount(userId)));
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read")
    public Result<Map<String, Object>> readAll(HttpServletRequest request) {
        Long userId = requireUserId(request);
        return Result.success(Map.of("updated", notifyService.markAllRead(userId)));
    }

    /**
     * 前端调 wx.requestSubscribeMessage 需要模板 ID，但模板 ID 属于环境配置不该写死在小程序包里，
     * 这里只吐已配置的部分；没配置就返回空对象，前端跳过授权、业务照常走。
     */
    @Operation(summary = "已配置的订阅消息模板 ID")
    @GetMapping("/templates")
    public Result<Map<String, String>> templates() {
        Map<String, String> configured = new LinkedHashMap<>();
        for (MessageType type : MessageType.values()) {
            String templateId = subscribeProperties.templateId(type.getTemplateKey());
            if (templateId != null) {
                configured.put(type.getTemplateKey(), templateId);
            }
        }
        return Result.success(configured);
    }

    private Long requireUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
