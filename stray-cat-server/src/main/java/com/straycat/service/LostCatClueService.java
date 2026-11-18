package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.LostCatClue;
import com.straycat.request.LostCatClueRequest;
import com.straycat.vo.LostCatClueVO;

import java.util.List;

/**
 * 寻猫线索（方案 4）。先审后发：提交后 audit_status=0，后台审核通过才通知启事发布者。
 * 线索详情是匿名放行的 GET，可见性一律在 Service 内自行判定。
 */
public interface LostCatClueService {

    /** 提交线索，返回线索 id；文本走内容安全检测，audit_status=0 */
    Long create(Long userId, LostCatClueRequest request);

    /** 线索详情；currentUserId 可为 null（匿名），Service 内做可见性判断 */
    LostCatClueVO detail(Long id, Long currentUserId);

    /** 我上报的线索（含审核状态与驳回原因） */
    List<LostCatClueVO> mine(Long userId);

    /** 后台：待审/全部列表 */
    Page<LostCatClue> list(Integer auditStatus, Integer pageNum, Integer pageSize);

    /** 后台：通过 / 驳回；仅通过时给启事发布者推站内消息 */
    void audit(Long reviewerId, Long id, Integer auditStatus, String auditRemark);
}
