package com.straycat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.CatAvatarUtil;
import com.straycat.common.HomeCache;
import com.straycat.common.PhotoValidator;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatProfile;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.interceptor.RoleInterceptor;
import com.straycat.service.CatProfileService;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.GeoService;
import com.straycat.service.RegionService;
import com.straycat.vo.GeoResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "猫咪管理")
@Slf4j
@RestController
@RequestMapping("/cat")
@RequiredArgsConstructor
public class CatController {

    private final CatProfileService catProfileService;

    private final ContentSecurityService contentSecurityService;

    private final GeoService geoService;

    private final RegionService regionService;

    private final HomeCache homeCache;

    private static final String CAT_STATS_CACHE_KEY = "cat:stats:v1";

    @Operation(summary = "猫咪页统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> cached = homeCache.get(CAT_STATS_CACHE_KEY, Map.class);
        if (cached != null && cached.containsKey("total") && cached.containsKey("adoptable")) {
            return Result.success(cached);
        }
        long total = catProfileService.count();
        long adoptable = catProfileService.count(new LambdaQueryWrapper<CatProfile>()
                .eq(CatProfile::getLifeStatus, 2));
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("total", total);
        data.put("adoptable", adoptable);
        homeCache.set(CAT_STATS_CACHE_KEY, data, 300);
        return Result.success(data);
    }

