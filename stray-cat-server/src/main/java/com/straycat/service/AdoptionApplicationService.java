package com.straycat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.AdoptionApplication;

public interface AdoptionApplicationService extends IService<AdoptionApplication> {

    void apply(AdoptionApplication application);

    AdoptionApplication getApplication(Long id);

    /**
     * 审核领养申请，同步联动猫咪的生活状态
     */
    void review(Long applicationId, Long reviewerId, Integer status, String comment);
}
