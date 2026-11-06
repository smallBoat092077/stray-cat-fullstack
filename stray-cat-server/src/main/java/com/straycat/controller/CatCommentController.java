package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatComment;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.interceptor.RoleInterceptor;
import com.straycat.service.CatCommentService;
import com.straycat.vo.CatCommentVO;
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

@Tag(name = "猫咪评论")
@RestController
@RequestMapping("/cat")
@RequiredArgsConstructor
public class CatCommentController {

    private final CatCommentService catCommentService;

    @Operation(summary = "猫咪评论列表")
    @GetMapping("/{catId}/comments")
    public Result<Page<CatCommentVO>> list(
            @PathVariable Long catId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        return Result.success(catCommentService.listByCat(catId, userId, pageNum, pageSize));
    }

    @Operation(summary = "展开某条评论的回复")
    @GetMapping("/comments/{commentId}/replies")
    public Result<Page<CatCommentVO>> replies(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "30") Integer pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        return Result.success(catCommentService.listReplies(commentId, userId, pageNum, pageSize));
    }

    @Operation(summary = "发表评论/回复")
    @PostMapping("/{catId}/comments")
    public Result<CatCommentVO> create(@PathVariable Long catId, @RequestBody CatComment form,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(catCommentService.create(userId, catId, form));
    }

    @Operation(summary = "删除评论（作者本人或管理员）")
    @DeleteMapping("/comments/{commentId}")
    public Result<?> delete(@PathVariable Long commentId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        Integer role = (Integer) request.getAttribute(RoleInterceptor.ROLE_ATTR);
        catCommentService.delete(userId, role, commentId);
        return Result.success();
    }
}
