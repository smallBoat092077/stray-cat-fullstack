package com.straycat.controller;

import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.weixin.WxacodeClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "带参小程序码")
@RestController
@RequestMapping("/wxa")
@RequiredArgsConstructor
public class WxacodeController {

    private final WxacodeClient wxacodeClient;

    @Operation(summary = "按短码生成小程序码（管理员）")
    @PostMapping("/code")
    @RequireRole(3)
    public Result<Map<String, Object>> code(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        return Result.success(wxacodeClient.generate(
                asString(req.get("sid")), asInt(req.get("width")), asString(req.get("envVersion"))));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
