package com.straycat.controller;

import com.straycat.common.Result;
import com.straycat.service.WeatherService;
import com.straycat.vo.WeatherVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页天气（天气卡片功能设计 §3.1），免登，与 bootstrap 一致匿名可见。
 */
@Tag(name = "天气")
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @Operation(summary = "按行政区码取天气（实况+未来3天；免登；缓存30min）")
    @GetMapping
    public Result<WeatherVO> get(@RequestParam(required = false) String adcode) {
        return Result.success(weatherService.getWeather(adcode));
    }
}