    @Operation(summary = "猫咪列表")
    @GetMapping("/list")
    public Result<Page<CatProfile>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer lifeStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String districtCode) {
        Page<CatProfile> page = new Page<>(pageNum, pageSize);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CatProfile>();
        if (lifeStatus != null) {
            wrapper.eq(CatProfile::getLifeStatus, lifeStatus);
        }
        // 区域过滤（区域划分需求 R4）：不带参数 = 全部口径（存量未归属数据可见）
        if (districtCode != null && !districtCode.isBlank()) {
            wrapper.eq(CatProfile::getDistrictCode, districtCode);
        } else if (cityCode != null && !cityCode.isBlank()) {
            wrapper.eq(CatProfile::getCityCode, cityCode);
        } else if (provinceCode != null && !provinceCode.isBlank()) {
            wrapper.eq(CatProfile::getProvinceCode, provinceCode);
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
        // 区域归属（区域划分需求 R2）：端侧已传码优先；缺失且有坐标时后端兜底补码，失败不阻断上报
        fillRegionIfMissing(catProfile.getLatitude(), catProfile.getLongitude(), catProfile);
        Long id = catProfileService.reportCat(catProfile);
        homeCache.evictByPrefix("cat:stats:");
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
                catProfile.getLocationDesc(), catProfile.getDescription(),
                catProfile.getEnvironmentDesc());
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
        homeCache.evictByPrefix("cat:stats:");
        return Result.success();
    }

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    /**
     * 区域码兜底补齐（区域划分需求 §2）：端侧上报时已调 /geo/reverse 取码随表单提交；
     * 端侧解析失败时这里用坐标再试一次，仍失败则落库为未归属（不阻断上报）。
     */
    private void fillRegionIfMissing(BigDecimal latitude, BigDecimal longitude, CatProfile target) {
        boolean hasCodes = notBlank(target.getProvinceCode()) || notBlank(target.getCityCode())
                || notBlank(target.getDistrictCode());
        if (hasCodes || latitude == null || longitude == null) {
            return;
        }
        try {
            GeoResultVO geo = geoService.reverse(latitude.doubleValue(), longitude.doubleValue());
            if (geo.isSuccess()) {
                if (notBlank(geo.getProvinceCode())) {
                    target.setProvinceCode(geo.getProvinceCode());
                }
                if (notBlank(geo.getCityCode())) {
                    target.setCityCode(geo.getCityCode());
                }
                if (notBlank(geo.getDistrictCode())) {
                    target.setDistrictCode(geo.getDistrictCode());
                }
            }
        } catch (Exception e) {
            log.warn("上报补区域码失败（不阻断上报）: {}", e.getMessage());
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 管理员修改猫咪头像（猫咪头像功能设计方案 §3.1）—— 从该猫上报图片里挑选。
     * JSON {"imageUrl": "/uploads/..."}；只允许猫咪本体图（CatAvatarUtil.isOwnedCatImage 校验，
     * 环境图拒绝，防越权引用任意图）。只写 cover_image 一列，不涉及内容安全 msg_sec_check。
     * 注意：挑选与上传拆成两个端点——@RequestBody 与 multipart 不能同方法共存（UAT 实测 415）。
     */
    /**
     * 管理员修正猫咪归属区域（区域划分需求 R5 / D4：存量数据后台逐个补）。
     * body {provinceCode, cityCode, districtCode}，国标码须在 region 字典中存在（防脏码）。
     */
    @Operation(summary = "管理员修正猫咪归属区域")
    @PostMapping("/{id}/region")
    @RequireRole(3)
    public Result<?> updateRegion(@PathVariable Long id, @RequestBody Map<String, String> body) {
        CatProfile existing = catProfileService.getById(id);
        if (existing == null) {
            return Result.error(ResultCode.CAT_NOT_FOUND);
        }
        String province = body.getOrDefault("provinceCode", "").trim();
        String city = body.getOrDefault("cityCode", "").trim();
        String district = body.getOrDefault("districtCode", "").trim();
        if (!province.isEmpty() && regionService.childrenOf(null).stream().noneMatch(r -> r.getCode().equals(province))) {
            return Result.error("省级行政区码无效");
        }
        // 清空归属必须显式 set null：updateById 默认忽略 null 字段（UAT 实测 D07）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CatProfile> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CatProfile>()
                .eq(CatProfile::getId, id)
                .set(CatProfile::getProvinceCode, province.isEmpty() ? null : province)
                .set(CatProfile::getCityCode, city.isEmpty() ? null : city)
                .set(CatProfile::getDistrictCode, district.isEmpty() ? null : district);
        catProfileService.update(uw);
        return Result.success();
    }

    @Operation(summary = "管理员修改猫咪头像（从该猫上报图片挑选）")
    @PostMapping("/{id}/cover")
    @RequireRole(3)
    public Result<Map<String, Object>> pickCover(@PathVariable Long id,
                                                 @RequestBody Map<String, String> body) {
        return doUpdateCover(id, body != null ? body.get("imageUrl") : null, null);
    }

    /**
     * 管理员修改猫咪头像 —— 上传新图。PhotoValidator 校验后落盘 uploads/cover/{catId}/。
     */
    @Operation(summary = "管理员修改猫咪头像（上传新图）")
    @PostMapping("/{id}/cover/upload")
    @RequireRole(3)
    public Result<Map<String, Object>> uploadCover(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file) {
        return doUpdateCover(id, null, file);
    }

    private Result<Map<String, Object>> doUpdateCover(Long id, String imageUrl, MultipartFile file) {
        CatProfile existing = catProfileService.getById(id);
        if (existing == null) {
            return Result.error(ResultCode.CAT_NOT_FOUND);
        }
        String cover;
        if (file != null && !file.isEmpty()) {
            String invalid = PhotoValidator.validate(file);
            if (invalid != null) {
                return Result.error(invalid);
            }
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
            String fileName = "cover/" + id + "/" + UUID.randomUUID() + ext;
            File dest = new File(new File(uploadPath).getAbsoluteFile(), fileName);
            if (!dest.getParentFile().exists() && !dest.getParentFile().mkdirs()) {
                return Result.error("上传失败");
            }
            try {
                file.transferTo(dest.getAbsoluteFile());
            } catch (IOException e) {
                return Result.error("上传失败");
            }
            cover = "/uploads/" + fileName;
        } else if (imageUrl != null && !imageUrl.isBlank()) {
            if (!CatAvatarUtil.isOwnedCatImage(existing, imageUrl)) {
                return Result.error("只能从该猫咪的上报图片或现有头像中挑选");
            }
            cover = imageUrl;
        } else {
            return Result.error("请提供上传图片或要挑选的上报图片路径");
        }
        CatProfile update = new CatProfile();
        update.setId(id);
        update.setCoverImage(cover);
        catProfileService.updateById(update);
        return Result.success(Map.of("coverImage", cover));
    }
}
