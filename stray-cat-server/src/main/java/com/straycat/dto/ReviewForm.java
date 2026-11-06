package com.straycat.dto;

import lombok.Data;

/**
 * 审核提交体，领养申请和投喂打卡记录共用。
 */
@Data
public class ReviewForm {

    /**
     * 被审核对象的 id：领养申请 id 或投喂打卡记录 id
     */
    private Long id;

    /**
     * 领养申请传 2已通过/3已拒绝，投喂打卡记录传 1已通过/2已拒绝
     */
    private Integer status;

    private String comment;
}
