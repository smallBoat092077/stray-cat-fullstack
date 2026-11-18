package com.straycat.controller;

import com.straycat.common.Result;
import com.straycat.service.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 活动页免登接口。带参小程序码（scene=s=短码）与分享卡片的落地页都是 pages/campaign/index，
 * 短码由 utils/attribution 在页面 onLoad 时 capture，这里只按 code/id 出详情。
 */
@Tag(name = "营销活动")
@RestController
@RequestMapping("/campaign")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @Operation(summary = "活动详情（免登）")
    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam(required = false) String code,
                                              @RequestParam(required = false) Long id) {
        return Result.success(campaignService.detail(code, id));
    }
}
