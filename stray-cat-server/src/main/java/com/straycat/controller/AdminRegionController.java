package com.straycat.controller;

import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 区域数据管理（区域划分需求 R1 / D1：只读字典，仅提供全量同步与统计，不做逐条增删改）。
 */
@Tag(name = "区域数据管理（管理员）")
@RestController
@RequestMapping("/admin/region")
@RequiredArgsConstructor
public class AdminRegionController {

    private final RegionService regionService;

    @Operation(summary = "从腾讯 district API 全量同步省市区（幂等）")
    @PostMapping("/sync")
    @RequireRole(3)
    public Result<Map<String, Object>> sync() {
        Map<String, Integer> out = regionService.syncFromTencent();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fetched", out.get("fetched"));
        data.put("inserted", out.get("inserted"));
        data.put("total", regionService.count());
        return Result.success(data);
    }

    @Operation(summary = "区域数据统计")
    @GetMapping("/stats")
    @RequireRole(3)
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", regionService.count());
        return Result.success(data);
    }
}
