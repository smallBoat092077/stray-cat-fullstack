package com.straycat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.common.ResultCode;
import com.straycat.config.TencentMapProperties;
import com.straycat.entity.CatProfile;
import com.straycat.exception.BusinessException;
import com.straycat.service.CatProfileService;
import com.straycat.service.GeoRouteService;
import com.straycat.util.GeoDistance;
import com.straycat.vo.GeoRouteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 路线规划代理：小程序只调我们自己的 /geo/route，腾讯的 key 留在服务端。
 * 未配 key、接口报错、返回结构不符，一律退化成两点直线（source=straight），
 * 让前端照常显示距离而不是白屏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoRouteServiceImpl implements GeoRouteService {

    private static final String API_HOST = "https://apis.map.qq.com/ws/direction/v1";
    private static final Set<String> SUPPORTED_MODES = Set.of("walking", "driving", "bicycling");
    /** 直线退化时用来估时间，约 5 km/h */
    private static final double WALK_METERS_PER_SECOND = 1.4;
    /** 长路线的折点可达数千个，抽稀后再下发，免得 setData 过大 */
    private static final int MAX_POLYLINE_POINTS = 300;

    private final TencentMapProperties properties;
    private final CatProfileService catProfileService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = buildRestTemplate();

    private String mode = "walking";

    @PostConstruct
    void resolveMode() {
        String configured = properties.getMode();
        if (configured != null && SUPPORTED_MODES.contains(configured)) {
            mode = configured;
        } else {
            log.warn("tencent.map.mode={} 不受支持，按 walking 处理", configured);
        }
    }

    @Override
    public GeoRouteVO routeToCat(Long catId, Double fromLatitude, Double fromLongitude) {
        CatProfile cat = catProfileService.getById(catId);
        if (cat == null) {
            throw new BusinessException(ResultCode.CAT_NOT_FOUND);
        }
        if (!hasPoint(cat)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "这只猫咪还没有上报坐标");
        }
        if (fromLatitude == null || fromLongitude == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }

        double toLat = cat.getLatitude().doubleValue();
        double toLng = cat.getLongitude().doubleValue();

        if (!properties.ready()) {
            return straight(fromLatitude, fromLongitude, toLat, toLng);
        }
        try {
            GeoRouteVO route = requestTencent(fromLatitude, fromLongitude, toLat, toLng);
            return route != null ? route : straight(fromLatitude, fromLongitude, toLat, toLng);
        } catch (Exception e) {
            log.warn("腾讯路线规划失败，退化为直线: {}", e.getMessage());
            return straight(fromLatitude, fromLongitude, toLat, toLng);
        }
    }

    @SuppressWarnings("unchecked")
    private GeoRouteVO requestTencent(double fromLat, double fromLng, double toLat, double toLng) throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(API_HOST + "/" + mode + "/")
                .queryParam("from", fromLat + "," + fromLng)
                .queryParam("to", toLat + "," + toLng)
                .queryParam("key", properties.getKey())
                .build()
                .toUri();
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
        Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);

        // 出错时只记 status/message，绝不把 URI 打进日志——里面带着 key
        Object statusValue = body.get("status");
        if (!(statusValue instanceof Number status) || status.intValue() != 0) {
            log.warn("腾讯路线规划返回异常 status={}, message={}", statusValue, body.get("message"));
            return null;
        }
        if (!(body.get("result") instanceof Map<?, ?> result)
                || !(result.get("routes") instanceof List<?> routes)
                || routes.isEmpty()
                || !(routes.get(0) instanceof Map<?, ?> first)) {
            log.warn("腾讯路线规划返回结构不符合预期");
            return null;
        }
        if (!(first.get("polyline") instanceof List<?> flat)) {
            log.warn("腾讯路线规划未返回 polyline");
            return null;
        }

        List<GeoRouteVO.Point> points = decodePolyline(flat);
        if (points.size() < 2) {
            return null;
        }

        GeoRouteVO route = new GeoRouteVO();
        route.setSource("tencent");
        route.setMode(mode);
        route.setDistanceMeters(first.get("distance") instanceof Number n ? n.intValue() : null);
        // 腾讯的 duration 单位是分钟，接口对内统一按秒下发
        route.setDurationSeconds(first.get("duration") instanceof Number n ? n.intValue() * 60 : null);
        route.setPolyline(thin(points));
        if (route.getDistanceMeters() == null || route.getDurationSeconds() == null) {
            double meters = GeoDistance.metersBetween(fromLat, fromLng, toLat, toLng);
            if (route.getDistanceMeters() == null) {
                route.setDistanceMeters((int) Math.round(meters));
            }
            if (route.getDurationSeconds() == null) {
                route.setDurationSeconds((int) Math.round(meters / WALK_METERS_PER_SECOND));
            }
        }
        return route;
    }

    /**
     * 腾讯的 polyline 是前向差分压缩过的：第一对是原始经纬度（度），之后每对是相对上一点的
     * 微度整数增量，要除以 1e6 累加还原。直接当成对坐标用会得到 (0,0)、(-25,-47) 这种点。
     */
    private List<GeoRouteVO.Point> decodePolyline(List<?> flat) {
        List<GeoRouteVO.Point> points = new ArrayList<>();
        double lat = 0;
        double lng = 0;
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            if (!(flat.get(i) instanceof Number row) || !(flat.get(i + 1) instanceof Number col)) {
                continue;
            }
            if (points.isEmpty()) {
                lat = row.doubleValue();
                lng = col.doubleValue();
            } else {
                lat += row.doubleValue() / 1_000_000;
                lng += col.doubleValue() / 1_000_000;
            }
            points.add(new GeoRouteVO.Point(round6(lat), round6(lng)));
        }
        return points;
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000) / 1_000_000d;
    }

    /** DECIMAL(10,7) 可以为空，也可能被上报成 0 —— 0,0 在大西洋几内亚湾，不能当坐标用 */
    private boolean hasPoint(CatProfile cat) {
        BigDecimal lat = cat.getLatitude();
        BigDecimal lng = cat.getLongitude();
        return lat != null && lng != null && (lat.signum() != 0 || lng.signum() != 0);
    }

    private GeoRouteVO straight(double fromLat, double fromLng, double toLat, double toLng) {
        double meters = GeoDistance.metersBetween(fromLat, fromLng, toLat, toLng);
        GeoRouteVO route = new GeoRouteVO();
        route.setSource("straight");
        route.setMode(mode);
        route.setDistanceMeters((int) Math.round(meters));
        route.setDurationSeconds((int) Math.round(meters / WALK_METERS_PER_SECOND));
        route.setPolyline(List.of(
                new GeoRouteVO.Point(fromLat, fromLng),
                new GeoRouteVO.Point(toLat, toLng)));
        return route;
    }

    private List<GeoRouteVO.Point> thin(List<GeoRouteVO.Point> points) {
        if (points.size() <= MAX_POLYLINE_POINTS) {
            return points;
        }
        int step = points.size() / MAX_POLYLINE_POINTS;
        List<GeoRouteVO.Point> kept = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i += step) {
            kept.add(points.get(i));
        }
        kept.add(points.get(points.size() - 1));
        return kept;
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }
}
