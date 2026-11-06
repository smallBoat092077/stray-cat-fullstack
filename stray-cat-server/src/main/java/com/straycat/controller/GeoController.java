package com.straycat.controller;

import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.GeoRouteService;
import com.straycat.vo.GeoRouteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "地理")
@RestController
@RequestMapping("/geo")
@RequiredArgsConstructor
public class GeoController {

    private final GeoRouteService geoRouteService;

    @Operation(summary = "从当前位置到某只猫的步行路线")
    @GetMapping("/route")
    public Result<GeoRouteVO> route(@RequestParam Long catId,
                                    @RequestParam(required = false) Double fromLatitude,
                                    @RequestParam(required = false) Double fromLongitude,
                                    HttpServletRequest request) {
        // 拦截器允许匿名 GET，但这个接口会花服务端的腾讯配额，不登录就不给查
        if (request.getAttribute(AuthInterceptor.USER_ID_ATTR) == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(geoRouteService.routeToCat(catId, fromLatitude, fromLongitude));
    }
}
