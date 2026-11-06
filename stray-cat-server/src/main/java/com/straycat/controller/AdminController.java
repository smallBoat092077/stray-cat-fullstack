package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.entity.SysUser;
import com.straycat.service.AdminService;
import com.straycat.vo.AdminTipVO;
import com.straycat.vo.CoinLedgerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "后台管理", description = "Vue 管理后台专用的只读接口")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "首页汇总")
    @GetMapping("/stats")
    @RequireRole(3)
    public Result<Map<String, Object>> stats() {
        return Result.success(adminService.stats());
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    @RequireRole(3)
    public Result<Page<SysUser>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer role,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(adminService.pageUsers(keyword, role, pageNum, pageSize));
    }

    @Operation(summary = "全局猫粮流水")
    @GetMapping("/ledger")
    @RequireRole(3)
    public Result<Page<CoinLedgerVO>> ledger(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(adminService.pageLedger(userId, bizType, pageNum, pageSize));
    }

    @Operation(summary = "全局打赏记录")
    @GetMapping("/tips")
    @RequireRole(3)
    public Result<Page<AdminTipVO>> tips(
            @RequestParam(required = false) Long catId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(adminService.pageTips(catId, pageNum, pageSize));
    }
}
