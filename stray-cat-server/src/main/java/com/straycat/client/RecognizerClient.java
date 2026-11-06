package com.straycat.client;

import com.straycat.config.RecognizerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 花纹识别服务客户端
 * 调用 Python FastAPI 服务进行特征提取、匹配、注册
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecognizerClient {

    private final RecognizerProperties properties;
    private final RestTemplate restTemplate;

    /**
     * 提取照片特征
     *
     * @param file 照片文件
     * @return 特征 ID，失败返回 null
     */
    public Long extractFeature(MultipartFile file) {
        try {
            String url = properties.getBaseUrl() + "/api/recognize/extract";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                Boolean success = (Boolean) result.get("success");
                if (Boolean.TRUE.equals(success)) {
                    return ((Number) result.get("feature_id")).longValue();
                } else {
                    log.warn("特征提取失败：{}", result.get("message"));
                }
            }
        } catch (Exception e) {
            log.error("调用识别服务失败：{}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 匹配猫咪
     *
     * @param file 照片文件
     * @param topK 返回前 K 个结果
     * @return 匹配结果列表
     */
    public List<MatchResult> matchCat(MultipartFile file, int topK) {
        try {
            String url = properties.getBaseUrl() + "/api/recognize/match";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("top_k", String.valueOf(topK));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");

                if (matches != null) {
                    return matches.stream().map(m -> new MatchResult(
                            ((Number) m.get("cat_id")).longValue(),
                            ((Number) m.get("similarity")).doubleValue(),
                            (String) m.get("image_url")
                    )).toList();
                }
            }
        } catch (Exception e) {
            log.error("匹配猫咪失败：{}", e.getMessage(), e);
        }
        return List.of();
    }

    /**
     * 注册新猫
     *
     * @param file  照片文件
     * @param catId 猫咪 ID
     * @return 特征 ID，失败返回 null
     */
    public Long registerCat(MultipartFile file, Long catId) {
        try {
            String url = properties.getBaseUrl() + "/api/recognize/register";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("cat_id", String.valueOf(catId));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                Boolean success = (Boolean) result.get("success");
                if (Boolean.TRUE.equals(success)) {
                    return ((Number) result.get("feature_id")).longValue();
                } else {
                    log.warn("注册猫咪失败：{}", result.get("message"));
                }
            }
        } catch (Exception e) {
            log.error("注册猫咪失败：{}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 匹配结果
     */
    public record MatchResult(Long catId, Double similarity, String imageUrl) {
    }
}
