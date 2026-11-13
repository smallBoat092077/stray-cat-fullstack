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
import java.util.Arrays;
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

    /** 上报时的图片与描述上限，与小程序端 picker 的 count 保持一致 */
    private static final int CAT_IMAGE_MAX = 3;

    private static final int ENV_IMAGE_MAX = 6;

    private static final int ENV_DESC_MIN = 10;

    private static final int ENV_DESC_MAX = 200;

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
        catProfile.setImages(normalizeImages(catProfile.getImages(), CAT_IMAGE_MAX, "猫咪照片"));
        catProfile.setEnvironmentImages(normalizeImages(catProfile.getEnvironmentImages(), ENV_IMAGE_MAX, "周边环境照片"));
        if (catProfile.getEnvironmentImages() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请至少上传一张周边环境照片");
        }
        String envDesc = catProfile.getEnvironmentDesc() == null ? null : catProfile.getEnvironmentDesc().trim();
        if (envDesc == null || envDesc.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请填写周边环境描述");
        }
        if (envDesc.length() < ENV_DESC_MIN) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "周边环境描述太短了，至少 " + ENV_DESC_MIN + " 个字");
        }
        if (envDesc.length() > ENV_DESC_MAX) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "周边环境描述最多 " + ENV_DESC_MAX + " 个字");
        }
        catProfile.setEnvironmentDesc(envDesc);

        // 只传了一张封面图的老客户端，也补进多图字段，详情轮播不至于空着
        if (catProfile.getImages() == null && catProfile.getCoverImage() != null) {
            catProfile.setImages(catProfile.getCoverImage());
        }
        if (catProfile.getCoverImage() == null && catProfile.getImages() != null) {
            catProfile.setCoverImage(catProfile.getImages().split(",")[0]);
        }

        contentSecurityService.check(catProfile.getDiscovererId(), ContentSecurityService.SCENE_PROFILE,
                catProfile.getName(), catProfile.getAgeEstimate(), catProfile.getBreed(),
                catProfile.getColor(), catProfile.getPersonality(),
                catProfile.getLocationDesc(), catProfile.getDescription(), envDesc);

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

    /**
     * 逗号分隔的图片路径去空、去重、限长；全部为空时返回 null，便于调用方区分"没传"和"传了空串"
     */
    private String normalizeImages(String raw, int max, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> paths = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (paths.isEmpty()) {
            return null;
        }
        if (paths.size() > max) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), label + "最多 " + max + " 张");
        }
        return String.join(",", paths);
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
