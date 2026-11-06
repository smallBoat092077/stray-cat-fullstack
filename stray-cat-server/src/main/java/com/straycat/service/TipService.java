package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.vo.TipVO;

public interface TipService {

    int MIN_AMOUNT = 1;

    int MAX_AMOUNT = 200;

    TipVO tip(Long fromUserId, Long catId, Integer amount, String message);

    Page<TipVO> listByCat(Long catId, Integer pageNum, Integer pageSize);

    Page<TipVO> listMine(Long userId, Integer pageNum, Integer pageSize);
}
