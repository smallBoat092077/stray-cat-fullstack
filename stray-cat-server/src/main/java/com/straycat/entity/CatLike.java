package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cat_like")
public class CatLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long catId;

    private Long userId;

    private LocalDateTime createdAt;
}
