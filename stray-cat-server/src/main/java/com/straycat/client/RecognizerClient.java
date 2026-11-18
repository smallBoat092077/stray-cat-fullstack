package com.straycat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.config.RecognizerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内网识别服务 HTTP 客户端（JDK HttpClient，不引入新依赖，写法对齐 WxacodeClient）。
 *
 * <p><b>降级约定（务必遵守）</b>：match / register / stats 均返回 Python 的原始响应 JsonNode，
 * <b>返回 null 即表示「本次调用降级」</b>（灰度关闭、熔断冷却中、连接/读取超时、非 2xx、
 * JSON 解析失败）。调用方必须据此走兜底，绝不能把 null 当成功结果使用。
 * Python 的 success / message 一律忽略，只由上层按 matches[].similarity 自行分级。
 *
 * <p>熔断：AtomicInteger 连续失败计数 + volatile cooldownUntil。连续 3 次失败冷却 30s，
 * 冷却期内直接短路；任一成功清零。所有异常只 log.warn，不 error 刷屏。
 */
@Slf4j
@Service
public class RecognizerClient {

    private static final String MATCH_PATH = "/api/recognize/match";
    private static final String REGISTER_PATH = "/api/recognize/register";
    private static final String STATS_PATH = "/api/recognize/stats";

    private static final int FAILURE_TRIP = 3;

    private final RecognizerProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long cooldownUntil = 0L;
    private final java.util.concurrent.atomic.AtomicBoolean recoveryWarmupRunning =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public RecognizerClient(RecognizerProperties props) {
        this.props = props;
        // JDK HttpClient 只有连接超时；读超时用 HttpRequest.timeout 近似
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
    }

    /** 灰度开关是否打开（供上层判断，不改变降级约定） */
    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** 当前是否处于熔断冷却期 */
    public boolean isInCooldown() {
        return System.currentTimeMillis() < cooldownUntil;
    }

    // ---------------- /api/recognize/match ----------------

    /** 用 MultipartFile 调 /match，top_k 取配置默认值 */
    public JsonNode match(MultipartFile file) {
        return match(file, null);
    }

