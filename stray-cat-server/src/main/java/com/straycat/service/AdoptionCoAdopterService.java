package com.straycat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.AdoptionApplication;
import com.straycat.entity.AdoptionCoAdopter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AdoptionCoAdopterService extends IService<AdoptionCoAdopter> {

    /**
     * 主申请人按账号名或昵称邀请共同领养人，成功返回邀请记录 id
     */
    Long invite(Long applicationId, Long inviterId, String keyword);

    /**
     * 被邀请人回应：同意或拒绝
     */
    void respond(Long id, Long userId, boolean accept);

    /**
     * 主申请人撤回一条还没回应的邀请
     */
    void cancel(Long id, Long inviterId);

    /**
     * 我收到的共同领养邀请（含已同意/已拒绝），带猫咪名与邀请人昵称
     */
    List<AdoptionCoAdopter> listMine(Long userId);

    /**
     * 某个申请单下的共同领养人，带被邀请人昵称头像
     */
    List<AdoptionCoAdopter> listByApplication(Long applicationId);

    /**
     * 批量统计每张申请单已同意的共同领养人数，列表页展示用
     */
    Map<Long, Integer> countAccepted(Collection<Long> applicationIds);

    /**
     * 审核结束时通知所有已同意的共同领养人，避免他们只能从主申请人那里打听结果
     */
    void notifyCoAdopters(AdoptionApplication application, String catName, boolean approved, String reason);
}
