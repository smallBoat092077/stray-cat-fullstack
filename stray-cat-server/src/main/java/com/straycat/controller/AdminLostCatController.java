package com.straycat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.LostCatPost;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.mapper.LostCatPostMapper;
import com.straycat.service.AdminHomeService;
import com.straycat.service.GeoService;
import com.straycat.vo.GeoResultVO;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台寻猫审核（方案 5.2），全部 @RequireRole(3)。
 */
@Slf4j
@Tag(name = "寻猫审核（管理员）")
@RestController
@RequestMapping("/admin/lost-cat")
@RequiredArgsConstructor
public class AdminLostCatController {

    private final AdminHomeService adminHomeService;

    private final LostCatPostMapper lostCatPostMapper;

    private final GeoService geoService;

    @Operation(summary = "待审/全部列表")
    @GetMapping("/list")
    @RequireRole(3)
    public Result<Page<LostCatPost>> list(@RequestParam(required = false) Integer auditStatus,
                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(adminHomeService.lostCatList(auditStatus, pageNum, pageSize));
    }

    @Operation(summary = "通过 / 驳回（驳回填原因）")
    @PostMapping("/{id}/audit")
    @RequireRole(3)
    public Result<Void> audit(@PathVariable Long id,
                              @RequestParam Integer auditStatus,
                              @RequestParam(required = false) String auditRemark,
                              HttpServletRequest httpRequest) {
        adminHomeService.auditLostCat(requireUserId(httpRequest), id, auditStatus, auditRemark);
        return Result.success(null);
    }

    /**
     * 修正启事归属区域（区域划分需求 R5 / D4：存量数据后台逐个补）。
     * body {provinceCode, cityCode, districtCode}，空串 = 清除归属。
     */
    @Operation(summary = "修正启事归属区域")
    @PostMapping("/{id}/region")
    @RequireRole(3)
    public Result<Void> updateRegion(@PathVariable Long id, @RequestBody Map<String, String> body) {
        LostCatPost post = lostCatPostMapper.selectById(id);
        if (post == null) {
            return Result.error("启事不存在");
        }
        // 清空归属必须显式 set null：updateById 默认忽略 null 字段（UAT 实测）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LostCatPost> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LostCatPost>()
                .eq(LostCatPost::getId, id)
                .set(LostCatPost::getProvinceCode, blankToNull(body.get("provinceCode")))
                .set(LostCatPost::getCityCode, blankToNull(body.get("cityCode")))
                .set(LostCatPost::getDistrictCode, blankToNull(body.get("districtCode")));
        lostCatPostMapper.update(null, uw);
        return Result.success(null);
    }

    /**
     * 存量区域码回填：按坐标逆地理给无归属的寻猫启事补省/市/区码。
     * 幂等：只处理 province_code 为空且有坐标的记录；补不上保持 NULL，不造假归属。
     */
    @Operation(summary = "回填寻猫启事归属区域（幂等）")
    @PostMapping("/backfill-region")
    @RequireRole(3)
    public Result<Map<String, Object>> backfillRegion() {
        List<LostCatPost> posts = lostCatPostMapper.selectList(new LambdaQueryWrapper<LostCatPost>()
                .isNull(LostCatPost::getProvinceCode)
                .isNotNull(LostCatPost::getLatitude)
                .isNotNull(LostCatPost::getLongitude));

        int filled = 0;
        int skipped = 0;
        for (LostCatPost post : posts) {
            try {
                GeoResultVO geo = geoService.reverse(
                        post.getLatitude().doubleValue(), post.getLongitude().doubleValue());
                if (geo == null || !geo.isSuccess() || geo.getProvinceCode() == null) {
                    skipped++;
                    continue;
                }
                lostCatPostMapper.update(null, new LambdaUpdateWrapper<LostCatPost>()
                        .eq(LostCatPost::getId, post.getId())
                        .set(LostCatPost::getProvinceCode, geo.getProvinceCode())
                        .set(LostCatPost::getCityCode, geo.getCityCode())
                        .set(LostCatPost::getDistrictCode, geo.getDistrictCode()));
                filled++;
            } catch (Exception e) {
                log.warn("回填寻猫启事区域失败: id={}, err={}", post.getId(), e.getMessage());
                skipped++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", posts.size());
        data.put("filled", filled);
        data.put("skipped", skipped);
        return Result.success(data);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Long requireUserId(HttpServletRequest request) {
        Object uid = request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Long.valueOf(uid.toString());
    }
}
