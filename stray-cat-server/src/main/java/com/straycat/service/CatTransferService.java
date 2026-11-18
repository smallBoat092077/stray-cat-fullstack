package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.CatTransferApply;
import com.straycat.entity.CatTransferPost;

import java.util.List;

public interface CatTransferService extends IService<CatTransferPost> {

    /** 在架列表，按创建时间倒序；返回前必须经 toPublic 脱敏 */
    Page<CatTransferPost> publicPosts(Integer pageNum, Integer pageSize, String provinceCode,
                                      String cityCode, String districtCode, String breed, String keyword);

    /** 详情：非在架信息仅本人/管理员可读（管理员按 userId 查角色）；浏览数 +1 */
    CatTransferPost publicDetail(Long id, Long userId);

    /** 对外视图：复制实体并把 contactPhone / contactName 置空 */
    CatTransferPost toPublic(CatTransferPost post);

    /** 发布（状态置待审） */
    Long publish(CatTransferPost post, Long userId);

    /** 我发布的全部状态，手机号不脱敏 */
    List<CatTransferPost> mine(Long userId);

    /** 下架，仅发布人 */
    void offline(Long id, Long userId);

    /** 提交接猫申请，并通知发布人 */
    Long apply(CatTransferApply apply, Long userId);

    /** 我的申请，回填 postTitle / postCatName，手机号不脱敏 */
    List<CatTransferApply> myApplies(Long userId);

    /** 某条转让收到的申请，仅发布人可见（含手机号） */
    List<CatTransferApply> appliesOfPost(Long postId, Long userId);

    /** 更新申请处理状态，仅该 post 的发布人 */
    void updateApplyStatus(Long applyId, Integer status, Long userId);

    /** 标记已转让，通知全部申请人 */
    void complete(Long postId, Long userId);

    /** 后台待审/全量列表，全字段不脱敏 */
    Page<CatTransferPost> adminPosts(Integer status, Integer pageNum, Integer pageSize);

    /** 后台审核：status 1 通过 / 4 驳回 */
    void audit(Long id, Integer status, String remark, Long reviewerId);
}
