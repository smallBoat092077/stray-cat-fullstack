package com.straycat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.service.SecurityMailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 腾讯云 SES 发信实现（备选通道，个人开发者一般开不了；当前生产主通道为 QQ 邮箱 SMTP）。
 * TC3-HMAC-SHA256 签名，仅用 JDK 自带 HttpClient + 加密 API，不引入额外依赖。
 * 激活条件：security.mail.provider=ses。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "security.mail", name = "provider", havingValue = "ses")
public class SesSecurityMailSender implements SecurityMailSender {

    private static final String HOST = "ses.tencentcloudapi.com";
    private static final String SERVICE = "ses";
    private static final String ENDPOINT = "https://" + HOST;
    private static final String VERSION = "2020-10-02";
    private static final String ACTION = "SendEmail";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${security.mail.ses-secret-id:}")
    private String secretId;

    @Value("${security.mail.ses-secret-key:}")
    private String secretKey;

    @Value("${security.mail.region:ap-guangzhou}")
    private String region;

    @Value("${security.mail.from:}")
    private String from;

    @Override
    public void send(String to, String subject, String content) {
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()
                || from == null || from.isBlank()) {
            throw new IllegalStateException("SES 未配置（MAIL_SES_SECRET_ID / MAIL_SES_SECRET_KEY / MAIL_FROM），无法发送安全邮件");
        }

        try {
            Map<String, Object> simple = new LinkedHashMap<>();
            simple.put("Text", content);
            Map<String, Object> emailContent = new LinkedHashMap<>();
            emailContent.put("Simple", simple);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("FromEmailAddress", from);
            payload.put("Destination", List.of(to));
            payload.put("Subject", subject);
            payload.put("Content", emailContent);
            String body = objectMapper.writeValueAsString(payload);

            long timestamp = Instant.now().getEpochSecond();
            String date = DATE_FMT.format(Instant.ofEpochMilli(timestamp * 1000));
            String authorization = buildAuthorization(body, timestamp, date);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Host", HOST)
                    .header("X-TC-Action", ACTION)
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .header("X-TC-Version", VERSION)
                    .header("X-TC-Region", region)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            if (response.statusCode() != 200 || (responseBody != null && responseBody.contains("\"Error\""))) {
                throw new IllegalStateException("SES 发信失败: status=" + response.statusCode()
                        + ", response=" + responseBody);
            }
            log.info("安全邮件已通过 SES 发送: to={}, subject={}", to, subject);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("SES 发信异常: to={}", to, e);
            throw new IllegalStateException("SES 发信异常: " + e.getMessage(), e);
        }
    }

    private String buildAuthorization(String payload, long timestamp, String date) throws Exception {
        String contentType = "application/json; charset=utf-8";
        String canonicalHeaders = "content-type:" + contentType + "\n" + "host:" + HOST + "\n";
        String signedHeaders = "content-type;host";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders
                + "\n" + sha256Hex(payload);

        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = hex(hmacSha256(secretSigning, stringToSign));

        return ALGORITHM + " Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    private static byte[] hmacSha256(byte[] key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
