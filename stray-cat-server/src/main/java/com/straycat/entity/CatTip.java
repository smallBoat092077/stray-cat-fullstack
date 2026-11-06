package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cat_tip")
public class CatTip {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long catId;

    private Long fromUserId;

    private Integer coinAmount;

    private String message;

    private LocalDateTime createdAt;
}
