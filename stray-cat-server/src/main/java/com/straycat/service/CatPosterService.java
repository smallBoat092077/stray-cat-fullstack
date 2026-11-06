package com.straycat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.CatPoster;

import java.util.List;

public interface CatPosterService extends IService<CatPoster> {
    List<CatPoster> listOrdered();
    void deleteById(Long id);
}
