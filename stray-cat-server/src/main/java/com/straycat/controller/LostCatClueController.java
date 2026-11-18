package com.straycat.controller;

import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.request.LostCatClueRequest;
import com.straycat.service.LostCatClueService;
import com.straycat.vo.LostCatClueVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 寻猫线索上报（方案 4）。提交需登录；详情是匿名放行的 GET，可见性在 Service 内判定。
 */
@Tag(name = "寻猫线索")
@RestController
@RequestMapping("/lost-cat/clue")
@RequiredArgsConstructor
public class LostCatClueController {

    private final LostCatClueService lostCatClueService;

    @Operation(summary = "提交线索（需登录，先审后发）")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody LostCatClueRequest request, HttpServletRequest httpRequest) {
        return Result.success(lostCatClueService.create(requireUserId(httpRequest), request));
    }

    @Operation(summary = "线索详情（匿名放行，Service 内校验可见性）")
    @GetMapping("/detail/{id}")
    public Result<LostCatClueVO> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.success(lostCatClueService.detail(id, optionalUserId(httpRequest)));
    }

    @Operation(summary = "我的线索（需登录，含审核状态与驳回原因）")
    @GetMapping("/mine")
    public Result<List<LostCatClueVO>> mine(HttpServletRequest httpRequest) {
        return Result.success(lostCatClueService.mine(requireUserId(httpRequest)));
    }

    private Long requireUserId(HttpServletRequest request) {
        Object uid = request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Long.valueOf(uid.toString());
    }

    /** 详情匿名放行：带 token 才有值，未登录返回 null 交给 Service 判可见性 */
    private Long optionalUserId(HttpServletRequest request) {
        Object uid = request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        return uid == null ? null : Long.valueOf(uid.toString());
    }
}
