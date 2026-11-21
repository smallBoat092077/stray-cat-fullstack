package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.config.TencentMapProperties;
import com.straycat.entity.Region;
import com.straycat.mapper.RegionMapper;
import com.straycat.service.RegionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 行政区域字典实现（区域划分需求 R1/R2，功能设计 §1.3 / §2）。
 *
 * 同步口径（district/v1/list）：腾讯返回 provinces / cities / districts 三个平铺数组，
 * id 即国标行政区码。父子关系按国标前缀推导：
 *   市 -> 省（前 2 位 + 0000）；区 -> 市（前 4 位 + 00），市级行不存在时下沉到省（直辖市）。
 * 直辖市省/市同码（110000），INSERT IGNORE 保证不重复写入，树构建时省级下无市级子节点
 * 则直接返回区级（市码 = 省码）。
 *
 * 码解析（resolveCodes）一律以本字典为准按「名称 + 层级 + 父级」查码，
 * 不猜测腾讯 geocoder 的 *_code 字段格式，保证与业务数据存的码同源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private static final String DISTRICT_LIST_URL =
            "https://apis.map.qq.com/ws/district/v1/list?key=%s";

    /** 四大直辖市省码：cities 数组里的行即区级行（腾讯数据形状，UAT 实测） */
    private static final java.util.Set<String> MUNICIPALITY_CODES =
            java.util.Set.of("110000", "120000", "310000", "500000");

    private final RegionMapper regionMapper;
    private final TencentMapProperties mapProps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 区域树内存缓存（10 分钟），同步后由 syncFromTencent 失效 */
    private volatile List<Map<String, Object>> treeCache;
    private volatile long treeCacheAt = 0;

    /** 码 -> 名称全量字典缓存（10 分钟），namesOf 批量翻译用 */
    private volatile Map<String, String> nameCache;
    private volatile long nameCacheAt = 0;

    @Override
    public Map<String, String> namesOf(java.util.Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> dict = loadNameDict();
        Map<String, String> out = new HashMap<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            String name = dict.get(code.trim());
            if (name != null) {
                out.put(code.trim(), name);
            }
        }
        return out;
    }

    private Map<String, String> loadNameDict() {
        long now = System.currentTimeMillis();
        Map<String, String> cached = nameCache;
        if (cached != null && now - nameCacheAt < 10 * 60 * 1000L) {
            return cached;
        }
        Map<String, String> dict = new HashMap<>();
        for (Region r : regionMapper.selectList(null)) {
            dict.put(r.getCode(), r.getName());
        }
        nameCache = dict;
        nameCacheAt = now;
        return dict;
    }

    @Override
    public List<Map<String, Object>> tree() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> cached = treeCache;
        if (cached != null && now - treeCacheAt < 10 * 60 * 1000L) {
            return cached;
        }
        Map<String, List<Region>> byParent = new HashMap<>();
        for (Region r : regionMapper.selectList(new LambdaQueryWrapper<Region>().orderByAsc(Region::getSort))) {
            byParent.computeIfAbsent(r.getParentCode() == null ? "" : r.getParentCode(), k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Region p : byParent.getOrDefault("", List.of())) {
            Map<String, Object> node = nodeOf(p);
            List<Map<String, Object>> cities = new ArrayList<>();
            for (Region c : byParent.getOrDefault(p.getCode(), List.of())) {
                if (c.getLevel() != 2) {
                    continue;
                }
                Map<String, Object> cityNode = nodeOf(c);
                List<Map<String, Object>> districts = new ArrayList<>();
                for (Region d : byParent.getOrDefault(c.getCode(), List.of())) {
                    districts.add(nodeOf(d));
                }
                cityNode.put("children", districts);
                cities.add(cityNode);
            }
            // 直辖市：省级下直接挂区级
            for (Region d : byParent.getOrDefault(p.getCode(), List.of())) {
                if (d.getLevel() == 3) {
                    cities.add(nodeOf(d));
                }
            }
            node.put("children", cities);
            tree.add(node);
        }
        treeCache = tree;
        treeCacheAt = now;
        return tree;
    }

    private Map<String, Object> nodeOf(Region r) {
        Map<String, Object> node = new HashMap<>();
        node.put("code", r.getCode());
        node.put("name", r.getName());
        node.put("level", r.getLevel());
        return node;
    }

    @Override
    public List<Region> childrenOf(String parentCode) {
        if (parentCode == null || parentCode.isBlank()) {
            return listByLevel(1);
        }
        List<Region> children = listByParent(parentCode);
        if (!children.isEmpty()) {
            return children;
        }
        // 直辖市：省级下没有市级子节点，下沉返回其区级列表
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .eq(Region::getParentCode, parentCode)
                .eq(Region::getLevel, 3)
                .orderByAsc(Region::getSort));
    }

    private List<Region> listByParent(String parentCode) {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .eq(Region::getParentCode, parentCode)
                .orderByAsc(Region::getSort));
    }

    private List<Region> listByLevel(int level) {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .eq(Region::getLevel, level)
                .orderByAsc(Region::getSort));
    }

    @Override
    public String[] resolveCodes(String provinceName, String cityName, String districtName) {
        String[] out = new String[3];
        if (provinceName == null || provinceName.isBlank()) {
            return out;
        }
        Region province = findByName(1, provinceName, null);
        if (province == null) {
            return out;
        }
        out[0] = province.getCode();

        // 直辖市：市名与省名相同，市码直接取省码
        if (cityName != null && !cityName.isBlank() && !cityName.equals(provinceName)) {
            Region city = findByName(2, cityName, province.getCode());
            if (city != null) {
                out[1] = city.getCode();
            }
        } else if (cityName != null && cityName.equals(provinceName)) {
            out[1] = province.getCode();
        }

        if (districtName != null && !districtName.isBlank()) {
            String parent = out[1] != null ? out[1] : province.getCode();
            Region district = findByName(3, districtName, parent);
            if (district == null && out[1] != null) {
                // 区可能直接挂在省下（直辖市同步口径）
                district = findByName(3, districtName, province.getCode());
            }
            if (district != null) {
                out[2] = district.getCode();
            }
        }
        return out;
    }

    private Region findByName(int level, String name, String parentCode) {
        LambdaQueryWrapper<Region> wrapper = new LambdaQueryWrapper<Region>()
                .eq(Region::getLevel, level)
                .eq(Region::getName, name);
        if (parentCode != null) {
            wrapper.eq(Region::getParentCode, parentCode);
        }
        return regionMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    @Override
    public Map<String, Integer> syncFromTencent() {
        if (!mapProps.ready()) {
            throw new IllegalStateException("未配置腾讯位置服务 key（tencent.map.key / TENCENT_MAP_KEY）");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(DISTRICT_LIST_URL, mapProps.getKey())))
                .timeout(Duration.ofMillis(30000))
                .GET()
                .build();
        JsonNode result;
        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(resp.body());
            if (body.path("status").asInt(-1) != 0) {
                throw new IllegalStateException("腾讯 district 接口返回异常: " + body.path("message").asText());
            }
            result = body.path("result");
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("调用腾讯 district 接口失败: " + e.getMessage());
        }

        JsonNode provinces = result.path(0);
        JsonNode cities = result.path(1);
        JsonNode districts = result.path(2);

        // 省 -> code 索引，用于推导市/区的父级
        Map<String, Region> provinceByCode = new HashMap<>();
        Map<String, Region> cityByCode = new HashMap<>();
        List<Region> rows = new ArrayList<>();

        for (int i = 0; i < provinces.size(); i++) {
            JsonNode p = provinces.get(i);
            Region r = new Region();
            r.setCode(p.path("id").asText());
            // 统一存全称（湖北省/武汉市/洪山区）：geocoder 的 ad_info 返回全称，
            // district 的 name 不带后缀（湖北/武汉），resolveCodes 按名称查码必须同口径
            r.setName(p.path("fullname").asText(p.path("name").asText()));
            r.setParentCode(null);
            r.setLevel(1);
            r.setSort(i);
            rows.add(r);
            provinceByCode.put(r.getCode(), r);
        }
        for (int i = 0; i < cities.size(); i++) {
            JsonNode c = cities.get(i);
            String code = c.path("id").asText();
            if (provinceByCode.containsKey(code)) {
                // 直辖市：市级行与省级行同码，跳过（树构建时省级直接挂区级）
                continue;
            }
            Region r = new Region();
            r.setCode(code);
            r.setName(c.path("fullname").asText(c.path("name").asText()));
            String provincePrefix = code.substring(0, 2) + "0000";
            r.setParentCode(provincePrefix);
            // 直辖市的「市」行在腾讯数据里就是区（id=110101 等）：直接标记为区级，
            // 否则与 districts 数组同码 INSERT IGNORE 会把区级行吞掉，直辖市区县查不到码
            if (MUNICIPALITY_CODES.contains(provincePrefix)) {
                r.setLevel(3);
            } else {
                r.setLevel(2);
            }
            r.setSort(i);
            rows.add(r);
            if (r.getLevel() == 2) {
                cityByCode.put(code, r);
            }
        }
        for (int i = 0; i < districts.size(); i++) {
            JsonNode d = districts.get(i);
            String code = d.path("id").asText();
            Region r = new Region();
            r.setCode(code);
            r.setName(d.path("fullname").asText(d.path("name").asText()));
            String cityParent = code.substring(0, 4) + "00";
            if (cityByCode.containsKey(cityParent)) {
                r.setParentCode(cityParent);
            } else {
                // 直辖市的区：父级直接挂省
                r.setParentCode(code.substring(0, 2) + "0000");
                r.setLevel(3);
                r.setSort(i);
                rows.add(r);
                continue;
            }
            r.setLevel(3);
            r.setSort(i);
            rows.add(r);
        }

        int inserted = 0;
        for (int i = 0; i < rows.size(); i += 500) {
            List<Region> batch = rows.subList(i, Math.min(i + 500, rows.size()));
            inserted += regionMapper.insertIgnoreBatch(batch);
        }
        treeCache = null; // 同步后失效树缓存
        nameCache = null; // 同步后失效名称字典缓存
        log.info("区域同步完成: fetched={}, inserted={}", rows.size(), inserted);
        Map<String, Integer> out = new HashMap<>();
        out.put("fetched", rows.size());
        out.put("inserted", inserted);
        return out;
    }

    @Override
    public long count() {
        return regionMapper.selectCount(null);
    }
}
