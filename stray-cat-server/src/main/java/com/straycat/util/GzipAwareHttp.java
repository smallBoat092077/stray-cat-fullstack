package com.straycat.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

/**
 * 轻量 HTTP GET 工具：统一处理和风天气"无视 Accept-Encoding、始终返回 gzip"的坑。
 *
 * 和风 API 即使请求不带 Accept-Encoding 也可能返回 gzip（Content-Encoding: gzip），
 * Java 11+ HttpClient 不会自动解压，直接按字符串解析会得到乱码/控制字符（CTRL-CHAR code 31）。
 * 这里根据响应头 Content-Encoding 判断，必要时用 GZIPInputStream 解压后再转成字符串。
 */
public final class GzipAwareHttp {

    private GzipAwareHttp() {
    }

    /** 发起 GET，返回解压后的响应文本；HTTP 非 2xx 返回 null */
    public static String get(String url, String apiKey, int connectTimeoutMs, int readTimeoutMs) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-QW-Api-Key", apiKey)
                .timeout(Duration.ofMillis(readTimeoutMs))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return null;
            }
            byte[] body = resp.body();
            String encoding = resp.headers().firstValue("Content-Encoding").orElse("").toLowerCase();
            if (encoding.contains("gzip")) {
                body = gunzip(body);
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }
}
