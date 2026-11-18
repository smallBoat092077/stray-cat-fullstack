package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.Result;
import com.straycat.entity.HomeContent;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.HomeLeaderboardService;
import com.straycat.service.HomeService;
import com.straycat.vo.HomeBootstrapVO;
import com.straycat.vo.MyRankVO;
import com.straycat.vo.RankItemVO;
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

import java.util.List;

/**
 * 首页免登接口（方案 5.1）。GET 全部匿名放行，写接口仅 /home/content/{id}/click 免登。
 */
@Tag(name = "首页")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final HomeLeaderboardService leaderboardService;

    @Operation(summary = "首屏聚合：热点/通告/三榜月榜Top5/模块开关（缓存60s）")
    @GetMapping("/bootstrap")
    public Result<HomeBootstrapVO> bootstrap() {
        return Result.success(homeService.bootstrap());
    }

    @Operation(summary = "三榜：type=feeding/food/active，period=week/month/all，limit<=50（缓存300s）")
    @GetMapping("/leaderboard")
    public Result<List<RankItemVO>> leaderboard(@RequestParam(defaultValue = "feeding") String type,
                                                @RequestParam(defaultValue = "month") String period,
                                                @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(leaderboardService.leaderboard(type, period, limit));
    }

    @Operation(summary = "我的名次（需登录，不缓存；未登录返回空）")
    @GetMapping("/leaderboard/mine")
    public Result<MyRankVO> mine(@RequestParam(defaultValue = "feeding") String type,
                                 @RequestParam(defaultValue = "month") String period,
                                 HttpServletRequest request) {
        Object uid = request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        Long userId = uid == null ? null : Long.valueOf(uid.toString());
        return Result.success(leaderboardService.mine(type, period, userId));
    }

    @Operation(summary = "通告列表（仅上线且在时间窗内）")
    @GetMapping("/notice/list")
    public Result<Page<HomeContent>> noticeList(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(homeService.noticeList(pageNum, pageSize));
    }

    @Operation(summary = "通告详情（pv 自增）")
    @GetMapping("/notice/{id}")
    public Result<HomeContent> noticeDetail(@PathVariable Long id) {
        return Result.success(homeService.noticeDetail(id));
    }

    @Operation(summary = "热点点击记数（匿名，失败不阻塞）")
    @PostMapping("/content/{id}/click")
    public Result<Void> click(@PathVariable Long id) {
        homeService.click(id);
        return Result.success(null);
    }
}
