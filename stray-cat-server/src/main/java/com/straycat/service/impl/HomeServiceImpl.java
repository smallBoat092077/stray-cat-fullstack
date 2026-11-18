package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.HomeCache;
import com.straycat.entity.HomeContent;
import com.straycat.mapper.HomeContentMapper;
import com.straycat.service.HomeLeaderboardService;
import com.straycat.service.HomeService;
import com.straycat.vo.HomeBootstrapVO;
import com.straycat.vo.RankItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private static final String BOOTSTRAP_KEY = HomeCache.BOOTSTRAP_PREFIX + "v1";

    private static final List<String> BOARD_TYPES = List.of("feeding", "food", "active");

    private final HomeContentMapper homeContentMapper;
    private final HomeLeaderboardService leaderboardService;
    private final HomeCache homeCache;

    @Value("${home.bootstrap-cache-seconds:60}")
    private long bootstrapCacheSeconds;

    @Value("${home.hotspot-limit:3}")
    private int hotspotLimit;

    @Value("${home.board-limit:5}")
    private int boardLimit;

    // 模块灰度开关：默认全开，可用外部配置 home.modules.* 单独关闭而不发版（方案 7）
    @Value("${home.modules.hotspot:true}")
    private boolean moduleHotspot;

    @Value("${home.modules.notice:true}")
    private boolean moduleNotice;

    @Value("${home.modules.leaderboard:true}")
    private boolean moduleLeaderboard;

    @Value("${home.modules.lost-cat:true}")
    private boolean moduleLostCat;

    @Value("${home.modules.weather:true}")
    private boolean moduleWeather;

    @Override
    public HomeBootstrapVO bootstrap() {
        HomeBootstrapVO cached = homeCache.get(BOOTSTRAP_KEY, HomeBootstrapVO.class);
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        HomeBootstrapVO vo = new HomeBootstrapVO();

        // 热点：只取上线且在时间窗内的 HOTSPOT，最多 hotspot-limit 条
        vo.setHotspots(moduleHotspot ? hotspots(now) : Collections.emptyList());

        // 通告：置顶优先、其余按创建时间倒序的最新 1 条
        vo.setNotice(moduleNotice ? notice(now) : null);

        // 三榜默认月榜：任一榜失败独立降级为空，不拖垮整个首屏
        Map<String, List<RankItemVO>> boards = new LinkedHashMap<>();
        for (String type : BOARD_TYPES) {
            List<RankItemVO> items = Collections.emptyList();
            if (moduleLeaderboard) {
                try {
                    items = leaderboardService.leaderboard(type, "month", boardLimit);
                } catch (Exception e) {
                    log.warn("首页榜单降级为空: type={}, err={}", type, e.getMessage());
                }
            }
            boards.put(type, items);
        }
        vo.setBoards(boards);

        Map<String, Boolean> modules = new LinkedHashMap<>();
        modules.put("hotspot", moduleHotspot);
        modules.put("notice", moduleNotice);
        modules.put("leaderboard", moduleLeaderboard);
        modules.put("lostCat", moduleLostCat);
        modules.put("weather", moduleWeather);
        vo.setModules(modules);

        homeCache.set(BOOTSTRAP_KEY, vo, bootstrapCacheSeconds);
        return vo;
    }

    @Override
    public Page<HomeContent> noticeList(Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<HomeContent> wrapper = noticeWrapper(LocalDateTime.now());
        wrapper.orderByDesc(HomeContent::getPinned).orderByDesc(HomeContent::getCreatedAt);
        return homeContentMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize), wrapper);
    }

    @Override
    public HomeContent noticeDetail(Long id) {
        HomeContent content = homeContentMapper.selectById(id);
        if (content == null || !"NOTICE".equalsIgnoreCase(content.getType())
                || content.getStatus() == null || content.getStatus() != 1) {
            return null;
        }
        // pv 自增失败不阻塞详情返回
        try {
            homeContentMapper.incrementPv(id);
        } catch (Exception e) {
            log.warn("通告 pv 自增失败: id={}, err={}", id, e.getMessage());
        }
        return homeContentMapper.selectById(id);
    }

    @Override
    public void click(Long id) {
        // 匿名点击记数，失败不阻塞内部跳转
        try {
            homeContentMapper.incrementPv(id);
        } catch (Exception e) {
            log.warn("热点点击记数失败: id={}, err={}", id, e.getMessage());
        }
    }

    private List<HomeBootstrapVO.ContentItem> hotspots(LocalDateTime now) {
        LambdaQueryWrapper<HomeContent> wrapper = onlineWrapper("HOTSPOT", now);
        wrapper.orderByAsc(HomeContent::getSortOrder)
                .orderByDesc(HomeContent::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(hotspotLimit, 50)));
        List<HomeBootstrapVO.ContentItem> items = new ArrayList<>();
        for (HomeContent content : homeContentMapper.selectList(wrapper)) {
            items.add(toItem(content));
        }
        return items;
    }

    private HomeBootstrapVO.ContentItem notice(LocalDateTime now) {
        LambdaQueryWrapper<HomeContent> wrapper = onlineWrapper("NOTICE", now);
        wrapper.orderByDesc(HomeContent::getPinned)
                .orderByDesc(HomeContent::getCreatedAt)
                .last("LIMIT 1");
        HomeContent content = homeContentMapper.selectOne(wrapper);
        return content == null ? null : toItem(content);
    }

    private LambdaQueryWrapper<HomeContent> noticeWrapper(LocalDateTime now) {
        return onlineWrapper("NOTICE", now);
    }

    /** 只取 status=1 且 start_time<=now<=end_time；NULL 时间表示长期有效 */
    private LambdaQueryWrapper<HomeContent> onlineWrapper(String type, LocalDateTime now) {
        LambdaQueryWrapper<HomeContent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HomeContent::getType, type)
                .eq(HomeContent::getStatus, 1)
                .and(w -> w.isNull(HomeContent::getStartTime).or().le(HomeContent::getStartTime, now))
                .and(w -> w.isNull(HomeContent::getEndTime).or().ge(HomeContent::getEndTime, now));
        return wrapper;
    }

    private HomeBootstrapVO.ContentItem toItem(HomeContent c) {
        HomeBootstrapVO.ContentItem item = new HomeBootstrapVO.ContentItem();
        item.setId(c.getId());
        item.setTitle(c.getTitle());
        item.setSummary(c.getSummary());
        item.setCoverUrl(c.getCoverUrl());
        item.setTag(c.getTag());
        item.setLinkType(c.getLinkType());
        item.setLinkUrl(c.getLinkUrl());
        return item;
    }
}
