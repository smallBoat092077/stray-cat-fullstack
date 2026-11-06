package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatLike;
import com.straycat.entity.CatProfile;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.CatLikeMapper;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.service.CatProfileService;
import com.straycat.service.CoinService;
import com.straycat.service.ContentSecurityService;
import com.straycat.util.CatLifeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CatProfileServiceImpl extends ServiceImpl<CatProfileMapper, CatProfile> implements CatProfileService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private CoinService coinService;

    @Resource
    private ContentSecurityService contentSecurityService;

    @Resource
    private CatLikeMapper catLikeMapper;

    private static final String CAT_GEO_KEY = "cat:geo";

    /** 人气榜单接口一次最多返回多少只，防止前端传个大 limit 当列表刷 */
    private static final int POPULAR_LIMIT_MAX = 10;

    @Override
    public List<CatProfile> getNearbyCats(double latitude, double longitude, double radius) {
        Circle circle = new Circle(new Point(longitude, latitude), new Distance(radius, Metrics.KILOMETERS));
        var results = redisTemplate.opsForGeo().radius(CAT_GEO_KEY, circle);
        
        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }

        List<Long> catIds = results.getContent().stream()
                .map(r -> Long.parseLong(String.valueOf(r.getContent().getName())))
                .collect(Collectors.toList());

        return listByIds(catIds);
    }

    @Override
    public List<CatProfile> getPopularCats(int limit) {
        int size = Math.min(Math.max(limit, 1), POPULAR_LIMIT_MAX);
        LambdaQueryWrapper<CatProfile> wrapper = new LambdaQueryWrapper<CatProfile>()
                // 已领养/回访中的猫已经不在野外，占人气位会让人白跑一趟
                .in(CatProfile::getLifeStatus,
                        CatLifeStatus.STRAYING, CatLifeStatus.FEEDING, CatLifeStatus.WAITING_ADOPTION)
                .orderByDesc(CatProfile::getLikeCount)
                .orderByDesc(CatProfile::getViewCount)
                .orderByAsc(CatProfile::getId)
                .last("LIMIT " + size);
        return list(wrapper);
    }

    @Override
    public Long reportCat(CatProfile catProfile) {
        contentSecurityService.check(catProfile.getDiscovererId(), ContentSecurityService.SCENE_PROFILE,
                catProfile.getName(), catProfile.getAgeEstimate(), catProfile.getBreed(),
                catProfile.getColor(), catProfile.getPersonality(),
                catProfile.getLocationDesc(), catProfile.getDescription());

        save(catProfile);

        // 添加到Redis GEO
        if (catProfile.getLatitude() != null && catProfile.getLongitude() != null) {
            redisTemplate.opsForGeo().add(CAT_GEO_KEY,
                    new Point(catProfile.getLongitude().doubleValue(), catProfile.getLatitude().doubleValue()),
                    catProfile.getId().toString());
        }

        coinService.grant(catProfile.getDiscovererId(), CoinService.REPORT_REWARD,
                CoinService.BIZ_REPORT, catProfile.getId(), "上报流浪猫");
        return catProfile.getId();
    }

    @Override
    public void updateLifeStatus(Long catId, Integer lifeStatus) {
        CatProfile cat = getById(catId);
        if (cat == null) {
            throw new BusinessException(ResultCode.CAT_NOT_FOUND);
        }
        if (lifeStatus == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "缺少生活状态");
        }
        if (!CatLifeStatus.canMove(cat.getLifeStatus(), lifeStatus)) {
            throw new BusinessException("不允许把猫咪状态从「" + CatLifeStatus.text(cat.getLifeStatus())
                    + "」改为「" + CatLifeStatus.text(lifeStatus) + "」");
        }
        cat.setLifeStatus(lifeStatus);
        cat.setUpdatedAt(LocalDateTime.now());
        updateById(cat);
    }

    @Override
    public CatProfile getDetail(Long catId, Long userId) {
        CatProfile cat = getById(catId);
        if (cat == null) {
            return null;
        }
        baseMapper.addViewCount(catId);
        cat.setViewCount((cat.getViewCount() == null ? 0 : cat.getViewCount()) + 1);
        cat.setLiked(userId != null && catLikeMapper.selectCount(new LambdaQueryWrapper<CatLike>()
                .eq(CatLike::getCatId, catId)
                .eq(CatLike::getUserId, userId)) > 0);
        return cat;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long catId, Long userId) {
        CatProfile cat = getById(catId);
        if (cat == null) {
            throw new BusinessException(ResultCode.CAT_NOT_FOUND);
        }
        LambdaQueryWrapper<CatLike> wrapper = new LambdaQueryWrapper<CatLike>()
                .eq(CatLike::getCatId, catId)
                .eq(CatLike::getUserId, userId);
        CatLike existing = catLikeMapper.selectOne(wrapper);
        if (existing != null) {
            catLikeMapper.deleteById(existing.getId());
            baseMapper.addLikeCount(catId, -1);
            return false;
        }
        CatLike like = new CatLike();
        like.setCatId(catId);
        like.setUserId(userId);
        like.setCreatedAt(LocalDateTime.now());
        catLikeMapper.insert(like);
        baseMapper.addLikeCount(catId, 1);
        return true;
    }
}
