package com.straycat.service;

import com.straycat.vo.RecognizeResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public interface RecognitionService {

    /**
     * 转发识别 → 过滤 cat_id=0 → 反查 cat_profile 补全 → 按 Java 阈值三级分级。
     * 任何异常 / 降级都返回降级 VO，绝不抛出。
     */
    RecognizeResultVO recognize(MultipartFile photo, Integer topK);

    /**
     * 打卡成功后按 task_id 找到刚插入的 feeding_record，只补写 4 个识别字段。
     * 任何异常只 log.warn，绝不抛出影响打卡成功；绝不触碰 cat_id（归属由 checkIn 决定）。
     */
    void attachResult(Long taskId, Long volunteerId, Long matchedCatId,
                      BigDecimal similarity, Integer recognizeStatus);
}
