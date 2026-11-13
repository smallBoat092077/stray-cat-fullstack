package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.Feedback;

public interface FeedbackService {

    /**
     * 提交反馈。内容过内容安全检测，同一用户 24 小时内最多 3 条。
     */
    Feedback create(Long userId, Integer type, String content, String images);

    /** 我的反馈列表（含管理员回复），最新的在前 */
    Page<Feedback> listMine(Long userId, Integer pageNum, Integer pageSize);

    /** 后台分页 */
    Page<Feedback> page(Integer status, Integer pageNum, Integer pageSize);

    /**
     * 后台处理：回复或忽略。回复时给反馈人补一条站内消息。
     */
    void handle(Long id, Integer status, String reply);
}
