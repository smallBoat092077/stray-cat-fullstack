package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.entity.CatTransferPost;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.CatTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 爱猫搬家后台审核，全部 @RequireRole(3)。
 */
@Tag(name = "爱猫搬家审核（管理员）")
@RestController
@RequestMapping("/admin/transfer")
@RequiredArgsConstructor
public class AdminTransferController {

    private final CatTransferService catTransferService;

    @GetMapping("/posts")
    @Operation(summary = "转让信息列表（全字段，可按状态筛选）")
    @RequireRole(3)
    public Result<Page<CatTransferPost>> posts(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(catTransferService.adminPosts(status, pageNum, pageSize));
    }

    @PostMapping("/post/{id}/audit")
    @Operation(summary = "审核：status 1通过 / 4驳回")
    @RequireRole(3)
    public Result<?> audit(@PathVariable Long id, @RequestParam Integer status,
                           @RequestParam(required = false) String remark,
                           HttpServletRequest request) {
        Long reviewerId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        catTransferService.audit(id, status, remark, reviewerId);
        return Result.success();
    }
}
