package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.dto.ReviewForm;
import com.straycat.entity.FeedingTask;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.FeedingTaskService;
import com.straycat.vo.FeedingRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "投喂管理")
@RestController
@RequestMapping("/feeding")
@RequiredArgsConstructor
public class FeedingController {

    private final FeedingTaskService feedingTaskService;

    @Operation(summary = "创建投喂任务")
    @PostMapping("/task")
    public Result<Long> createTask(@RequestBody FeedingTask task) {
        Long id = feedingTaskService.createTask(task);
        return Result.success(id);
    }

    @Operation(summary = "接单")
    @PostMapping("/task/{taskId}/accept")
    public Result<?> acceptTask(@PathVariable Long taskId, HttpServletRequest request) {
        Long volunteerId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        feedingTaskService.acceptTask(taskId, volunteerId);
        return Result.success();
    }

    @Operation(summary = "打卡")
    @PostMapping("/task/{taskId}/checkin")
    public Result<?> checkIn(
            @PathVariable Long taskId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false) String images,
            HttpServletRequest request) {
        Long volunteerId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        feedingTaskService.checkIn(taskId, volunteerId, latitude, longitude, images);
        return Result.success();
    }

    @Operation(summary = "任务列表")
    @GetMapping("/task/list")
    public Result<Page<FeedingTask>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Long sponsorId = (Boolean.TRUE.equals(mine))
                ? (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR) : null;
        Page<FeedingTask> result = feedingTaskService.listTasks(pageNum, pageSize, status, sponsorId, keyword);
        feedingTaskService.fillDisplayInfo(result.getRecords());
        return Result.success(result);
    }

    @Operation(summary = "任务详情")
    @GetMapping("/task/{id}")
    public Result<FeedingTask> detail(@PathVariable Long id) {
        return Result.success(feedingTaskService.getTaskDetail(id));
    }

    @Operation(summary = "某只猫咪最近的投喂打卡记录")
    @GetMapping("/record/cat/{catId}")
    public Result<List<FeedingRecordVO>> catRecords(@PathVariable Long catId) {
        return Result.success(feedingTaskService.listRecordsByCat(catId));
    }

    @Operation(summary = "我打的投喂卡")
    @GetMapping("/record/mine")
    public Result<Page<FeedingRecordVO>> myRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long volunteerId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        return Result.success(feedingTaskService.listMyRecords(volunteerId, pageNum, pageSize));
    }

    @Operation(summary = "打卡记录列表（审核工作台）")
    @GetMapping("/record/list")
    @RequireRole(3)
    public Result<List<FeedingRecordVO>> recordList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(feedingTaskService.listRecords(status, limit));
    }

    @Operation(summary = "审核投喂打卡记录")
    @PostMapping("/record/review")
    @RequireRole(3)
    public Result<?> reviewRecord(@RequestBody ReviewForm form, HttpServletRequest request) {
        Long reviewerId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        feedingTaskService.reviewRecord(form.getId(), reviewerId, form.getStatus(), form.getComment());
        return Result.success();
    }
}
