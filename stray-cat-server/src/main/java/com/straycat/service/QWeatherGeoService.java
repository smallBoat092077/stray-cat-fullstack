package com.straycat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.common.HomeCache;
import com.straycat.config.QWeatherProperties;
import com.straycat.entity.Region;
import com.straycat.mapper.RegionMapper;
import com.straycat.util.GzipAwareHttp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 和风天气地名服务：adcode → 经纬度。
 *
 * 新版和风天气接口（/weather/v1/current、/daily）只认经纬度，不认国标 adcode，
 * 因此需要先把用户在前端选的行政区码转成经纬度：
 *   adcode → 本地 region 字典拼名称 → 和风 GeoAPI lookup → latitude/longitude
 *
 * - 结果按 adcode 缓存 7 天（名称→坐标极少变化），缓存命中不再调 GeoAPI；
 * - 区级名查不到时回退市级名，再查不到返回 null（上层隐藏卡片）；
 * - 复用 HomeCache（Redis 静默降级），任何异常都返回 null，绝不影响首页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QWeatherGeoService {

    /** 无定位/无效 adcode 时的默认城市：武汉 */
    private static final String DEFAULT_ADCODE = "420100";

    private static final String CACHE_PREFIX = "weather:geo:qw:v1:";
    /** 经纬度映射稳定，缓存 7 天 */
    private static final long CACHE_TTL = 7L * 24 * 3600;

    private final QWeatherProperties properties;
    private final HomeCache homeCache;
    private final RegionMapper regionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 经纬度结果（保留无参构造与 setter，供 Jackson 从缓存反序列化） */
    public static class LatLon {
        private String lat;
        private String lon;

        public LatLon() {
        }

        public LatLon(String lat, String lon) {
            this.lat = lat;
            this.lon = lon;
        }

        public String getLat() {
            return lat;
        }

        public void setLat(String lat) {
            this.lat = lat;
        }

        public String getLon() {
            return lon;
        }

        public void setLon(String lon) {
            this.lon = lon;
        }

        public boolean valid() {
            return lat != null && lon != null;
        }
    }

    /**
     * adcode → 经纬度，带 7 天缓存与降级链。查不到返回 null。
     */
    public LatLon resolve(String adcode) {
        String code = normalize(adcode);
        String cacheKey = CACHE_PREFIX + code;

        LatLon cached = homeCache.get(cacheKey, LatLon.class);
        if (cached != null && cached.valid()) {
            return cached;
        }
        if (!properties.ready()) {
            return null;
        }

        List<String> candidates = nameCandidates(code);
        LatLon result = null;
        for (String name : candidates) {
            if (name == null || name.isBlank()) {
                continue;
            }
            result = lookup(name);
            if (result != null) {
                break;
            }
        }

        if (result != null) {
            homeCache.set(cacheKey, result, CACHE_TTL);
        } else {
            log.warn("和风 GeoAPI 反查经纬度失败: adcode={}, 候选名={}", code, candidates);
        }
        return result;
    }

    /** 按 adcode 上溯 region 字典拼「省市区」名称，供展示与 GeoAPI 查询 */
    public String resolveCityName(String adcode) {
        String code = normalize(adcode);
        try {
            List<String> names = new ArrayList<>();
            String cur = code;
            for (int i = 0; i < 3 && cur != null; i++) {
                Region r = regionMapper.selectById(cur);
                if (r == null || r.getName() == null) {
                    break;
                }
                names.add(0, r.getName());
                cur = r.getParentCode();
                if (cur == null || (cur.equals(code) && i >= 1)) {
                    break;
                }
            }
            return String.join("", names);
        } catch (Exception e) {
            log.warn("区域名解析失败: adcode={}, err={}", code, e.getMessage());
            return null;
        }
    }

    /** 生成查询候选名：优先「市+区」，再「市」，再完整省市区 */
    private List<String> nameCandidates(String code) {
        List<String> out = new ArrayList<>();
        try {
            Region region = regionMapper.selectById(code);
            if (region == null) {
                return out;
            }
            String full = resolveCityName(code);
            if (region.getLevel() != null && region.getLevel() == 3) {
                // 区级：父级为市（或直辖市的省）
                Region parent = region.getParentCode() == null ? null : regionMapper.selectById(region.getParentCode());
                String cityName = parent == null ? null : cleanName(parent.getName());
                String districtName = cleanName(region.getName());
                if (cityName != null && districtName != null) {
                    out.add(cityName + districtName);
                }
                if (cityName != null) {
                    out.add(cityName);
                }
            }
            if (full != null && !out.contains(full)) {
                out.add(full);
            }
            String self = cleanName(region.getName());
            if (self != null && !out.contains(self)) {
                out.add(self);
            }
        } catch (Exception e) {
            log.warn("构造区域候选名失败: adcode={}, err={}", code, e.getMessage());
        }
        return out;
    }

    /** 去掉行政后缀，和风 GeoAPI 对「武汉市洪山区」这类更稳 */
    private String cleanName(String name) {
        if (name == null) {
            return null;
        }
        return name.replaceAll("(省|市|自治区|区|县|自治州|盟|地区)$", "");
    }

    /** 调和风 GeoAPI：location -> 经纬度（自动解压 gzip） */
    private LatLon lookup(String name) {
        String url = properties.baseUrl() + "/geo/v2/city/lookup?location="
                + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&number=1";
        try {
            String bodyText = GzipAwareHttp.get(url, properties.getKey(),
                    properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
            if (bodyText == null) {
                log.warn("GeoAPI 查询 HTTP 非200: name={}", name);
                return null;
            }
            JsonNode body = objectMapper.readTree(bodyText);
            if (body.has("error") || !"200".equals(body.path("code").asText())) {
                log.warn("GeoAPI 查询失败: name={}, code={}, resp={}",
                        name, body.path("code").asText(), bodyText);
                return null;
            }
            JsonNode first = body.path("location").path(0);
            String lat = text(first, "lat");
            String lon = text(first, "lon");
            return new LatLon(lat, lon);
        } catch (Exception e) {
            log.warn("GeoAPI 查询异常: name={}, err={}", name, e.getMessage());
            return null;
        }
    }

    private String normalize(String adcode) {
        if (adcode != null && adcode.matches("\\d{6}")) {
            return adcode;
        }
        return DEFAULT_ADCODE;
    }

    private String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText("");
        return s.isEmpty() ? null : s;
    }
}
