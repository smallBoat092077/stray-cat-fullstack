package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.CoinTransaction;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.CoinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "猫粮账户")
@RestController
@RequestMapping("/coin")
@RequiredArgsConstructor
public class CoinController {

    private final CoinService coinService;

    @Operation(summary = "余额与累计打赏")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("balance", coinService.getBalance(userId));
        data.put("tipped", coinService.sumTipped(userId));
        data.put("signedToday", coinService.hasSignedIn(userId));
        return Result.success(data);
    }

    @Operation(summary = "猫粮收支明细")
    @GetMapping("/transactions")
    public Result<Page<CoinTransaction>> transactions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(coinService.listTransactions(userId, pageNum, pageSize));
    }

    @Operation(summary = "每日签到")
    @PostMapping("/signin")
    public Result<Map<String, Object>> signIn(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        boolean granted = coinService.signIn(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("granted", granted);
        data.put("reward", granted ? CoinService.SIGNIN_REWARD : 0);
        data.put("balance", coinService.getBalance(userId));
        return Result.success(granted ? "签到成功" : "今日已签到", data);
    }
}
