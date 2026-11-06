package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.straycat.entity.CatPoster;
import com.straycat.mapper.CatPosterMapper;
import com.straycat.service.CatPosterService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatPosterServiceImpl extends ServiceImpl<CatPosterMapper, CatPoster> implements CatPosterService {

    @Override
    public List<CatPoster> listOrdered() {
        return list(new LambdaQueryWrapper<CatPoster>()
                .orderByAsc(CatPoster::getSortOrder)
                .orderByDesc(CatPoster::getCreatedAt));
    }

    @Override
    public void deleteById(Long id) {
        removeById(id);
    }
}
