package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.Campaign;

import java.util.Map;

public interface CampaignService extends IService<Campaign> {

    /**
     * 后台分页列表。
     */
    Page<Campaign> page(Integer pageNum, Integer pageSize);

    /**
     * 新增或编辑活动。code 只能创建时设置（与已投放物料对齐），编辑时不可改。
     */
    Campaign saveCampaign(Campaign form);

    /**
     * 上下线。
     */
    void toggleStatus(Long id, int status);

    /**
     * 免登活动详情：活动信息 + 本活动关联猫咪（来自 share_link biz_type=cat 去重）
     * + 访问热度（pv / newUsers，只下发数字，不暴露访客身份）。
     * code 或 id 任一命中即可；活动不存在返回 null。
     */
    Map<String, Object> detail(String code, Long id);

    /**
     * 给「访问过该活动」的用户发活动提醒（站内必达，微信订阅尽力）。
     * 返回发送条数。
     */
    int sendReminder(Long campaignId, String content);
}
