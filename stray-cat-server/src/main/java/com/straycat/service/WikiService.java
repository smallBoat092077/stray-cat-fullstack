package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.dto.QuizSubmitDTO;
import com.straycat.entity.WikiArticle;
import com.straycat.entity.WikiQuestion;
import com.straycat.entity.WikiQuizResult;
import com.straycat.vo.WikiQuestionVO;
import com.straycat.vo.WikiQuizResultVO;

import java.util.List;
import java.util.Map;

public interface WikiService {

    /** 五大分类 + 每类已发布文章数 */
    List<Map<String, Object>> categories();

    /** 按维度均衡抽题，服务端打乱选项，不返回答案 */
    List<WikiQuestionVO> questions(Integer limit);

    /** 服务端判分 + 画像 + 薄弱维度 + 推荐文章；userId 非 null 时落库 */
    WikiQuizResultVO submit(QuizSubmitDTO dto, Long userId);

    /** 已发布文章分页 */
    Page<WikiArticle> articles(String category, Integer pageNum, Integer pageSize);

    /** 文章详情，阅读数 +1（失败吞掉） */
    WikiArticle article(Long id);

    /** 我的测评历史 */
    List<WikiQuizResult> myQuiz(Long userId);

    /* ---------- 后台维护 ---------- */

    Page<WikiQuestion> adminQuestions(Integer status, Integer pageNum, Integer pageSize);

    WikiQuestion saveQuestion(Map<String, Object> body);

    void questionStatus(Long id, Integer status);

    void deleteQuestion(Long id);

    Page<WikiArticle> adminArticles(String category, Integer status, Integer pageNum, Integer pageSize);

    WikiArticle saveArticle(Map<String, Object> body);

    void articleStatus(Long id, Integer status);

    void deleteArticle(Long id);
}
