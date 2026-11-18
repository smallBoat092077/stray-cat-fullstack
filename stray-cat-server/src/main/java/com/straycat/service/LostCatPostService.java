package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.request.LostCatPostRequest;
import com.straycat.vo.LostCatPostVO;

import java.util.List;

/**
 * 寻猫启事（方案 6.2）。先审后发：广场只出 audit_status=1，我的发布含待审/驳回。
 */
public interface LostCatPostService {

    /** 广场列表，只出审核通过；status 可选（0 寻找中 / 1 已找到）；区域码可选（区域划分需求 R4，不带 = 全部口径） */
    Page<LostCatPostVO> list(Integer status, Integer pageNum, Integer pageSize,
                             String provinceCode, String cityCode, String districtCode);

    /** 详情，view_count 自增 */
    LostCatPostVO detail(Long id);

    /** 发布，返回启事 id；文本走内容安全检测，audit_status=0 */
    Long create(Long userId, LostCatPostRequest request);

    /** 我的发布（含待审/驳回，可见驳回原因） */
    List<LostCatPostVO> mine(Long userId);

    /** 标记已找到，仅本人 */
    void markFound(Long userId, Long id);
}
