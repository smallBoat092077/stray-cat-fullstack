package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.dto.QuizSubmitDTO;
import com.straycat.entity.WikiArticle;
import com.straycat.entity.WikiQuizResult;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.service.WikiService;
import com.straycat.vo.WikiQuestionVO;
import com.straycat.vo.WikiQuizResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "猫咪百科", description = "答题测评与科普文章接口")
@RestController
@RequestMapping("/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiService wikiService;

    @GetMapping("/categories")
    @Operation(summary = "五大分类及每类已发布文章数")
    public Result<List<Map<String, Object>>> categories() {
        return Result.success(wikiService.categories());
    }

    @GetMapping("/questions")
    @Operation(summary = "均衡抽题并打乱选项（不下发正确答案）")
    public Result<List<WikiQuestionVO>> questions(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(wikiService.questions(limit));
    }

    @PostMapping("/quiz/submit")
    @Operation(summary = "提交答案判分；登录用户落库测评记录")
    public Result<WikiQuizResultVO> submit(@RequestBody QuizSubmitDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        return Result.success(wikiService.submit(dto, userId));
    }

    @GetMapping("/articles")
    @Operation(summary = "已发布文章分页")
    public Result<Page<WikiArticle>> articles(@RequestParam(required = false) String category,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(wikiService.articles(category, pageNum, pageSize));
    }

    @GetMapping("/article/{id}")
    @Operation(summary = "文章详情（阅读数+1）")
    public Result<WikiArticle> article(@PathVariable Long id) {
        WikiArticle article = wikiService.article(id);
        return article == null ? Result.error("文章不存在或未发布") : Result.success(article);
    }

    @GetMapping("/my-quiz")
    @Operation(summary = "我的测评历史（需登录）")
    public Result<List<WikiQuizResult>> myQuiz(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(wikiService.myQuiz(userId));
    }
}
