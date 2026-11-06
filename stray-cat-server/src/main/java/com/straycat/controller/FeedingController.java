package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.client.RecognizerClient;
import com.straycat.common.Result;
import com.straycat.config.RecognizerProperties;
import com.straycat.dto.CheckinRecognizeForm;
import com.straycat.dto.ReviewForm;
import com.straycat.entity.CatProfile;
import com.straycat.entity.FeedingTask;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.CatProfileService;
import com.straycat.service.FeedingTaskService;
import com.straycat.vo.FeedingRecordVO;
import com.straycat.vo.RecognizeResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "投喂管理")
@RestController
@RequestMapping("/feeding")
@RequiredArgsConstructor
public class FeedingController {

    private final FeedingTaskService feedingTaskService;
    private final CatProfileService catProfileService;
    private final RecognizerClient recognizerClient;
    private final RecognizerProperties recognizerProperties;

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

    @Operation(summary = "拍照识别猫咪（打卡前）")
    @PostMapping("/recognize")
    public Result<RecognizeResultVO> recognizeCat(
            @RequestParam MultipartFile photo,
            @RequestParam(required = false) Long catId) {
        // 如果指定了 catId，直接注册
        if (catId != null) {
            Long featureId = recognizerClient.registerCat(photo, catId);
            if (featureId != null) {
                CatProfile cat = catProfileService.getById(catId);
                return Result.success(RecognizeResultVO.builder()
                        .success(true)
                        .message("猫咪注册成功")
                        .matches(List.of(RecognizeResultVO.MatchInfo.builder()
                                .catId(catId)
                                .similarity(1.0)
                                .catName(cat != null ? cat.getName() : null)
                                .avatar(cat != null ? cat.getCoverImage() : null)
                                .originalLng(cat != null ? cat.getLongitude() != null ? cat.getLongitude().doubleValue() : null : null)
                                .originalLat(cat != null ? cat.getLatitude() != null ? cat.getLatitude().doubleValue() : null : null)
                                .needsManualConfirm(false)
                                .build()))
                        .build());
            }
            return Result.success(RecognizeResultVO.builder()
                    .success(false)
                    .message("注册失败")
                    .build());
        }

        // 否则进行匹配
        List<RecognizerClient.MatchResult> matches = recognizerClient.matchCat(photo, 3);

        List<RecognizeResultVO.MatchInfo> matchInfos = matches.stream().map(m -> {
            CatProfile cat = catProfileService.getById(m.catId());
            double similarity = m.similarity();
            boolean needsManual = similarity >= recognizerProperties.getManualThreshold()
                    && similarity < recognizerProperties.getMatchThreshold();

            return RecognizeResultVO.MatchInfo.builder()
                    .catId(m.catId())
                    .similarity(similarity)
                    .catName(cat != null ? cat.getName() : null)
                    .avatar(cat != null ? cat.getCoverImage() : null)
                    .originalLng(cat != null && cat.getLongitude() != null ? cat.getLongitude().doubleValue() : null)
                    .originalLat(cat != null && cat.getLatitude() != null ? cat.getLatitude().doubleValue() : null)
                    .needsManualConfirm(needsManual)
                    .build();
        }).toList();

        boolean success = !matches.isEmpty() && matches.get(0).similarity() >= recognizerProperties.getMatchThreshold();

        return Result.success(RecognizeResultVO.builder()
                .success(success)
                .message(success ? "识别成功" : "未找到匹配的猫咪")
                .matches(matchInfos)
                .build());
    }

    @Operation(summary = "带识别的打卡")
    @PostMapping("/checkin-with-recognize")
    public Result<?> checkinWithRecognize(
            @RequestParam Long taskId,
            @RequestParam MultipartFile photo,
            @RequestParam Long catId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) Integer portions,
            @RequestParam(required = false) String remark,
            HttpServletRequest request) {
        Long volunteerId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);

        // 1. 先注册特征（关联 catId）
        Long featureId = recognizerClient.registerCat(photo, catId);
        if (featureId == null) {
            return Result.error("特征提取失败");
        }

        // 2. 执行打卡
        String images = ""; // 照片已上传到识别服务，这里可以保存 URL
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
            HttpServletRequest request) {
        Page<FeedingTask> page = new Page<>(pageNum, pageSize);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FeedingTask>();
        if (status != null) {
            wrapper.eq(FeedingTask::getStatus, status);
        }
        if (Boolean.TRUE.equals(mine)) {
            Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
            wrapper.eq(FeedingTask::getSponsorId, userId);
        }
        wrapper.orderByDesc(FeedingTask::getCreatedAt);
        Page<FeedingTask> result = feedingTaskService.page(page, wrapper);
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
