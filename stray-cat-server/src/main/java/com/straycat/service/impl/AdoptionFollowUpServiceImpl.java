package com.straycat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.straycat.entity.AdoptionFollowUp;
import com.straycat.entity.CatProfile;
import com.straycat.mapper.AdoptionFollowUpMapper;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.service.AdoptionFollowUpService;
import com.straycat.util.CatLifeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
public class AdoptionFollowUpServiceImpl extends ServiceImpl<AdoptionFollowUpMapper, AdoptionFollowUp> implements AdoptionFollowUpService {

    @Resource
    private CatProfileMapper catProfileMapper;

    @Override
    public void submitFollowUp(AdoptionFollowUp followUp) {
        followUp.setStatus(1);
        followUp.setCreatedAt(LocalDateTime.now());
        save(followUp);

        // 交完首次回访，猫咪进入回访期，回访期满由管理员置回已领养
        if (followUp.getCatId() == null) {
            return;
        }
        CatProfile cat = catProfileMapper.selectById(followUp.getCatId());
        if (cat != null && CatLifeStatus.canMove(cat.getLifeStatus(), CatLifeStatus.FOLLOWING_UP)) {
            cat.setLifeStatus(CatLifeStatus.FOLLOWING_UP);
            cat.setUpdatedAt(LocalDateTime.now());
            catProfileMapper.updateById(cat);
        }
    }
}
