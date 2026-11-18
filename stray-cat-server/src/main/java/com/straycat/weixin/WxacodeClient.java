package com.straycat.weixin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.entity.ShareLink;
import com.straycat.exception.BusinessException;
import com.straycat.service.ShareLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 带参小程序码。复用小程序 appid 的全局 access_token 缓存，
 * 同一 sid 落盘一次，重复调用不再打微信接口（同时规避 45009 限流）。
 *
 * <p>用 JDK HttpClient 而不是 RestTemplate：后者默认的请求头组合会被微信 API 网关
 * 以 412 Precondition Failed 拒绝（同一 token、同一 body 用 curl 正常返回 200），
 * 这里显式固定 Content-Type / Accept / User-Agent，行为与 curl 一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxacodeClient {

    private static final String API = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=";

    private final WxAccessTokenService wxAccessTokenService;
    private final ShareLinkService shareLinkService;

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public Map<String, Object> generate(String sid, Integer width, String envVersion) {
        if (sid == null || !sid.matches("[A-Za-z0-9]{6,12}")) {
            throw new BusinessException("短码不合法");
        }
        ShareLink link = shareLinkService.resolve(sid);
        if (link == null) {
            throw new BusinessException("短码不存在或已停用，无法生成小程序码");
        }
        String page = link.getLandingPage();
        if (page == null || page.isBlank()) {
            throw new BusinessException("该短码未配置落地页，无法生成小程序码");
        }

        int w = (width == null || width < 280 || width > 1280) ? 430 : width;
        String env = ("trial".equals(envVersion) || "develop".equals(envVersion)) ? envVersion : "release";
        boolean checkPath = "release".equals(env);

        Path dir = Paths.get(uploadPath).resolve("wxacode");
        Path file = dir.resolve(sid + ".png");
        String relativeUrl = "/uploads/wxacode/" + sid + ".png";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sid", sid);
        data.put("scene", "s=" + sid);
        data.put("page", page);
        data.put("envVersion", env);
        data.put("url", relativeUrl);

        if (Files.isRegularFile(file)) {
            data.put("cached", true);
            return data;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        // scene 上限 32 可见字符：只放短码，业务 id 由小程序端 resolve 补齐
        body.put("scene", "s=" + sid);
        body.put("page", page);
        body.put("width", w);
        body.put("check_path", checkPath);
        body.put("env_version", env);
        body.put("is_hyaline", false);

        byte[] image = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = wxAccessTokenService.get();
            if (token.isEmpty()) {
                throw new BusinessException("微信接口暂不可用（access_token 获取失败）");
            }
            try {
                String json = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API + token))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("Accept", "*/*")
                        .header("User-Agent", "curl/7.81.0")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<byte[]> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofByteArray());
                byte[] bytes = response.body();
                int errcode = (bytes != null && bytes.length > 0 && bytes[0] == '{')
                        ? errcodeOf(bytes) : -1;

                if (response.statusCode() != 200 || errcode > 0) {
                    log.warn("生成小程序码失败: sid={}, http={}, errcode={}",
                            sid, response.statusCode(), errcode);
                    if ((errcode == 40001 || errcode == 42001) && attempt == 0) {
                        wxAccessTokenService.invalidate();
                        continue;
                    }
                    if (errcode > 0) {
                        throw new BusinessException(errorMessage(errcode));
                    }
                    throw new BusinessException("微信接口返回 HTTP " + response.statusCode());
                }
                if (bytes == null || bytes.length == 0) {
                    throw new BusinessException("微信返回空内容");
                }
                image = bytes;
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("调用微信接口被中断");
            } catch (IOException e) {
                log.warn("调用微信生成小程序码异常: {}", e.getMessage());
                throw new BusinessException("调用微信接口失败：" + e.getMessage());
            }
        }

        if (image == null) {
            throw new BusinessException("微信凭证失效，请稍后重试");
        }
        try {
            Files.createDirectories(dir);
            Files.write(file, image);
        } catch (IOException e) {
            log.error("小程序码落盘失败: {}", file, e);
            throw new BusinessException("小程序码保存失败");
        }
        data.put("cached", false);
        data.put("bytes", image.length);
        return data;
    }

    private int errcodeOf(byte[] bytes) {
        try {
            JsonNode node = objectMapper.readTree(bytes);
            return node.path("errcode").asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String errorMessage(int errcode) {
        return switch (errcode) {
            case 41030 -> "页面不存在或小程序未发布（41030），请检查落地页与发布状态";
            case 45009 -> "触发微信接口限流（45009），请稍后再试";
            case 40001, 42001 -> "微信凭证失效，请稍后重试";
            case 40002 -> "凭证类型不合法，请检查小程序 appid/secret 配置";
            default -> "微信小程序码接口返回错误（" + errcode + "）";
        };
    }
}