    /** /feeding/recognize 走这里：允许前端覆盖 top_k */
    public JsonNode match(MultipartFile file, Integer topK) {
        if (file == null) {
            return null;
        }
        try {
            return match(file.getBytes(), file.getOriginalFilename(), topK);
        } catch (IOException e) {
            recordFailure();
            log.warn("读取识别照片失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 允许直接传已缓存的字节。打卡链路在 savePhoto 落盘前先把 MultipartFile 字节读进内存，
     * 因为 transferTo 会移动容器临时文件，之后再从同一 MultipartFile 读字节会失败；
     * 识别与落盘复用同一份字节，避免二次上传。
     */
    public JsonNode match(byte[] bytes, String filename, Integer topK) {
        return match(bytes, filename, topK, props.getMatchTimeoutMs());
    }

    /** 预热专用：冷启动首次 match 可达数秒，用长超时避免误计失败 */
    public JsonNode matchWithLongTimeout(byte[] bytes, String filename, Integer topK) {
        return match(bytes, filename, topK, props.getRegisterTimeoutMs());
    }

    private JsonNode match(byte[] bytes, String filename, Integer topK, int timeoutMs) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("top_k", String.valueOf(resolveTopK(topK)));
        // /match 是用户等待的交互链路，收紧整体超时以快速降级；register/预热仍走独立的长超时
        return postMultipart(MATCH_PATH, bytes, filename, fields, timeoutMs);
    }

    // ---------------- /api/recognize/register ----------------

    public JsonNode register(MultipartFile file, Long catId) {
        return register(file, catId, null);
    }

    /**
     * 三期：登记时把原图 /uploads 相对路径作为 image_url 传给 Python，
     * 落进 cat_feature.image_url（复用现有列，不改表）。
     * 之前 Java 一直没传该字段，导致样本列表「基准图」列恒空、无法复查。
     */
    public JsonNode register(MultipartFile file, Long catId, String imageUrl) {
        if (file == null || catId == null) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            recordFailure();
            log.warn("读取登记照片失败: {}", e.getMessage());
            return null;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("cat_id", String.valueOf(catId));
        if (imageUrl != null && !imageUrl.isBlank()) {
            fields.put("image_url", imageUrl);
        }
        // 写操作用更长超时：超时后 Python 仍可能完成写库，短超时会造成「报失败但已入库」的不一致
        return postMultipart(REGISTER_PATH, bytes, file.getOriginalFilename(), fields,
                props.getRegisterTimeoutMs());
    }

    // ---------------- 三期：删除基准样本 ----------------

    /**
     * 删除一条基准特征（Python 侧删行并重建 FAISS 索引）。
     * 返回 null 表示降级；上层按 success 字段判定结果。
     */
    public JsonNode deleteFeature(Long featureId) {
        if (featureId == null || !callable()) {
            return null;
        }
        try {
            String boundary = "----straycat" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ascii(out, "--" + boundary + "\r\n");
            ascii(out, "Content-Disposition: form-data; name=\"feature_id\"\r\n\r\n");
            out.write(String.valueOf(featureId).getBytes(StandardCharsets.UTF_8));
            ascii(out, "\r\n--" + boundary + "--\r\n");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url("/api/recognize/delete")))
                    .timeout(Duration.ofMillis(props.getRegisterTimeoutMs()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return handle("/api/recognize/delete", response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordFailure();
            log.warn("识别服务删除样本调用被中断");
        } catch (IOException | IllegalArgumentException e) {
            recordFailure();
            log.warn("识别服务删除样本调用失败: {}", e.getMessage());
        }
        return null;
    }

    // ---------------- 二期：启动 / 恢复预热 ----------------

    /**
     * 预热专用：走 Python /api/recognize/warmup，跳过 CatGate 但真实触发
     * ORB 特征提取 + FAISS 检索的冷加载。一期「噪点图打 /match」的方式在二期闸门下
     * 会被直接拒识，虽返回 200 却没走到冷加载逻辑，故独立成端点。
     * 返回 null 表示本次调用降级（灰度关闭 / 熔断 / 超时 / 非 2xx）。
     */
    public JsonNode warmup() {
        if (!callable()) {
            return null;
        }
        return postMultipart("/api/recognize/warmup", new byte[0], "warmup.png",
                new LinkedHashMap<>(), props.getRegisterTimeoutMs());
    }

    /** 供启动预热全部失败时主动唤起恢复预热守护线程（正常路径由熔断自动调度） */
    public void requestRecoveryWarmup() {
        scheduleRecoveryWarmup();
    }

    // ---------------- /api/recognize/stats ----------------

    public JsonNode stats() {
        if (!callable()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url(STATS_PATH)))
                    .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return handle(STATS_PATH, response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordFailure();
            log.warn("识别服务 stats 调用被中断");
        } catch (IOException | IllegalArgumentException e) {
            recordFailure();
            log.warn("识别服务 stats 调用失败: {}", e.getMessage());
        }
        return null;
    }

    // ---------------- 内部实现 ----------------

    private JsonNode postMultipart(String path, byte[] bytes, String filename,
                                   Map<String, String> fields) {
        return postMultipart(path, bytes, filename, fields, props.getReadTimeoutMs());
    }

    private JsonNode postMultipart(String path, byte[] bytes, String filename,
                                   Map<String, String> fields, int timeoutMs) {
        if (!callable()) {
            return null;
        }
        try {
            MultipartBody body = buildMultipart(bytes, filename, fields);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url(path)))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "multipart/form-data; boundary=" + body.boundary())
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return handle(path, response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordFailure();
            log.warn("识别服务调用被中断: path={}", path);
        } catch (IOException | IllegalArgumentException e) {
            recordFailure();
            log.warn("识别服务调用失败: path={}, err={}", path, e.getMessage());
        }
        return null;
    }

