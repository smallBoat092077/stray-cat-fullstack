package com.straycat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.AdoptionFollowUp;

public interface AdoptionFollowUpService extends IService<AdoptionFollowUp> {

    void submitFollowUp(AdoptionFollowUp followUp);
}
