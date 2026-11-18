package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cat_poster")
public class CatPoster {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String imageUrl;

    /** 点击海报跳转的站内页面路径，如 pages/cat/detail?id=1；只允许站内路径，禁止外链 */
    private String linkUrl;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
