package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatTip;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.TipService;
import com.straycat.vo.TipVO;
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

@Tag(name = "送猫粮")
@RestController
@RequestMapping("/tip")
@RequiredArgsConstructor
public class TipController {

    private final TipService tipService;

    @Operation(summary = "给猫咪送猫粮")
    @PostMapping
    public Result<TipVO> tip(@RequestBody CatTip form, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(tipService.tip(userId, form.getCatId(), form.getCoinAmount(), form.getMessage()));
    }

    @Operation(summary = "某只猫咪收到的猫粮")
    @GetMapping("/cat/{catId}")
    public Result<Page<TipVO>> listByCat(
            @PathVariable Long catId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(tipService.listByCat(catId, pageNum, pageSize));
    }

    @Operation(summary = "我送出的猫粮")
    @GetMapping("/my")
    public Result<Page<TipVO>> listMine(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(tipService.listMine(userId, pageNum, pageSize));
    }
}
