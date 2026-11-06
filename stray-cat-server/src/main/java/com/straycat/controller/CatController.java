package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatProfile;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.interceptor.RoleInterceptor;
import com.straycat.service.CatProfileService;
import com.straycat.service.ContentSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "猫咪管理")
@RestController
@RequestMapping("/cat")
@RequiredArgsConstructor
public class CatController {

    private final CatProfileService catProfileService;

    private final ContentSecurityService contentSecurityService;

    @Operation(summary = "猫咪列表")
    @GetMapping("/list")
    public Result<Page<CatProfile>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer lifeStatus,
            @RequestParam(required = false) String keyword) {
        Page<CatProfile> page = new Page<>(pageNum, pageSize);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CatProfile>();
        if (lifeStatus != null) {
            wrapper.eq(CatProfile::getLifeStatus, lifeStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(CatProfile::getName, kw)
                    .or().like(CatProfile::getBreed, kw)
                    .or().like(CatProfile::getColor, kw));
        }
        wrapper.orderByDesc(CatProfile::getCreatedAt);
        return Result.success(catProfileService.page(page, wrapper));
    }

    @Operation(summary = "猫咪详情")
    @GetMapping("/{id}")
    public Result<CatProfile> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        CatProfile cat = catProfileService.getDetail(id, userId);
        if (cat == null) {
            return Result.error(ResultCode.CAT_NOT_FOUND);
        }
        return Result.success(cat);
    }

    @Operation(summary = "点赞/取消点赞")
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        boolean liked = catProfileService.toggleLike(id, userId);
        CatProfile cat = catProfileService.getById(id);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("liked", liked);
        data.put("likeCount", cat == null ? 0 : cat.getLikeCount());
        return Result.success(data);
    }

    @Operation(summary = "上报流浪猫")
    @PostMapping("/report")
    public Result<Long> report(@RequestBody CatProfile catProfile, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        catProfile.setDiscovererId(userId);
        Long id = catProfileService.reportCat(catProfile);
        return Result.success(id);
    }

    @Operation(summary = "附近猫咪")
    @GetMapping("/nearby")
    public Result<List<CatProfile>> nearby(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5") double radius) {
        List<CatProfile> cats = catProfileService.getNearbyCats(latitude, longitude, radius);
        return Result.success(cats);
    }

    @Operation(summary = "人气猫咪")
    @GetMapping("/popular")
    public Result<List<CatProfile>> popular(@RequestParam(defaultValue = "3") Integer limit) {
        return Result.success(catProfileService.getPopularCats(limit));
    }

    @Operation(summary = "更新猫咪信息")
    @PutMapping("/{id}")
    @RequireRole(0)
    public Result<?> update(@PathVariable Long id, @RequestBody CatProfile catProfile,
                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        CatProfile existing = catProfileService.getById(id);
        if (existing == null) {
            return Result.error(ResultCode.CAT_NOT_FOUND);
        }
        Integer role = (Integer) request.getAttribute(RoleInterceptor.ROLE_ATTR);
        boolean owner = userId.equals(existing.getDiscovererId()) || userId.equals(existing.getCaregiverId());
        if ((role == null || role < 3) && !owner) {
            return Result.error(ResultCode.FORBIDDEN);
        }
        contentSecurityService.check(userId, ContentSecurityService.SCENE_PROFILE,
                catProfile.getName(), catProfile.getAgeEstimate(), catProfile.getBreed(),
                catProfile.getColor(), catProfile.getPersonality(),
                catProfile.getLocationDesc(), catProfile.getDescription());
        catProfile.setId(id);
        // 生活状态、领养人和累计猫粮只走服务端联动，不接受这里直接改
        catProfile.setLifeStatus(null);
        catProfile.setAdopterId(null);
        catProfile.setCoinTotal(null);
        catProfileService.updateById(catProfile);
        return Result.success();
    }

    @Operation(summary = "纠正猫咪生活状态")
    @PutMapping("/{id}/life-status")
    @RequireRole(3)
    public Result<?> updateLifeStatus(@PathVariable Long id, @RequestBody CatProfile form) {
        catProfileService.updateLifeStatus(id, form.getLifeStatus());
        return Result.success();
    }
}
