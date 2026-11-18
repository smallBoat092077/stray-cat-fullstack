package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爱猫搬家 - 转让信息。
 * contactName / contactPhone 属于隐私字段，只在受控接口（发布人看申请、申请人看自己）返回，
 * 列表/详情统一用 CatTransferService.toPublic 置空后再下发。
 */
@Data
@TableName("cat_transfer_post")
public class CatTransferPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布人 */
    private Long userId;

    private String catName;

    private String breed;

    /** 月龄 */
    private Integer ageMonths;

    /** 性别：公 / 母 / 未知，前端原样展示 */
    private String gender;

    /** 是否绝育：0 否 1 是 */
    private Integer neutered;

    /** 是否疫苗：0 否 1 是 */
    private Integer vaccinated;

    private String healthNote;

    private String personality;

    private String provinceCode;

    private String cityCode;

    private String districtCode;

    /** 模糊地点，如「洪山区」，不到门牌 */
    private String addressHint;

    /** 转让原因 */
    private String reason;

    /** 领养要求 */
    private String requirement;

    /** 逗号分隔的 /uploads/... 相对路径，最多 6 张 */
    private String photos;

    private String contactName;

    private String contactPhone;

    /** 0待审 1在架 2已转让 3已下架 4驳回 */
    private Integer status;

    private Integer viewCount;

    private Integer applyCount;

    private Long reviewerId;

    private String reviewRemark;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
