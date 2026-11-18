package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.entity.Campaign;
import com.straycat.service.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "活动管理（管理员）")
@RestController
@RequestMapping("/admin/campaign")
@RequiredArgsConstructor
public class AdminCampaignController {

    private final CampaignService campaignService;

    @Operation(summary = "活动分页列表")
    @GetMapping("/list")
    @RequireRole(3)
    public Result<Page<Campaign>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(campaignService.page(pageNum, pageSize));
    }

    @Operation(summary = "新增/编辑活动")
    @PostMapping("/save")
    @RequireRole(3)
    public Result<Campaign> save(@RequestBody Campaign form) {
        return Result.success(campaignService.saveCampaign(form));
    }

    @Operation(summary = "上下线活动")
    @PostMapping("/status")
    @RequireRole(3)
    public Result<Void> status(@RequestParam Long id, @RequestParam Integer status) {
        campaignService.toggleStatus(id, status);
        return Result.success(null);
    }

    @Operation(summary = "给活动访客发送活动提醒")
    @PostMapping("/{id}/remind")
    @RequireRole(3)
    public Result<Map<String, Object>> remind(@PathVariable Long id,
                                              @RequestParam(required = false) String content) {
        int count = campaignService.sendReminder(id, content);
        return Result.success(Map.of("sent", count));
    }
}
