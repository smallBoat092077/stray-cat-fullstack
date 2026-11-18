package com.straycat.vo;

import com.straycat.entity.WikiArticle;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WikiQuizResultVO {

    /** 满分 100，每题 10 分 */
    private Integer score;

    /** 知识画像标签 */
    private String portrait;

    /** 最薄弱的两个维度（中文名） */
    private List<String> weakCategories;

    /** 每个薄弱维度推荐 2 篇文章 */
    private List<WikiArticle> recommended;

    private Integer correctCount;

    /**
     * 五维得分率（0-100），key 为维度 code（feeding/health/behavior/hygiene/firstaid）。
     * 供结果页画五维得分条；没答过的维度给 0 但保留 key，保证前端五维齐全。
     */
    private Map<String, Integer> categoryScores;
}
