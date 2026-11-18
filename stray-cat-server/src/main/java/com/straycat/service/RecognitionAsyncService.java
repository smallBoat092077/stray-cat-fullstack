package com.straycat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.straycat.client.RecognizerClient;
import com.straycat.config.RecognizerProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 打卡后的异步花纹识别（2026-11-15 性能优化 P0-2 路线 A）。
 *
 * <p>识别结果按既定设计只是辅助候选，<b>永远不决定 feeding_record.cat_id</b>，
 * 因此没有必要让用户点「提交」后同步等待。这里改为打卡主链路立即返回，
 * 识别在单线程有界队列里执行，完成后复用 RecognitionService.attachResult() 事后补写 4 列。
 *
 * <p>降级保护：队列满 / 调用异常 / 照片字节读取失败都只影响识别字段，打卡不受影响。
 * 线程池为 daemon，随应用关闭而 shutdown，不阻塞 JVM 退出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecognitionAsyncService {

    private static final int QUEUE_CAPACITY = 20;

    private final RecognizerClient recognizerClient;
    private final RecognitionService recognitionService;
    private final RecognizerProperties recognizerProperties;

    private final AtomicInteger threadSeq = new AtomicInteger();

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "recognize-async-" + threadSeq.incrementAndGet());
                t.setDaemon(true);
                return t;
            },
            (r, ex) -> log.warn("异步识别队列已满，本次识别跳过（不影响打卡）"));

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * 提交一次异步识别。调用方已经完成 checkIn，打卡记录此时已落库。
     *
     * @param photoBytes 打卡前缓存的同一张照片字节（MultipartFile 临时文件在请求结束后不可用）
     * @param catId      用户在候选列表中确认的猫，仅作 matched_cat_id 记录，不参与比对
     */
    public void submit(byte[] photoBytes, String filename,
                       Long taskId, Long volunteerId, Long catId) {
        try {
            executor.execute(() -> run(photoBytes, filename, taskId, volunteerId, catId));
        } catch (RuntimeException e) {
            log.warn("提交异步识别失败（不影响打卡）: taskId={}, err={}", taskId, e.getMessage());
        }
    }

    private void run(byte[] photoBytes, String filename,
                     Long taskId, Long volunteerId, Long catId) {
        long start = System.currentTimeMillis();
        boolean degraded = photoBytes == null;
        boolean noCat = false;
        String rejectReason = null;
        BigDecimal bestSimilarity = null;
        Long bestCatId = null;
        Best best = null;
        if (!degraded) {
            try {
                // 后台执行，用长超时避免冷启动误判降级；绝不抛到打卡链路
                JsonNode node = recognizerClient.matchWithLongTimeout(photoBytes, filename, null);
                if (node == null) {
                    degraded = true;
                } else {
                    noCat = node.path("no_cat").asBoolean(false);
                    rejectReason = node.path("reject_reason").asText(null);
                    best = parseBest(node);
                    bestSimilarity = best.similarity();
                    bestCatId = best.catId();
                }
            } catch (Exception e) {
                log.warn("异步识别调用异常（不影响打卡）: taskId={}, err={}", taskId, e.getMessage());
                degraded = true;
            }
        }
        int status = resolveRecognizeStatus(degraded, bestSimilarity, bestCatId, catId);
        recognitionService.attachResult(taskId, volunteerId, catId, bestSimilarity, status);
        // 三期：日志补全诊断字段，可区分「全局像但局部不匹配」/「闸门拒识」/「降级」。
        // 口径说明：matched_cat_id 落的是用户确认的任务猫 catId（上一步 attachResult 的入参），
        // bestCatId 只是识别 top1 的参考值，两者语义不同，勿混用。
        if (degraded) {
            log.info("异步识别完成: taskId={}, status=4(降级), degraded=true, costMs={}",
                    taskId, System.currentTimeMillis() - start);
        } else if (noCat) {
            log.info("异步识别完成: taskId={}, status={}, reject_reason={}, no_cat=true, costMs={}",
                    taskId, status, rejectReason, System.currentTimeMillis() - start);
        } else if (best != null && best.similarity() != null) {
            log.info("异步识别完成: taskId={}, status={}, bestCatId={}, similarity={}, "
                            + "global_cos={}, local_score={}, good_matches={}, inliers={}, "
                            + "matchedCatId(用户确认)={}, costMs={}",
                    taskId, status, bestCatId, bestSimilarity,
                    best.globalCos(), best.localScore(), best.goodMatches(), best.inliers(),
                    catId, System.currentTimeMillis() - start);
        } else {
            log.info("异步识别完成: taskId={}, status={}, 无候选, costMs={}",
                    taskId, status, System.currentTimeMillis() - start);
        }
    }

    /**
     * recognize_status 落库口径（与优化前完全一致）：
     * 4 降级/异常；3 best&lt;manualThreshold 或无候选；1 有候选且前端传 catId 且高置信且 catId==bestCatId；
     * 2 有候选且人工选了猫；0 其余。
     */
    private int resolveRecognizeStatus(boolean degraded, BigDecimal bestSimilarity,
                                       Long bestCatId, Long catId) {
        if (degraded) {
            return 4;
        }
        if (bestSimilarity == null
                || bestSimilarity.compareTo(recognizerProperties.getManualThreshold()) < 0) {
            return 3;
        }
        if (catId != null
                && bestSimilarity.compareTo(recognizerProperties.getMatchThreshold()) >= 0
                && catId.equals(bestCatId)) {
            return 1;
        }
        if (catId != null) {
            return 2;
        }
        return 0;
    }

    /** 只取 matches[]（忽略 Python 的 success/message），过滤 cat_id=0，返回最高分候选及诊断字段 */
    private Best parseBest(JsonNode node) {
        BigDecimal bestSimilarity = null;
        Long bestCatId = null;
        BigDecimal globalCos = null;
        BigDecimal localScore = null;
        Integer goodMatches = null;
        Integer inliers = null;
        JsonNode matches = node.path("matches");
        if (matches.isArray()) {
            for (JsonNode m : matches) {
                long catId = m.path("cat_id").asLong(0L);
                if (catId == 0L) {
                    continue;
                }
                JsonNode sim = m.path("similarity");
                if (!sim.isNumber()) {
                    continue;
                }
                BigDecimal value = sim.decimalValue();
                if (bestSimilarity == null || value.compareTo(bestSimilarity) > 0) {
                    bestSimilarity = value;
                    bestCatId = catId;
                    globalCos = decimalOrNull(m, "global_cos");
                    localScore = decimalOrNull(m, "local_score");
                    goodMatches = m.path("good_matches").isNumber()
                            ? m.path("good_matches").asInt() : null;
                    inliers = m.path("inliers").isNumber()
                            ? m.path("inliers").asInt() : null;
                }
            }
        }
        return new Best(bestSimilarity, bestCatId, globalCos, localScore, goodMatches, inliers);
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).decimalValue() : null;
    }

    private record Best(BigDecimal similarity, Long catId, BigDecimal globalCos,
                        BigDecimal localScore, Integer goodMatches, Integer inliers) {}
}
