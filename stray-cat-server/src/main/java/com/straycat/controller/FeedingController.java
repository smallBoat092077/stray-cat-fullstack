package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.dto.ReviewForm;
import com.straycat.entity.FeedingTask;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.FeedingTaskService;
import com.straycat.service.VolunteerLevelService;
import com.straycat.vo.FeedingRecordVO;
import com.straycat.vo.VolunteerRankVO;
import com.straycat.vo.VolunteerStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "投喂管理")
@Slf4j
@RestController
@RequestMapping("/feeding")
@RequiredArgsConstructor
public class FeedingController {

    private final FeedingTaskService feedingTaskService;

    private final VolunteerLevelService volunteerLevelService;

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    @Operation(summary = "我的志愿者等级面板")
    @GetMapping("/volunteer/stats")
    public Result<VolunteerStatsVO> volunteerStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(volunteerLevelService.stats(userId));
    }

    @Operation(summary = "投喂排行榜（按已通过打卡数，昵称脱敏）")
    @GetMapping("/volunteer/leaderboard")
    public Result<List<VolunteerRankVO>> volunteerLeaderboard(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(volunteerLevelService.leaderboard(limit));
    }

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

    @Operation(summary = "拍照识别猫咪（小程序打卡的识别步骤）")
    @PostMapping("/recognize")
    public Result<Map<String, Object>> recognize(@RequestParam("photo") MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return Result.error("照片不能为空");
        }
        // AI 识别服务（stray-cat-recognizer）尚未接入生产，先保证功能闭环：
        // 返回 success=false，小程序端会显示猫咪选择器让志愿者手动选任务关联的猫，
        // 打卡本身走人工审核，不会因为跳过 AI 识别产生错误归属
        Map<String, Object> data = new HashMap<>();
        data.put("success", false);
        data.put("message", "自动识别暂不可用，请手动选择猫咪");
        return Result.success(data);
    }

    @Operation(summary = "带识别照片的打卡（照片随打卡一起上传）")
    @PostMapping("/checkin-with-recognize")
    public Result<?> checkInWithRecognize(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam Long taskId,
            @RequestParam(required = false) Long catId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) Integer portions,
            @RequestParam(required = false) String remark,
            HttpServletRequest request) {
        Long volunteerId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (photo == null || photo.isEmpty()) {
            return Result.error("打卡照片不能为空");
        }
        // catId 由任务关联决定（前端选的识别结果仅作参考），保证打卡归属不被错挂
        String imageUrl = savePhoto(photo);
        feedingTaskService.checkIn(taskId, volunteerId, latitude, longitude, imageUrl);
        return Result.success();
    }

    /** 打卡照片落盘，返回 /uploads/ 相对路径（与 /common/upload 同一存储目录） */
    private String savePhoto(MultipartFile photo) {
        try {
            String originalName = photo.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
            String fileName = UUID.randomUUID() + ext;
            File dir = new File(uploadPath).getAbsoluteFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            photo.transferTo(new File(dir, fileName).getAbsoluteFile());
            return "/uploads/" + fileName;
        } catch (IOException e) {
            log.error("打卡照片保存失败", e);
            throw new com.straycat.exception.BusinessException("打卡照片保存失败，请重试");
        }
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
