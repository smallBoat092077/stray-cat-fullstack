package com.straycat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.entity.CatProfile;
import com.straycat.service.CatProfileService;
import com.straycat.service.GeoService;
import com.straycat.vo.GeoResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 猫咪数据管理（管理员）：一次性数据修复工具。
 */
@Slf4j
@Tag(name = "猫咪数据管理（管理员）")
@RestController
@RequestMapping("/admin/cat")
@RequiredArgsConstructor
public class AdminCatController {

    private final CatProfileService catProfileService;
    private final GeoService geoService;

    /**
     * 地点名错位修复：按坐标逆地理回填存量猫咪的 location_name（真实地址）。
     * 幂等：只处理 location_name 为空且有坐标的记录；失败的不阻断，计数返回。
     */
    @Operation(summary = "回填猫咪真实地点名（幂等）")
    @PostMapping("/backfill-location-name")
    @RequireRole(3)
    public Result<Map<String, Object>> backfillLocationName() {
        List<CatProfile> cats = catProfileService.list(new LambdaQueryWrapper<CatProfile>()
                .isNull(CatProfile::getLocationName)
                .isNotNull(CatProfile::getLatitude)
                .isNotNull(CatProfile::getLongitude));

        int filled = 0;
        int skipped = 0;
        for (CatProfile cat : cats) {
            try {
                GeoResultVO geo = geoService.reverse(
                        cat.getLatitude().doubleValue(), cat.getLongitude().doubleValue());
                String address = geo == null ? null : geo.getAddress();
                if (address == null || address.trim().isEmpty()) {
                    skipped++;
                    continue;
                }
                catProfileService.update(new LambdaUpdateWrapper<CatProfile>()
                        .eq(CatProfile::getId, cat.getId())
                        .set(CatProfile::getLocationName, address.trim()));
                filled++;
            } catch (Exception e) {
                log.warn("回填猫咪地点名失败: id={}, err={}", cat.getId(), e.getMessage());
                skipped++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", cats.size());
        data.put("filled", filled);
        data.put("skipped", skipped);
        return Result.success(data);
    }

    /**
     * 存量区域码回填：按坐标逆地理给无归属的猫补省/市/区码。
     * 投喂任务的区域随猫（FeedingTaskMapper join cat_profile），猫补码后投喂页按区域查询自动生效。
     * 幂等：只处理 province_code 为空且有坐标的记录；补不上（无坐标/解析失败）保持 NULL，不造假归属。
     */
    @Operation(summary = "回填猫咪归属区域（幂等）")
    @PostMapping("/backfill-region")
    @RequireRole(3)
    public Result<Map<String, Object>> backfillRegion() {
        List<CatProfile> cats = catProfileService.list(new LambdaQueryWrapper<CatProfile>()
                .isNull(CatProfile::getProvinceCode)
                .isNotNull(CatProfile::getLatitude)
                .isNotNull(CatProfile::getLongitude));

        int filled = 0;
        int skipped = 0;
        for (CatProfile cat : cats) {
            try {
                GeoResultVO geo = geoService.reverse(
                        cat.getLatitude().doubleValue(), cat.getLongitude().doubleValue());
                if (geo == null || !geo.isSuccess() || geo.getProvinceCode() == null) {
                    skipped++;
                    continue;
                }
                catProfileService.update(new LambdaUpdateWrapper<CatProfile>()
                        .eq(CatProfile::getId, cat.getId())
                        .set(CatProfile::getProvinceCode, geo.getProvinceCode())
                        .set(CatProfile::getCityCode, geo.getCityCode())
                        .set(CatProfile::getDistrictCode, geo.getDistrictCode()));
                filled++;
            } catch (Exception e) {
                log.warn("回填猫咪区域失败: id={}, err={}", cat.getId(), e.getMessage());
                skipped++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", cats.size());
        data.put("filled", filled);
        data.put("skipped", skipped);
        return Result.success(data);
    }
}
