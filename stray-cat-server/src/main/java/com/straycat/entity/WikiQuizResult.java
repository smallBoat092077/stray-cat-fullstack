package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 猫咪百科 - 测评记录。未登录答题不落库，只有 userId 非 null 时才保存。
 */
@Data
@TableName("wiki_quiz_result")
public class WikiQuizResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 提交答案 JSON，如 [{"questionId":1,"optionIndex":2}] */
    private String answersJson;

    private Integer score;

    /** 最薄弱的两个维度 JSON 数组 */
    private String weakCategoriesJson;

    /** 推荐文章 id JSON 数组 */
    private String recommendedJson;

    private LocalDateTime createdAt;
}
