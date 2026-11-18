package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.LostCatClue;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.LostCatClueService;
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
 * 后台寻猫线索审核（方案 4.3）。全部 @RequireRole(3)；审核参数走 query 串，
 * 用 @RequestBody 会被前端拦截器剥壳导致参数静默丢失。
 */
@Tag(name = "寻猫线索审核（管理员）")
@RestController
@RequestMapping("/admin/lost-cat/clue")
@RequiredArgsConstructor
public class AdminLostCatClueController {

    private final LostCatClueService lostCatClueService;

    @Operation(summary = "待审/全部列表")
    @GetMapping("/list")
    @RequireRole(3)
    public Result<Page<LostCatClue>> list(@RequestParam(required = false) Integer auditStatus,
                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(lostCatClueService.list(auditStatus, pageNum, pageSize));
    }

    @Operation(summary = "通过 / 驳回（驳回填原因）")
    @PostMapping("/{id}/audit")
    @RequireRole(3)
    public Result<Void> audit(@PathVariable Long id,
                              @RequestParam Integer auditStatus,
                              @RequestParam(required = false) String auditRemark,
                              HttpServletRequest httpRequest) {
        lostCatClueService.audit(requireUserId(httpRequest), id, auditStatus, auditRemark);
        return Result.success(null);
    }

    private Long requireUserId(HttpServletRequest request) {
        Object uid = request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Long.valueOf(uid.toString());
    }
}
