package com.straycat.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.straycat.entity.CatProfile;
import com.straycat.mapper.CatProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatGeoInitializer implements ApplicationRunner {

    private final CatProfileMapper catProfileMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CAT_GEO_KEY = "cat:geo";

    @Override
    public void run(ApplicationArguments args) {
        LambdaQueryWrapper<CatProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(CatProfile::getLatitude).isNotNull(CatProfile::getLongitude);
        var cats = catProfileMapper.selectList(wrapper);

        for (CatProfile cat : cats) {
            try {
                redisTemplate.opsForGeo().add(CAT_GEO_KEY,
                        new Point(cat.getLongitude().doubleValue(), cat.getLatitude().doubleValue()),
                        cat.getId().toString());
            } catch (Exception e) {
                log.warn("猫咪 {} GEO 初始化失败: {}", cat.getId(), e.getMessage());
            }
        }

        log.info("Redis GEO 初始化完成，共 {} 只猫咪有坐标数据", cats.size());
    }
}