    /** 非 2xx / JSON 解析失败计失败；2xx 且解析成功才算成功并清零计数 */
    private JsonNode handle(String path, HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            recordFailure();
            log.warn("识别服务返回非 2xx: path={}, http={}", path, status);
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(response.body());
            if (node == null || node.isNull()) {
                recordFailure();
                log.warn("识别服务响应为空: path={}", path);
                return null;
            }
            markSuccess();
            return node;
        } catch (Exception e) {
            recordFailure();
            log.warn("识别服务响应 JSON 解析失败: path={}, err={}", path, e.getMessage());
            return null;
        }
    }

    /** 灰度关闭或冷却期内直接短路（不计失败，冷却期才不会无限延长） */
    private boolean callable() {
        if (!props.isEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < cooldownUntil) {
            log.warn("识别服务熔断冷却中，短路本次调用，剩余 {}s", (cooldownUntil - now) / 1000);
            return false;
        }
        return true;
    }

    private void recordFailure() {
        int n = consecutiveFailures.incrementAndGet();
        if (n >= FAILURE_TRIP) {
            cooldownUntil = System.currentTimeMillis()
                    + props.getFailureCooldownSeconds() * 1000L;
            consecutiveFailures.set(0); // 本轮已结算，冷却结束后重新累计，避免一直卡在冷却
            log.warn("识别服务连续失败 {} 次，进入熔断冷却 {}s", n, props.getFailureCooldownSeconds());
            scheduleRecoveryWarmup();
        }
    }

    /**
     * 熔断后启动一个守护线程，等服务恢复（含单独重启识别服务的冷启动）时用长超时
     * 走一次 /warmup 预热，避免运维只重启 recognizer 时首个用户请求撞超时降级。
     * 二期也可由启动预热全部失败时主动调用。不影响用户请求链路：
     * 探测走 stats，预热走独立端点，全程异步，失败即退出。
     */
    private void scheduleRecoveryWarmup() {
        if (!recoveryWarmupRunning.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                int maxProbes = 20;   // 最多约 20 × 3s = 60s，覆盖 30s 冷却 + 冷启动
                for (int i = 0; i < maxProbes; i++) {
                    Thread.sleep(3000);
                    if (!props.isEnabled()) {
                        return;
                    }
                    if (System.currentTimeMillis() < cooldownUntil) {
                        continue;   // 冷却期内不探测，避免延长冷却
                    }
                    // stats 走 callable()，会自然短路冷却期；恢复后用它判断是否可达
                    if (stats() == null) {
                        continue;
                    }
                    boolean ok = warmup() != null;
                    log.info("识别服务恢复后重新预热完成: warmOk={}", ok);
                    return;
                }
                log.warn("识别服务恢复预热探测超时退出，后续请求仍会自动重试");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("识别服务恢复预热异常（不影响主链路）: {}", e.getMessage());
            } finally {
                recoveryWarmupRunning.set(false);
            }
        }, "recognizer-recovery-warmup");
        t.setDaemon(true);
        t.start();
    }

    /** 96×96 噪点图，保证 ORB 走完 extract_feature + FAISS 检索全流程；不落盘、不写库 */
    private byte[] warmupPng() throws java.io.IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                96, 96, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.util.Random rnd = new java.util.Random(20261115L);
        for (int y = 0; y < 96; y++) {
            for (int x = 0; x < 96; x++) {
                img.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private void markSuccess() {
        consecutiveFailures.set(0);
        cooldownUntil = 0L;
    }

    private int resolveTopK(Integer topK) {
        return (topK == null || topK <= 0) ? props.getTopK() : topK;
    }

    private String url(String path) {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://recognizer:8000";
        }
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    /** 手工拼 multipart/form-data：普通 Form 字段 + 文件 part(name=file) + 结束 boundary */
    private MultipartBody buildMultipart(byte[] bytes, String filename,
                                         Map<String, String> fields) throws IOException {
        String boundary = "----straycat" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            ascii(out, "--" + boundary + "\r\n");
            ascii(out, "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n");
            out.write(field.getValue().getBytes(StandardCharsets.UTF_8));
            ascii(out, "\r\n");
        }
        ascii(out, "--" + boundary + "\r\n");
        ascii(out, "Content-Disposition: form-data; name=\"file\"; filename=\""
                + safeFilename(filename) + "\"\r\n");
        ascii(out, "Content-Type: " + contentType(filename) + "\r\n\r\n");
        out.write(bytes);
        ascii(out, "\r\n");
        ascii(out, "--" + boundary + "--\r\n");
        return new MultipartBody(boundary, out.toByteArray());
    }

    private static void ascii(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 头部里的文件名做清洗，避免 CRLF / 引号注入 */
    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "photo.jpg";
        }
        String name = filename.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private static String contentType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private record MultipartBody(String boundary, byte[] bytes) {}
}
