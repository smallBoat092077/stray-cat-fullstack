package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.CatComment;
import com.straycat.vo.CatCommentVO;

public interface CatCommentService {

    /**
     * 某只猫咪的评论：按时间倒序分页的顶级评论，每条带回复总数和前 3 条回复
     */
    Page<CatCommentVO> listByCat(Long catId, Long viewerId, Integer pageNum, Integer pageSize);

    /**
     * 某条顶级评论下的全部回复，按时间正序分页（「展开更多回复」用）
     */
    Page<CatCommentVO> listReplies(Long parentId, Long viewerId, Integer pageNum, Integer pageSize);

    /**
     * 发表评论或回复。parentId 为 0/空是顶级评论，否则归到所属顶级评论这一楼，层级不再加深。
     * 内容走 msgSecCheck 先审后发，成功后同步 cat_profile.comment_count。
     */
    CatCommentVO create(Long userId, Long catId, CatComment form);

    /**
     * 软删评论：作者本人或管理员可删；删顶级评论会连整楼回复一起下架，计数同步回退
     */
    void delete(Long userId, Integer role, Long commentId);
}
