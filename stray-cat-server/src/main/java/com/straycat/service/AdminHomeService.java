package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.HomeContent;
import com.straycat.entity.LostCatPost;
import com.straycat.request.HomeContentRequest;

/**
 * 后台：首页内容 CRUD + 寻猫审核（方案 5.2 / 6.4），全部由 @RequireRole(3) 保护。
 * 内容发布/上下线后主动清除 home:bootstrap:* 缓存。
 */
public interface AdminHomeService {

    Page<HomeContent> contentList(String type, Integer pageNum, Integer pageSize);

    HomeContent saveContent(Long adminId, HomeContentRequest request);

    void changeStatus(Long id, Integer status);

    /** 逻辑下线优先：置 status=0，不做物理删除 */
    void deleteContent(Long id);

    Page<LostCatPost> lostCatList(Integer auditStatus, Integer pageNum, Integer pageSize);

    /** auditStatus：1 通过 / 2 驳回（驳回需填原因） */
    void auditLostCat(Long reviewerId, Long id, Integer auditStatus, String auditRemark);
}
