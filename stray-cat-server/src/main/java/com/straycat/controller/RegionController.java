package com.straycat.controller;

import com.straycat.common.Result;
import com.straycat.entity.Region;
import com.straycat.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 行政区域公共查询（区域划分需求 R1/R4）。region 为只读字典，无鉴权。
 */
@Tag(name = "行政区域")
@RestController
@RequestMapping("/region")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @Operation(summary = "三级区域树（后台级联/名称映射用，10 分钟缓存）")
    @GetMapping("/tree")
    public Result<List<java.util.Map<String, Object>>> tree() {
        return Result.success(regionService.tree());
    }

    @Operation(summary = "子级行政区划（parentCode 空 = 省级列表）")
    @GetMapping("/children")
    public Result<List<Region>> children(@RequestParam(required = false) String parentCode) {
        return Result.success(regionService.childrenOf(parentCode));
    }
}
