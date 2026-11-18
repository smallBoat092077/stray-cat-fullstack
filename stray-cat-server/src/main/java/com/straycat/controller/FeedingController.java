package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.PhotoValidator;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.config.RecognizerProperties;
import com.straycat.dto.ReviewForm;
import com.straycat.entity.FeedingTask;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.FeedingTaskService;
import com.straycat.service.RecognitionAsyncService;
import com.straycat.service.RecognitionService;
import com.straycat.service.VolunteerLevelService;
import com.straycat.vo.FeedingRecordVO;
import com.straycat.vo.RecognizeConfigVO;
import com.straycat.vo.RecognizeResultVO;
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

    private final RecognitionService recognitionService;

    private final RecognitionAsyncService recognitionAsyncService;

    private final RecognizerProperties recognizerProperties;

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

    @Operation(summary = "拍照识别灰度配置（打卡页 onLoad 取一次，GET 匿名放行）")
    @GetMapping("/recognize/config")
    public Result<RecognizeConfigVO> recognizeConfig() {
        RecognizeConfigVO vo = new RecognizeConfigVO();
        vo.setEnabled(recognizerProperties.isEnabled());
        vo.setMatchThreshold(recognizerProperties.getMatchThreshold());
        vo.setManualThreshold(recognizerProperties.getManualThreshold());
        vo.setTopK(recognizerProperties.getTopK());
        return Result.success(vo);
    }

    @Operation(summary = "拍照识别猫咪（转发识别服务，异常/降级一律 HTTP 200 返回降级结果）")
    @PostMapping("/recognize")
    public Result<RecognizeResultVO> recognize(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(required = false) Long catId,
            @RequestParam(required = false) Integer topK) {
        String invalid = validatePhoto(photo);
        if (invalid != null) {
            return Result.error(invalid);
        }
        try {
            // catId 是前端「用户确认的候选」，识别阶段不参与比对，仅保留入参兼容小程序契约
            return Result.success(recognitionService.recognize(photo, topK));
        } catch (Exception e) {
            log.warn("拍照识别异常，返回降级结果: {}", e.getMessage());
            return Result.success(RecognizeResultVO.degraded());
        }
    }

    @Operation(summary = "带识别照片的打卡（复用同一张照片取识别分数，归属仍由任务决定）")
    @PostMapping("/checkin-with-recognize")
    public Result<Map<String, Object>> checkInWithRecognize(
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
        String invalid = validatePhoto(photo);
        if (invalid != null) {
            return Result.error(invalid);
        }

        // 先缓存本次上传的字节：savePhoto 的 transferTo 会移动容器临时文件，
        // 请求结束后 MultipartFile 临时文件不可用，异步识别只能依赖这份字节。
        byte[] photoBytes = null;
        try {
            photoBytes = photo.getBytes();
        } catch (IOException e) {
            log.warn("读取打卡照片字节失败（异步识别按降级处理，不影响打卡）: {}", e.getMessage());
        }

        // 1) 落盘（存量 savePhoto 零改动）
        String imageUrl = savePhoto(photo);

        // 2) 存量打卡逻辑零改动：cat_id 由 checkIn 内部按任务关联写入，识别结果永不自动定归属
        feedingTaskService.checkIn(taskId, volunteerId, latitude, longitude, imageUrl);

        // 3) 识别是纯辅助动作，改为后台异步执行，打卡立即返回；
        //    完成后由 RecognitionAsyncService 复用 attachResult 事后补写 4 个识别字段。
        recognitionAsyncService.submit(photoBytes, photo.getOriginalFilename(),
                taskId, volunteerId, catId);

        Map<String, Object> data = new HashMap<>();
        data.put("recognizeStatus", 0); // 0=处理中/未识别，异步完成后补写真实状态
        data.put("matchedCatId", catId);
        return Result.success(data);
    }

    /** 图片真实性校验统一走 PhotoValidator（与管理端 register 共用同一口径） */
    private String validatePhoto(MultipartFile photo) {
        return PhotoValidator.validate(photo);
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
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String districtCode,
            HttpServletRequest request) {
        Long sponsorId = (Boolean.TRUE.equals(mine))
                ? (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR) : null;
        // 区域过滤（区域划分需求 D7：按猫的归属区域）；不带参数 = 全部口径
        Page<FeedingTask> result = feedingTaskService.listTasks(pageNum, pageSize, status, sponsorId, keyword,
                provinceCode, cityCode, districtCode);
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
