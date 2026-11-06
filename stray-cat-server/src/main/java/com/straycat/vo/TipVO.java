package com.straycat.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TipVO {

    private Long id;

    private Long catId;

    private String catName;

    /**
     * 脱敏后的打赏人昵称
     */
    private String nickname;

    private Integer coinAmount;

    private String message;

    private LocalDateTime createdAt;

    /**
     * 本次打赏生成的投喂任务 id，打赏接口返回，列表接口不查这一列
     */
    private Long taskId;
}
