package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.straycat.client.RecognizerClient;
import com.straycat.common.CatAvatarUtil;
import com.straycat.config.RecognizerProperties;
import com.straycat.entity.CatProfile;
import com.straycat.entity.FeedingRecord;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.mapper.FeedingRecordMapper;
import com.straycat.service.RecognitionService;
import com.straycat.vo.RecognizeMatchVO;
import com.straycat.vo.RecognizeResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecognitionServiceImpl implements RecognitionService {

    private final RecognizerClient recognizerClient;
    private final RecognizerProperties props;
    private final CatProfileMapper catProfileMapper;
    private final FeedingRecordMapper feedingRecordMapper;

    @Override
    public RecognizeResultVO recognize(MultipartFile photo, Integer topK) {
        JsonNode response;
        try {
            // 返回 null 即降级（灰度关闭 / 熔断 / 超时 / 非 2xx / JSON 解析失败）
            response = recognizerClient.match(photo, topK);
        } catch (Exception e) {
            log.warn("识别转发异常，走降级: {}", e.getMessage());
            return RecognizeResultVO.degraded();
        }
        if (response == null) {
            return RecognizeResultVO.degraded();
        }
        try {
            return grade(response);
        } catch (Exception e) {
            log.warn("识别结果处理异常，走降级: {}", e.getMessage());
            return RecognizeResultVO.degraded();
        }
    }

    /** 二期：优先识别拒识闸门；其余只取 matches[].similarity，忽略 Python 的 success / message */
    private RecognizeResultVO grade(JsonNode response) {
        // 照片里没有可识别的猫咪：服务健康，但绝不返回任何候选，也不降级
        if (response.path("no_cat").asBoolean(false)) {
            String reason = response.path("reject_reason").asText(null);
            String tip = response.path("message").asText("");
            return RecognizeResultVO.rejected(reason, tip);
        }
        List<Raw> raws = new ArrayList<>();
        JsonNode matches = response.path("matches");
        if (matches.isArray()) {
            for (JsonNode m : matches) {
                long catId = m.path("cat_id").asLong(0L);
                if (catId == 0L) {   // /extract 写入的临时特征，必须过滤
                    continue;
                }
                JsonNode sim = m.path("similarity");
                if (!sim.isNumber()) {
                    continue;
                }
                raws.add(new Raw(catId, sim.decimalValue()));
            }
        }
        // Python 一般已按相似度降序，这里兜底重排，保证 best 就是第一个
        raws.sort((a, b) -> b.similarity().compareTo(a.similarity()));

        // Python 的 matches[].image_url 恒为空，按 cat_id 批量反查 cat_profile 补全
        Map<Long, CatProfile> cats = new HashMap<>();
        List<Long> ids = raws.stream().map(Raw::catId).distinct().toList();
        if (!ids.isEmpty()) {
            try {
                for (CatProfile cat : catProfileMapper.selectBatchIds(ids)) {
                    cats.put(cat.getId(), cat);
                }
            } catch (Exception e) {
                log.warn("反查 cat_profile 失败，候选保留 catId 与「未知猫咪」: {}", e.getMessage());
            }
        }

        List<RecognizeMatchVO> vos = new ArrayList<>();
        for (Raw raw : raws) {
            RecognizeMatchVO vo = new RecognizeMatchVO();
            CatProfile cat = cats.get(raw.catId());
            vo.setCatId(raw.catId());
            vo.setCatName(cat == null || cat.getName() == null ? "未知猫咪" : cat.getName());
            vo.setCoverImage(cat == null ? null : CatAvatarUtil.resolveCatAvatar(cat));
            vo.setColor(cat == null ? null : cat.getColor());
            vo.setSimilarity(raw.similarity());
            vos.add(vo);
        }

        BigDecimal best = vos.isEmpty() ? null : vos.get(0).getSimilarity();
        BigDecimal second = vos.size() > 1 ? vos.get(1).getSimilarity() : BigDecimal.ZERO;
        BigDecimal margin = best == null ? BigDecimal.ZERO : best.subtract(second);
        RecognizeResultVO result = new RecognizeResultVO();
        result.setDegraded(false);
        result.setRejected(false);
        result.setMatches(vos);
        if (best == null) {
            result.setSuccess(false);
            result.setConfidence("low");
            result.setMessage("未找到相似猫咪，请手动选择任务猫咪或重新拍照");
        } else if (best.compareTo(props.getMatchThreshold()) >= 0
                && margin.compareTo(props.getMarginThreshold()) >= 0) {
            result.setSuccess(true);
            result.setConfidence("high");
            result.setMessage("识别结果仅供参考，请对照猫咪照片人工核对后再确认");
        } else if (best.compareTo(props.getMatchThreshold()) >= 0) {
            // 二期：多个候选分数挤在一起时降为 medium，避免 indiscriminately 判高置信
            result.setSuccess(true);
            result.setConfidence("medium");
            result.setMessage("多个候选分数接近，请对照猫咪照片人工核对后点选");
        } else if (best.compareTo(props.getManualThreshold()) >= 0) {
            result.setSuccess(true);
            result.setConfidence("medium");
            result.setMessage("识别结果仅供参考，请自行点选确认猫咪");
        } else {
            result.setSuccess(false);
            result.setConfidence("low");
            result.setMessage("识别置信度较低，请手动选择任务猫咪或重新拍照");
        }
        return result;
    }

    @Override
    public void attachResult(Long taskId, Long volunteerId, Long matchedCatId,
                             BigDecimal similarity, Integer recognizeStatus) {
        try {
            if (taskId == null) {
                return;
            }
            LambdaQueryWrapper<FeedingRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FeedingRecord::getTaskId, taskId);
            if (volunteerId != null) {
                wrapper.eq(FeedingRecord::getVolunteerId, volunteerId);
            }
            // 同一任务只允许打卡一次，取刚插入的那条
            wrapper.orderByDesc(FeedingRecord::getId).last("limit 1");
            List<FeedingRecord> records = feedingRecordMapper.selectList(wrapper);
            if (records.isEmpty()) {
                log.warn("补写识别字段：未找到 taskId={} 的打卡记录", taskId);
                return;
            }
            // 只 set 4 个识别字段 + 主键，绝不 set cat_id（归属始终由 checkIn 内部按任务关联写入）
            FeedingRecord patch = new FeedingRecord();
            patch.setId(records.get(0).getId());
            patch.setMatchedCatId(matchedCatId);
            patch.setSimilarity(similarity);
            patch.setRecognizeStatus(recognizeStatus);
            patch.setRecognizedAt(LocalDateTime.now());
            feedingRecordMapper.updateById(patch);
        } catch (Exception e) {
            log.warn("补写识别字段失败（不影响打卡成功）: taskId={}, err={}", taskId, e.getMessage());
        }
    }

    private record Raw(Long catId, BigDecimal similarity) {}
}
