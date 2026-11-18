package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爱猫搬家 - 接猫申请。contactPhone 只给该 post 的发布人看，申请人看自己的那条也可以看。
 */
@Data
@TableName("cat_transfer_apply")
public class CatTransferApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long applicantId;

    private String applyNote;

    private String contactPhone;

    /** 家人是否同意：0 否 1 是 */
    private Integer familyAgree;

    /** 家里是否已有其他宠物：0 否 1 是 */
    private Integer hasOtherPets;

    /** 是否同意回访：0 否 1 是 */
    private Integer promiseVisit;

    /** 0待处理 1已联系 2不合适 */
    private Integer status;

    private LocalDateTime createdAt;

    /** 以下两个字段不落库，仅在「我的申请」列表里按 postId 回填展示用 */
    @TableField(exist = false)
    private String postTitle;

    @TableField(exist = false)
    private String postCatName;
}
