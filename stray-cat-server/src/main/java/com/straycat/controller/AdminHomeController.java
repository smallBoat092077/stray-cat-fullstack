package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.HomeContent;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.request.HomeContentRequest;
import com.straycat.service.AdminHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台首页内容管理（方案 5.2），全部 @RequireRole(3)。
 */
@Tag(name = "首页内容管理（管理员）")
@RestController
@RequestMapping("/admin/home")
@RequiredArgsConstructor
public class AdminHomeController {

    private final AdminHomeService adminHomeService;

    @Operation(summary = "内容位分页列表")
    @GetMapping("/content/list")
    @RequireRole(3)
    public Result<Page<HomeContent>> contentList(@RequestParam(required = false) String type,
                                                 @RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(adminHomeService.contentList(type, pageNum, pageSize));
    }

    @Operation(summary = "新增/编辑内容位（linkUrl 过站内正则，拒绝外链）")
    @PostMapping("/content/save")
    @RequireRole(3)
    public Result<HomeContent> save(@RequestBody HomeContentRequest request, HttpServletRequest httpRequest) {
        return Result.success(adminHomeService.saveContent(requireUserId(httpRequest), request));
    }

    @Operation(summary = "上/下线（主动清 home:bootstrap:* 缓存）")
    @PostMapping("/content/{id}/status")
    @RequireRole(3)
    public Result<Void> status(@PathVariable Long id, @RequestParam Integer status) {
        adminHomeService.changeStatus(id, status);
        return Result.success(null);
    }

    @Operation(summary = "删除内容位（逻辑下线优先）")
    @DeleteMapping("/content/{id}")
    @RequireRole(3)
    public Result<Void> delete(@PathVariable Long id) {
        adminHomeService.deleteContent(id);
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
