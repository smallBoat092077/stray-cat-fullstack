package com.straycat.config;

import com.straycat.client.RecognizerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 识别服务冷启动预热（二期：失败退避重试 + 独立 warmup 端点）。
 *
 * <p>一期用 96×96 随机噪点图打 /match，但二期新增 CatGate 拒识闸门后，噪点图会被
 * 闸门直接挡掉，/match 虽返回 HTTP 200 却没有真正触发 ORB / FAISS 冷加载，预热形同虚设。
 * 二期改调 Python 的 /api/recognize/warmup（跳过闸门、真实走一次特征提取 + FAISS 检索）。
 *
 * <p>另外，一期「启动预热失败」既不计入熔断失败计数、也没人补预热，导致 2026-11-15
 * 真机第一个拍照的用户撞上冷启动降级。这里改为失败退避重试最多 5 次（间隔 5s），
 * 仍失败则主动唤起 RecognizerClient 的恢复预热守护线程。
 *
 * <p>独立守护线程，不阻断 Spring Boot 启动与 DatabaseMigration；灰度关闭（如生产）完全不预热。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecognizerWarmupRunner implements ApplicationRunner {

    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_INTERVAL_MS = 5000L;

    private final RecognizerProperties props;
    private final RecognizerClient recognizerClient;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled()) {
            return;   // 灰度关闭（如生产）不做任何预热
        }
        Thread t = new Thread(() -> {
            try {
                // 稍等容器网络与 Python 服务 ready，避免无谓的失败计数
                Thread.sleep(1500);
                boolean ok = false;
                for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                    ok = recognizerClient.warmup() != null;
                    log.info("识别服务预热 {}/{}: warmOk={}, baseUrl={}",
                            attempt, MAX_ATTEMPTS, ok, props.getBaseUrl());
                    if (ok) {
                        break;
                    }
                    if (attempt < MAX_ATTEMPTS) {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    }
                }
                if (ok) {
                    log.info("识别服务预热完成: warmOk=true, baseUrl={}", props.getBaseUrl());
                } else {
                    log.warn("识别服务预热 {} 次仍失败，转恢复预热与降级兜底: baseUrl={}",
                            MAX_ATTEMPTS, props.getBaseUrl());
                    recognizerClient.requestRecoveryWarmup();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("识别服务预热异常（不影响启动）: {}", e.getMessage());
            }
        }, "recognizer-warmup");
        t.setDaemon(true);
        t.start();
    }
}
