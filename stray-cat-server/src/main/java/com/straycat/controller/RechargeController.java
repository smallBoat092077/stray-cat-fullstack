package com.straycat.controller;

import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.RechargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "猫粮充值")
@RestController
@RequestMapping("/recharge")
@RequiredArgsConstructor
public class RechargeController {

    private final RechargeService rechargeService;

    @Operation(summary = "充值开关与礼包列表")
    @GetMapping("/packs")
    public Result<Map<String, Object>> packs() {
        Map<String, Object> data = new LinkedHashMap<>(rechargeService.capability());
        data.put("packs", rechargeService.listPacks());
        return Result.success(data);
    }

    @Operation(summary = "下单，返回调起虚拟支付所需签名")
    @PostMapping("/order")
    public Result<Map<String, Object>> createOrder(@RequestBody Map<String, String> body,
                                                   HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(rechargeService.createOrder(userId, body.get("packCode")));
    }

    @Operation(summary = "支付后服务端查单确认到账")
    @PostMapping("/confirm")
    public Result<Map<String, Object>> confirm(@RequestBody Map<String, String> body,
                                               HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(rechargeService.confirmPaid(userId, body.get("outTradeNo")));
    }

    @Operation(summary = "查询订单到账状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> status(@RequestParam String outTradeNo, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(rechargeService.queryStatus(userId, outTradeNo));
    }

    @Operation(summary = "我的充值记录")
    @GetMapping("/orders")
    public Result<java.util.List<Map<String, Object>>> orders(@RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(rechargeService.listOrders(userId, pageNum, pageSize));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        return userId == null ? null : Long.valueOf(userId.toString());
    }
}
