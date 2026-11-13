package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.Feedback;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户反馈")
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 提交反馈必须登录：AuthInterceptor 对 GET 放行匿名，但 POST 一律校验 token，
     * 这里再显式卡一次防御，拿不到 userId 直接 401。
     */
    @Operation(summary = "提交反馈")
    @PostMapping("/submit")
    public Result<Feedback> create(@RequestBody Feedback form, HttpServletRequest request) {
        Long userId = requireUserId(request);
        return Result.success(feedbackService.create(userId, form.getType(), form.getContent(), form.getImages()));
    }

    @Operation(summary = "我的反馈列表")
    @GetMapping("/my")
    public Result<Page<Feedback>> mine(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long userId = requireUserId(request);
        return Result.success(feedbackService.listMine(userId, pageNum, pageSize));
    }

    private Long requireUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
