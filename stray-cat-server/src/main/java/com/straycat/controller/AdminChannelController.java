package com.straycat.controller;

import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.service.ShareLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 渠道看板独立成类，不动 AdminService / AdminController，
 * 保证现有 18 个汇总指标零回归。
 */
@Tag(name = "渠道看板")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminChannelController {

    private final ShareLinkService shareLinkService;

    @Operation(summary = "渠道来源统计")
    @GetMapping("/stats/channel")
    @RequireRole(3)
    public Result<Map<String, Object>> channelStats(
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(shareLinkService.channelStats(days == null ? 30 : days));
    }

    @Operation(summary = "渠道短码列表")
    @GetMapping("/share/links")
    @RequireRole(3)
    public Result<List<Map<String, Object>>> links(
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(shareLinkService.linkList(days == null ? 30 : days));
    }
}
