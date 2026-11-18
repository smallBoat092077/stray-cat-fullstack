package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.entity.WikiArticle;
import com.straycat.entity.WikiQuestion;
import com.straycat.service.WikiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 猫咪百科题库/文章后台维护，全部 @RequireRole(3)。
 */
@Tag(name = "猫咪百科维护（管理员）")
@RestController
@RequestMapping("/admin/wiki")
@RequiredArgsConstructor
public class AdminWikiController {

    private final WikiService wikiService;

    @GetMapping("/questions")
    @Operation(summary = "题库列表（含草稿）")
    @RequireRole(3)
    public Result<Page<WikiQuestion>> questions(@RequestParam(required = false) Integer status,
                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(wikiService.adminQuestions(status, pageNum, pageSize));
    }

    @PostMapping("/questions")
    @Operation(summary = "新增/更新题目（options 由服务端序列化为 optionsJson）")
    @RequireRole(3)
    public Result<WikiQuestion> saveQuestion(@RequestBody Map<String, Object> body) {
        return Result.success(wikiService.saveQuestion(body));
    }

    @PostMapping("/questions/{id}/status")
    @Operation(summary = "题目上下架")
    @RequireRole(3)
    public Result<?> questionStatus(@PathVariable Long id, @RequestParam Integer status) {
        wikiService.questionStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/questions/{id}")
    @Operation(summary = "删除题目")
    @RequireRole(3)
    public Result<?> deleteQuestion(@PathVariable Long id) {
        wikiService.deleteQuestion(id);
        return Result.success();
    }

    @GetMapping("/articles")
    @Operation(summary = "文章列表（含草稿）")
    @RequireRole(3)
    public Result<Page<WikiArticle>> articles(@RequestParam(required = false) String category,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(wikiService.adminArticles(category, status, pageNum, pageSize));
    }

    @PostMapping("/articles")
    @Operation(summary = "新增/更新文章")
    @RequireRole(3)
    public Result<WikiArticle> saveArticle(@RequestBody Map<String, Object> body) {
        return Result.success(wikiService.saveArticle(body));
    }

    @PostMapping("/articles/{id}/status")
    @Operation(summary = "文章上下架")
    @RequireRole(3)
    public Result<?> articleStatus(@PathVariable Long id, @RequestParam Integer status) {
        wikiService.articleStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/articles/{id}")
    @Operation(summary = "删除文章")
    @RequireRole(3)
    public Result<?> deleteArticle(@PathVariable Long id) {
        wikiService.deleteArticle(id);
        return Result.success();
    }
}
