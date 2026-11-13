package com.straycat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.AdoptionApplication;

import java.util.List;

public interface AdoptionApplicationService extends IService<AdoptionApplication> {

    void apply(AdoptionApplication application);

    AdoptionApplication getApplication(Long id);

    /**
     * 审核领养申请，同步联动猫咪的生活状态
     */
    void review(Long applicationId, Long reviewerId, Integer status, String comment);

    /**
     * 补猫咪名和共同领养人数，列表和详情都靠它，避免前端拿到空名字
     */
    void fillDisplayInfo(List<AdoptionApplication> applications);
}
