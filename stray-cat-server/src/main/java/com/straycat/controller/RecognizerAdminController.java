package com.straycat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.straycat.annotation.RequireRole;
import com.straycat.client.RecognizerClient;
import com.straycat.common.PhotoValidator;
import com.straycat.common.Result;
import com.straycat.mapper.CatFeatureMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 花纹样本管理（方案 5.2 / 7.2；三期：原图落盘 + 多图管理），接口全部 @RequireRole(3)。 */
@Tag(name = "花纹样本管理（管理员）")
@Slf4j
@RestController
@RequestMapping("/admin/recognize")
@RequiredArgsConstructor
public class RecognizerAdminController {

    private final RecognizerClient recognizerClient;
    private final CatFeatureMapper catFeatureMapper;

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    @Operation(summary = "识别统计：透传 Python stats + 本地 cat_feature 登记数")
    @GetMapping("/stats")
    @RequireRole(3)
    public Result<Map<String, Object>> stats() {
        JsonNode stats = recognizerClient.stats();   // null = Python 不可达 / 降级
        boolean degraded = stats == null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalFeatures", degraded ? 0 : stats.path("total_features").asInt(0));
        data.put("totalCats", degraded ? 0 : stats.path("total_cats").asInt(0));
        data.put("indexSize", degraded ? 0 : stats.path("index_size").asInt(0));
        // 二期：透传闸门与特征维度，便于 UAT 核对版本
        data.put("featureDim", degraded ? 0 : stats.path("feature_dim").asInt(0));
        data.put("gateEnabled", !degraded && stats.path("gate_enabled").asBoolean(false));
        data.put("usableFeatures", degraded ? 0 : stats.path("usable_features").asInt(0));
        int localSamples = 0;
        try {
            localSamples = catFeatureMapper.countAll();
        } catch (Exception e) {
            // cat_feature 由 Python 自建，未建表时按 0 处理，绝不 500
            log.warn("统计本地 cat_feature 样本数失败: {}", e.getMessage());
        }
        data.put("localSamples", localSamples);
        data.put("degraded", degraded);
        return Result.success(data);
    }

    @Operation(summary = "只读 cat_feature 样本列表（catId 可选）")
    @GetMapping("/samples")
    @RequireRole(3)
    public Result<List<Map<String, Object>>> samples(@RequestParam(required = false) Long catId) {
        try {
            return Result.success(catFeatureMapper.samples(catId));
        } catch (Exception e) {
            log.warn("读取 cat_feature 样本列表失败: {}", e.getMessage());
            return Result.success(new ArrayList<>());
        }
    }

    @Operation(summary = "登记一只猫的花纹基准图（原图落盘 + 转发 Python /register）")
    @PostMapping("/register")
    @RequireRole(3)
    public Result<Map<String, Object>> register(@RequestParam("photo") MultipartFile photo,
                                                 @RequestParam Long catId) {
        Map<String, Object> data = new LinkedHashMap<>();
        String invalid = PhotoValidator.validate(photo);
        if (invalid != null) {
            return Result.error(invalid);
        }
        // 三期：原图落盘 {upload-path}/recognizer/{catId}/，失败的登记不残留文件
        File saved = saveSamplePhoto(photo, catId);
        JsonNode response = recognizerClient.register(photo, catId,
                saved == null ? null : "/uploads/recognizer/" + catId + "/" + saved.getName());
        boolean cleanup = false;
        if (response == null) {
            cleanup = true;
            data.put("featureId", null);
            data.put("message", "识别服务暂不可用，请稍后再试");
        } else if (response.path("no_cat").asBoolean(false)
                || !response.path("success").asBoolean(false)) {
            // 二期：Python 闸门拒识 / 特征提取失败时返回 success=false + feature_id=null，
            // 必须明确报错，否则前端会误以为基准图登记成功（库里其实什么都没写入）
            cleanup = true;
            return Result.error(response.path("message").asText("照片不符合基准图要求，请换一张猫咪照片"));
        } else {
            JsonNode featureId = response.path("feature_id");
            data.put("featureId", featureId.isNumber() ? featureId.asLong() : null);
            data.put("imageUrl", saved == null ? "" : "/uploads/recognizer/" + catId + "/" + saved.getName());
            data.put("message", response.path("message").asText("登记完成"));
        }
        if (cleanup && saved != null && !saved.delete()) {
            log.warn("登记失败后原图清理失败: {}", saved.getAbsolutePath());
        }
        return Result.success(data);
    }

    @Operation(summary = "删除一条基准样本（转发 Python /delete 并重建索引）")
    @PostMapping("/samples/{featureId}/delete")
    @RequireRole(3)
    public Result<Map<String, Object>> deleteSample(@PathVariable Long featureId) {
        Map<String, Object> data = new LinkedHashMap<>();
        JsonNode response = recognizerClient.deleteFeature(featureId);
        if (response == null) {
            return Result.error("识别服务暂不可用，请稍后再试");
        }
        if (!response.path("success").asBoolean(false)) {
            return Result.error(response.path("message").asText("删除失败，请稍后再试"));
        }
        data.put("featureId", featureId);
        data.put("message", response.path("message").asText("删除成功"));
        return Result.success(data);
    }

    /** 三期：基准原图落盘 {upload-path}/recognizer/{catId}/uuid.ext；失败不影响登记流程（只丢 image_url） */
    private File saveSamplePhoto(MultipartFile photo, Long catId) {
        try {
            String originalName = photo.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
            File dir = new File(uploadPath, "recognizer" + File.separatorChar + catId).getAbsoluteFile();
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("基准原图目录创建失败: {}", dir.getAbsolutePath());
                return null;
            }
            File dest = new File(dir, UUID.randomUUID() + ext);
            photo.transferTo(dest.getAbsoluteFile());
            return dest;
        } catch (IOException | IllegalStateException e) {
            // 落盘失败不阻断登记：特征/描述子仍可入库，仅损失「基准图预览」能力
            log.warn("基准原图落盘失败（登记继续，仅无预览图）: {}", e.getMessage());
            return null;
        }
    }
}
