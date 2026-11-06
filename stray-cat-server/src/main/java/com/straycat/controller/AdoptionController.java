package com.straycat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.dto.ReviewForm;
import com.straycat.entity.AdoptionApplication;
import com.straycat.entity.AdoptionFollowUp;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.interceptor.RoleInterceptor;
import com.straycat.service.AdoptionApplicationService;
import com.straycat.service.AdoptionFollowUpService;
import com.straycat.service.CoinService;
import com.straycat.service.ContentSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/adoption")
@Tag(name = "领养管理", description = "领养申请相关接口")
public class AdoptionController {

    @Resource
    private AdoptionApplicationService adoptionApplicationService;

    @Resource
    private AdoptionFollowUpService adoptionFollowUpService;

    @Resource
    private CoinService coinService;

    @Resource
    private ContentSecurityService contentSecurityService;

    @PostMapping("/apply")
    @Operation(summary = "提交领养申请")
    public Result<Long> apply(@RequestBody AdoptionApplication application, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        contentSecurityService.check(userId, ContentSecurityService.SCENE_SOCIAL,
                application.getCatExperience(), application.getAdoptionReason(), application.getWorkSchedule());
        application.setApplicantId(userId);
        adoptionApplicationService.apply(application);
        return Result.success(application.getId());
    }

    @GetMapping("/application/{id}")
    @Operation(summary = "获取申请详情")
    @RequireRole(0)
    public Result<AdoptionApplication> getApplication(@PathVariable Long id, HttpServletRequest request) {
        AdoptionApplication application = adoptionApplicationService.getApplication(id);
        if (application == null) {
            return Result.error("申请不存在");
        }
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (!canSeeApplications(request) && !userId.equals(application.getApplicantId())) {
            return Result.error(ResultCode.FORBIDDEN);
        }
        return Result.success(application);
    }

    @GetMapping("/application/list")
    @Operation(summary = "获取申请列表")
    @RequireRole(0)
    public Result<Page<AdoptionApplication>> getApplicationList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long applicantId,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {

        // 申请单里有住房、作息这些个人信息，志愿者/管理员之外只能看自己的
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (!canSeeApplications(request)) {
            applicantId = userId;
        }

        Page<AdoptionApplication> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AdoptionApplication> wrapper = new LambdaQueryWrapper<>();

        if (applicantId != null) {
            wrapper.eq(AdoptionApplication::getApplicantId, applicantId);
        }
        if (status != null) {
            wrapper.eq(AdoptionApplication::getStatus, status);
        }
        wrapper.orderByDesc(AdoptionApplication::getCreatedAt);

        return Result.success(adoptionApplicationService.page(page, wrapper));
    }

    @PostMapping("/review")
    @Operation(summary = "审核领养申请")
    @RequireRole(1)
    public Result<?> review(@RequestBody ReviewForm form, HttpServletRequest request) {
        Long reviewerId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        adoptionApplicationService.review(form.getId(), reviewerId, form.getStatus(), form.getComment());
        return Result.success();
    }

    /**
     * RoleInterceptor 在校验 @RequireRole 时把角色放进了 request，志愿者及以上能看全量申请
     */
    private boolean canSeeApplications(HttpServletRequest request) {
        Object role = request.getAttribute(RoleInterceptor.ROLE_ATTR);
        return role instanceof Integer && (Integer) role >= 1;
    }

    @PostMapping("/followup/submit")
    @Operation(summary = "提交领养回访")
    public Result<?> submitFollowUp(@RequestBody AdoptionFollowUp followUp, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        contentSecurityService.check(userId, ContentSecurityService.SCENE_SOCIAL,
                followUp.getCatCondition(), followUp.getLivingEnvironment(),
                followUp.getHealthStatus(), followUp.getRemark());
        followUp.setAdopterId(userId);
        adoptionFollowUpService.submitFollowUp(followUp);
        coinService.grant(userId, CoinService.FOLLOWUP_REWARD, CoinService.BIZ_FOLLOWUP,
                followUp.getId(), "提交领养回访");
        return Result.success();
    }

    @GetMapping("/followup/list")
    @Operation(summary = "获取回访列表")
    public Result<Page<AdoptionFollowUp>> getFollowUpList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Integer status) {

        Page<AdoptionFollowUp> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AdoptionFollowUp> wrapper = new LambdaQueryWrapper<>();

        if (applicationId != null) {
            wrapper.eq(AdoptionFollowUp::getApplicationId, applicationId);
        }
        if (status != null) {
            wrapper.eq(AdoptionFollowUp::getStatus, status);
        }
        wrapper.orderByDesc(AdoptionFollowUp::getCreatedAt);

        return Result.success(adoptionFollowUpService.page(page, wrapper));
    }
}
