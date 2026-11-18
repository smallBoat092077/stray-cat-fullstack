package com.straycat.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.config.TencentMapProperties;
import com.straycat.service.GeoService;
import com.straycat.service.RegionService;
import com.straycat.vo.GeoResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 腾讯位置服务逆地理编码代理（区域划分需求 R3 / D2 / D5）。
 *
 * 设计要点：
 * - 复用路线规划的同一把 key（tencent.map.key / TENCENT_MAP_KEY），key 只在后端不下发小程序；
 * - 缓存 key 为坐标 4 位小数（约 11m 粒度），命中缓存不产生配额消耗；
 *   正向结果 TTL 7 天，负向（解析失败）TTL 1 小时，避免配额被打穿；
 * - 任何失败都返回 success=false 的结构化结果，绝不抛异常阻断上报/列表链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoServiceImpl implements GeoService {

    private static final String GEOCODER_URL =
            "https://apis.map.qq.com/ws/geocoder/v1/?location=%s,%s&key=%s&get_poi=0";

    private static final long POSITIVE_TTL_MS = 7L * 24 * 3600 * 1000;
    private static final long NEGATIVE_TTL_MS = 3600L * 1000;
    private static final int CACHE_MAX = 20000;

    private final TencentMapProperties properties;
    private final RegionService regionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(GeoResultVO value, long expireAt) {}

    @Override
    public GeoResultVO reverse(double latitude, double longitude) {
        if (!properties.ready()) {
            return GeoResultVO.notConfigured();
        }
        String cacheKey = round4(latitude) + "," + round4(longitude);
        CacheEntry hit = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (hit != null && hit.expireAt() > now) {
            return hit.value();
        }
        GeoResultVO result = callTencent(latitude, longitude);
        long ttl = result.isSuccess() ? POSITIVE_TTL_MS : NEGATIVE_TTL_MS;
        if (cache.size() > CACHE_MAX) {
            cache.clear(); // 简单兜底：极端情况下全清，宁可多耗一点配额也不 OOM
        }
        cache.put(cacheKey, new CacheEntry(result, now + ttl));
        return result;
    }

    private GeoResultVO callTencent(double latitude, double longitude) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(GEOCODER_URL, round4(latitude), round4(longitude), properties.getKey())))
                .timeout(Duration.ofMillis(5000))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(resp.body());
            if (body.path("status").asInt(-1) != 0) {
                log.warn("逆地理编码失败: status={}, message={}",
                        body.path("status").asInt(), body.path("message").asText());
                return GeoResultVO.failed();
            }
            JsonNode ad = body.path("result").path("ad_info");
            String provinceName = ad.path("province").asText(null);
            String cityName = ad.path("city").asText(null);
            String districtName = ad.path("district").asText(null);
            String[] codes = regionService.resolveCodes(provinceName, cityName, districtName);

            GeoResultVO vo = new GeoResultVO();
            vo.setGeoConfigured(true);
            // 省码解析不到才算失败；区码缺失只算部分成功，市级过滤仍可用
            vo.setSuccess(codes[0] != null);
            vo.setProvinceName(provinceName);
            vo.setCityName(cityName);
            vo.setDistrictName(districtName);
            // 格式化详细地址（地点名错位修复的存量回填用）：腾讯 geocoder result.address
            String address = body.path("result").path("address").asText(null);
            if (address != null && !address.trim().isEmpty()) {
                vo.setAddress(address.trim());
            }
            vo.setProvinceCode(codes[0]);
            vo.setCityCode(codes[1]);
            vo.setDistrictCode(codes[2]);
            return vo;
        } catch (java.io.IOException e) {
            log.warn("逆地理编码调用异常: {}", e.getMessage());
            return GeoResultVO.failed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return GeoResultVO.failed();
        }
    }

    private String round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
