package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.HomeContent;
import com.straycat.vo.HomeBootstrapVO;

/**
 * 首页公共内容：首屏聚合、通告列表/详情、热点点击记数。
 */
public interface HomeService {

    /** 热点 ≤3 + 最新通告 1 条 + 三榜默认月榜 Top5 + 模块开关，缓存 60s */
    HomeBootstrapVO bootstrap();

    /** 通告列表（仅上线且在时间窗内），分页 */
    Page<HomeContent> noticeList(Integer pageNum, Integer pageSize);

    /** 通告详情，pv 自增 */
    HomeContent noticeDetail(Long id);

    /** 热点点击记数，匿名调用，失败不阻塞 */
    void click(Long id);
}
