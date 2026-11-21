package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.VolunteerApplication;
import com.straycat.request.VolunteerApplyRequest;

import java.util.Map;

public interface VolunteerApplicationService {

    /** 提交申请，返回申请 id */
    Long apply(Long userId, VolunteerApplyRequest req);

    /** 我的最新一条申请，无则 null */
    VolunteerApplication myLatest(Long userId);

    /** 后台分页列表，status 为空表示全部 */
    Page<VolunteerApplication> list(Integer pageNum, Integer pageSize, Integer status);

    /**
     * 审核：status 1通过 2拒绝。通过时把申请人 role 升为 1（志愿者）。
     */
    void review(Long applicationId, Long reviewerId, Integer status, String comment);

    /** 各状态计数，供后台 tab 角标 */
    Map<String, Long> counts();
}
