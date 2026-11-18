package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.MessageType;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatTransferApply;
import com.straycat.entity.CatTransferPost;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.CatTransferService;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.NotifyService;
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

import java.util.List;
import java.util.Map;

/**
 * 爱猫搬家（个人转让 → 信息流 → 私人对接，不提供聊天窗口）。
 * 手机号只出现在「发布人看申请」「申请人看自己」两个受控接口，列表/详情一律脱敏。
 */
@Tag(name = "爱猫搬家", description = "转让信息发布、申请与审核接口")
@RestController
@RequestMapping("/transfer")
@RequiredArgsConstructor
public class CatTransferController {

    private final CatTransferService catTransferService;
    private final ContentSecurityService contentSecurityService;
    private final NotifyService notifyService;

    @GetMapping("/posts")
    @Operation(summary = "转让列表（只返回在架，不含联系方式）")
    public Result<Page<CatTransferPost>> posts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String districtCode,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String keyword) {
        return Result.success(catTransferService.publicPosts(
                pageNum, pageSize, provinceCode, cityCode, districtCode, breed, keyword));
    }

    @GetMapping("/post/{id}")
    @Operation(summary = "转让详情（浏览量+1；不返回联系方式）")
    public Result<CatTransferPost> detail(@PathVariable Long id, HttpServletRequest request) {
        CatTransferPost post = catTransferService.publicDetail(id, userId(request));
        return post == null ? Result.error("转让信息不存在或不可查看") : Result.success(post);
    }

    @PostMapping("/post")
    @Operation(summary = "发布转让（内容安全→待审；同一用户最多 3 条）")
    public Result<Long> publish(@RequestBody CatTransferPost post, HttpServletRequest request) {
        Long userId = requireLogin(request);
        contentSecurityService.check(userId, ContentSecurityService.SCENE_SOCIAL,
                post.getCatName(), post.getReason(), post.getRequirement(),
                post.getHealthNote(), post.getPersonality());
        return Result.success(catTransferService.publish(post, userId));
    }

    @GetMapping("/mine")
    @Operation(summary = "我发布的（含待审/驳回，手机号不脱敏）")
    public Result<List<CatTransferPost>> mine(HttpServletRequest request) {
        return Result.success(catTransferService.mine(requireLogin(request)));
    }

    @PostMapping("/post/{id}/offline")
    @Operation(summary = "下架（仅发布人）")
    public Result<?> offline(@PathVariable Long id, HttpServletRequest request) {
        catTransferService.offline(id, requireLogin(request));
        return Result.success();
    }

    @PostMapping("/apply")
    @Operation(summary = "提交接猫申请，并通知发布人")
    public Result<Long> apply(@RequestBody CatTransferApply apply, HttpServletRequest request) {
        Long userId = requireLogin(request);
        contentSecurityService.check(userId, ContentSecurityService.SCENE_SOCIAL, apply.getApplyNote());
        return Result.success(catTransferService.apply(apply, userId));
    }

    @GetMapping("/my-applies")
    @Operation(summary = "我的申请（含处理状态与猫名，手机号不脱敏）")
    public Result<List<CatTransferApply>> myApplies(HttpServletRequest request) {
        return Result.success(catTransferService.myApplies(requireLogin(request)));
    }

    @GetMapping("/applies/{postId}")
    @Operation(summary = "某条转让收到的申请（仅发布人，含手机号）")
    public Result<List<CatTransferApply>> applies(@PathVariable Long postId, HttpServletRequest request) {
        return Result.success(catTransferService.appliesOfPost(postId, requireLogin(request)));
    }

    @PostMapping("/apply/{id}/status")
    @Operation(summary = "更新申请处理状态（仅该 post 发布人）")
    public Result<?> applyStatus(@PathVariable Long id, @RequestParam Integer status,
                                 HttpServletRequest request) {
        catTransferService.updateApplyStatus(id, status, requireLogin(request));
        return Result.success();
    }

    @PostMapping("/post/{id}/complete")
    @Operation(summary = "标记已转让并通知全部申请人（仅发布人）")
    public Result<?> complete(@PathVariable Long id, HttpServletRequest request) {
        catTransferService.complete(id, requireLogin(request));
        return Result.success();
    }

    @PostMapping("/report")
    @Operation(summary = "举报疑似交易/虚假信息（发给管理员）")
    public Result<?> report(@RequestParam Long postId, @RequestParam String reason,
                            HttpServletRequest request) {
        requireLogin(request);
        if (postId == null || reason == null || reason.isBlank()) {
            return Result.error(ResultCode.PARAM_MISSING.getCode(), "缺少举报信息");
        }
        notifyService.sendToAdmins(MessageType.FEEDBACK_NEW, postId,
                "爱猫搬家举报: " + reason, Map.of());
        return Result.success();
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
    }

    private Long requireLogin(HttpServletRequest request) {
        Long userId = userId(request);
        if (userId == null) {
            // GET 默认匿名放行，登录专属接口要显式判空
            throw new com.straycat.exception.BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
