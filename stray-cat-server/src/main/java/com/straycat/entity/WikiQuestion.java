package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 猫咪百科 - 题目。optionsJson 是 JSON 数组字符串，answerIndex 是原始选项下标；
 * 对外抽题时服务端打乱选项并只下发题目与选项，绝不下发 answerIndex。
 */
@Data
@TableName("wiki_question")
public class WikiQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** feeding/health/behavior/hygiene/firstaid */
    private String category;

    private String title;

    /** JSON 数组字符串，如 ["选项A","选项B","选项C"] */
    private String optionsJson;

    /** 正确答案在 optionsJson 原始数组中的下标 */
    private Integer answerIndex;

    /** 解析，后台维护用，不下发给答题接口 */
    private String analysis;

    private Integer sort;

    /** 0草稿 1启用 */
    private Integer status;

    private LocalDateTime createdAt;
}
