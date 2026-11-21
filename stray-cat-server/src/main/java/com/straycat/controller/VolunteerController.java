package com.straycat.controller;

import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.dto.ReviewForm;
import com.straycat.entity.VolunteerApplication;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.request.VolunteerApplyRequest;
import com.straycat.service.VolunteerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "志愿者申请")
@RestController
@RequestMapping("/volunteer")
@RequiredArgsConstructor
public class VolunteerController {

    private final VolunteerApplicationService volunteerApplicationService;

    @PostMapping("/apply")
    @Operation(summary = "提交志愿者申请")
    public Result<Long> apply(@RequestBody VolunteerApplyRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(com.straycat.common.ResultCode.UNAUTHORIZED);
        }
        return Result.success(volunteerApplicationService.apply(userId, req));
    }

    @GetMapping("/my-apply")
    @Operation(summary = "我的最新志愿者申请")
    public Result<VolunteerApplication> myApply(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(com.straycat.common.ResultCode.UNAUTHORIZED);
        }
        return Result.success(volunteerApplicationService.myLatest(userId));
    }

    // ---- 后台管理接口 ----

    @GetMapping("/admin/list")
    @Operation(summary = "后台志愿者申请列表")
    @RequireRole(3)
    public Result<?> adminList(@RequestParam(defaultValue = "1") Integer pageNum,
                               @RequestParam(defaultValue = "10") Integer pageSize,
                               @RequestParam(required = false) Integer status) {
        return Result.success(volunteerApplicationService.list(pageNum, pageSize, status));
    }

    @GetMapping("/admin/counts")
    @Operation(summary = "后台各状态申请数")
    @RequireRole(3)
    public Result<Map<String, Long>> adminCounts() {
        return Result.success(volunteerApplicationService.counts());
    }

    @PostMapping("/admin/review")
    @Operation(summary = "审核志愿者申请（1通过 2拒绝）")
    @RequireRole(3)
    public Result<?> adminReview(@RequestBody ReviewForm form, HttpServletRequest request) {
        Long reviewerId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        volunteerApplicationService.review(form.getId(), reviewerId, form.getStatus(), form.getComment());
        return Result.success();
    }
}
