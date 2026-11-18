package com.straycat.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.common.HomeCache;
import com.straycat.config.QWeatherProperties;
import com.straycat.service.QWeatherGeoService;
import com.straycat.service.WeatherService;
import com.straycat.util.GzipAwareHttp;
import com.straycat.vo.WeatherVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页天气代理（和风天气 QWeather 新版 v1 API）。
 *
 * 设计要点：
 * - 数据源：和风天气开发者独立 Host + API Key（X-QW-Api-Key 请求头），key 只在后端；
 * - adcode → 经纬度：交给 QWeatherGeoService（GeoAPI 反查 + 7 天缓存 + 降级链）；
 * - 按 adcode 缓存到 HomeCache（Redis 静默降级），TTL 1800s，是真正的取数节流；
 * - 失败不写缓存（避免偶发失败锁 30 分钟），返回各字段为空的 VO，前端据此隐藏卡片；
 * - 城市名用本地 region 字典按 adcode 上溯拼接，不依赖和风返回；
 * - 对外契约 WeatherVO 与腾讯时期完全一致，前端零改动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    private static final String DEFAULT_ADCODE = "420100";

    private static final String CACHE_PREFIX = "weather:qw:v1:";
    private static final long CACHE_TTL = 1800L;

    /** 和风风向方位码 → 中文（前端展示用） */
    private static final java.util.Map<String, String> COMPASS_CN = java.util.Map.ofEntries(
            java.util.Map.entry("n", "北风"), java.util.Map.entry("nne", "东北偏北风"),
            java.util.Map.entry("ne", "东北风"), java.util.Map.entry("ene", "东北偏东风"),
            java.util.Map.entry("e", "东风"), java.util.Map.entry("ese", "东南偏东风"),
            java.util.Map.entry("se", "东南风"), java.util.Map.entry("sse", "东南偏南风"),
            java.util.Map.entry("s", "南风"), java.util.Map.entry("ssw", "西南偏南风"),
            java.util.Map.entry("sw", "西南风"), java.util.Map.entry("wsw", "西南偏西风"),
            java.util.Map.entry("w", "西风"), java.util.Map.entry("wnw", "西北偏西风"),
            java.util.Map.entry("nw", "西北风"), java.util.Map.entry("nnw", "西北偏北风")
    );

    private final QWeatherProperties properties;
    private final HomeCache homeCache;
    private final QWeatherGeoService geoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WeatherVO getWeather(String adcode) {
        String code = normalize(adcode);
        String cacheKey = CACHE_PREFIX + code;

        WeatherVO cached = homeCache.get(cacheKey, WeatherVO.class);
        if (cached != null) {
            return cached;
        }

        WeatherVO vo;
        if (!properties.ready()) {
            log.warn("天气代理：和风 host/key 未配置，返回空天气");
            vo = emptyVo(code);
        } else {
            QWeatherGeoService.LatLon loc = geoService.resolve(code);
            if (loc == null || !loc.valid()) {
                log.warn("天气代理：adcode={} 反查经纬度失败，返回空天气", code);
                vo = emptyVo(code);
            } else {
                vo = callQWeather(code, loc);
            }
        }

        // 只有解析出实况数据才写缓存，失败不缓存
        if (vo != null && vo.getNow() != null) {
            homeCache.set(cacheKey, vo, CACHE_TTL);
        }
        return vo == null ? emptyVo(code) : vo;
    }

    /** 校验并归一化 adcode：必须是 6 位数字，否则回默认武汉 */
    private String normalize(String adcode) {
        if (adcode != null && adcode.matches("\\d{6}")) {
            return adcode;
        }
        return DEFAULT_ADCODE;
    }

    /**
     * 调和风新版 v1：实况 current + 3 天预报 daily，两个请求任一失败即降级空 VO。
     */
    private WeatherVO callQWeather(String adcode, QWeatherGeoService.LatLon loc) {
        String currentUrl = properties.baseUrl() + "/weather/v1/current/" + loc.getLat() + "/" + loc.getLon()
                + "?localTime=true";
        String dailyUrl = properties.baseUrl() + "/weather/v1/daily/" + loc.getLat() + "/" + loc.getLon()
                + "?days=3&localTime=true";

        JsonNode current = fetch(currentUrl);
        if (current == null || current.has("error")) {
            log.warn("和风实况查询失败: adcode={}, resp={}", adcode,
                    current == null ? "null" : current.toString());
            return emptyVo(adcode);
        }
        JsonNode daily = fetch(dailyUrl);
        if (daily == null || daily.has("error")) {
            log.warn("和风预报查询失败: adcode={}, resp={}", adcode,
                    daily == null ? "null" : daily.toString());
            return emptyVo(adcode);
        }

        WeatherVO vo = parse(current, daily);
        vo.setCityName(geoService.resolveCityName(adcode));
        return vo;
    }

    /** 发起 GET 请求（自动解压 gzip），带 API Key 请求头；失败返回 null */
    private JsonNode fetch(String url) {
        try {
            String body = GzipAwareHttp.get(url, properties.getKey(),
                    properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
            if (body == null) {
                log.warn("和风天气 HTTP 非200: url={}", url);
                return null;
            }
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("和风天气请求异常: url={}, err={}", url, e.getMessage());
            return null;
        }
    }

    /** 解析和风 v1 响应为 WeatherVO（保留多口径容错） */
    private WeatherVO parse(JsonNode current, JsonNode daily) {
        WeatherVO vo = new WeatherVO();

        // ---- 实况 ----
        WeatherVO.Now now = new WeatherVO.Now();
        JsonNode conditionNode = firstPresent(current, "condition");
        now.setWeather(textOf(conditionNode, "text"));
        now.setTemperature(roundInt(numberValue(firstPresent(current, "temperature", "temp"))));
        JsonNode humidityNode = current.get("humidity");
        if (humidityNode != null && humidityNode.isNumber()) {
            double h = humidityNode.asDouble();
            // 兼容 0~1 与 0~100 两种口径
            now.setHumidity((int) Math.round(h <= 1.0 ? h * 100 : h));
        }
        JsonNode wind = firstPresent(current, "wind");
        if (wind != null) {
            JsonNode dir = wind.get("direction");
            if (dir != null) {
                String compass = textOf(dir, "compass");
                now.setWindDirection(compassCn(compass));
            }
            JsonNode scale = wind.get("scale");
            if (scale != null && !scale.isNull()) {
                String s = scale.isNumber() ? scale.asText() : scale.asText("");
                now.setWindPower(s.isEmpty() ? null : s + "级");
            }
        }
        if (now.getWeather() != null || now.getTemperature() != null) {
            vo.setNow(now);
        }

        // ---- 未来预报 ----
        JsonNode days = firstPresent(daily, "days", "daily");
        if (days != null && days.isArray()) {
            List<WeatherVO.FutureDay> list = new ArrayList<>();
            for (JsonNode d : days) {
                if (list.size() >= 3) {
                    break;
                }
                WeatherVO.FutureDay fd = new WeatherVO.FutureDay();
                JsonNode dateNode = firstPresent(d, "forecastStartTime", "date", "date_str");
                String start = dateNode == null || dateNode.isNull() ? null : dateNode.asText(null);
                fd.setDate(start == null || start.isEmpty() ? null
                        : (start.length() >= 10 ? start.substring(0, 10) : start));
                JsonNode daytime = firstPresent(d, "daytime");
                if (daytime != null) {
                    JsonNode cond = firstPresent(daytime, "condition");
                    if (cond != null) {
                        fd.setWeather(textOf(cond, "text"));
                    }
                }
                JsonNode maxNode = firstPresent(d, "temperatureMax", "tempMax");
                JsonNode minNode = firstPresent(d, "temperatureMin", "tempMin");
                fd.setHigh(roundInt(numberValue(maxNode)));
                fd.setLow(roundInt(numberValue(minNode)));
                list.add(fd);
            }
            vo.setFuture(list);
        }

        // 观测时间优先用实况接口的 validTime（如 2026-11-18T06:45+08:00），取本地时间部分
        String validTime = textOf(current, "validTime");
        if (validTime != null && validTime.length() >= 16) {
            // 截取「MM-dd HH:mm」用于展示，保留 +08:00 时区偏移信息时取前16位
            vo.setUpdateTime(validTime.substring(5, 16));
        } else {
            vo.setUpdateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        }
        return vo;
    }

    /** 兼容两种形态：数值节点直接取 value；或 {value: x} 对象 */
    private JsonNode numberValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return node.get("value");
        }
        return node;
    }

    /** 数值节点 → Integer（四舍五入，兼容小数） */
    private Integer roundInt(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return (int) Math.round(node.asDouble());
        }
        try {
            String s = node.asText("").replaceAll("[^0-9.\\-]", "");
            if (s.isEmpty() || s.equals("-") || s.equals(".")) {
                return null;
            }
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String compassCn(String compass) {
        if (compass == null) {
            return null;
        }
        return COMPASS_CN.getOrDefault(compass.toLowerCase(), null);
    }

    private WeatherVO emptyVo(String adcode) {
        WeatherVO vo = new WeatherVO();
        vo.setCityName(geoService.resolveCityName(adcode));
        return vo;
    }

    private JsonNode firstPresent(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull()) {
                return v;
            }
        }
        return null;
    }

    /** 按字段路径取文本：支持 "condition/text" 点号路径 */
    private String textOf(JsonNode node, String path) {
        if (node == null) {
            return null;
        }
        JsonNode cur = node;
        for (String part : path.split("\\.")) {
            cur = cur == null ? null : cur.get(part);
        }
        if (cur == null || cur.isNull()) {
            return null;
        }
        String s = cur.asText("");
        return s.isEmpty() ? null : s;
    }
}
