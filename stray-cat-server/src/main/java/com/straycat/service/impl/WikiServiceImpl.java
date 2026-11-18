package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.common.ResultCode;
import com.straycat.dto.QuizAnswerDTO;
import com.straycat.dto.QuizSubmitDTO;
import com.straycat.entity.WikiArticle;
import com.straycat.entity.WikiQuestion;
import com.straycat.entity.WikiQuizResult;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.WikiArticleMapper;
import com.straycat.mapper.WikiQuestionMapper;
import com.straycat.mapper.WikiQuizResultMapper;
import com.straycat.service.WikiService;
import com.straycat.vo.WikiQuestionVO;
import com.straycat.vo.WikiQuizResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WikiServiceImpl implements WikiService {

    /** 五个维度，顺序即前端展示顺序 */
    private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>();
    static {
        CATEGORY_NAMES.put("feeding", "喂养");
        CATEGORY_NAMES.put("health", "健康观察");
        CATEGORY_NAMES.put("behavior", "行为沟通");
        CATEGORY_NAMES.put("hygiene", "清洁护理");
        CATEGORY_NAMES.put("firstaid", "应急处理");
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WikiQuestionMapper questionMapper;
    private final WikiArticleMapper articleMapper;
    private final WikiQuizResultMapper quizResultMapper;

    @Override
    public List<Map<String, Object>> categories() {
        List<WikiArticle> published = articleMapper.selectList(new LambdaQueryWrapper<WikiArticle>()
                .select(WikiArticle::getCategory).eq(WikiArticle::getStatus, 1));
        Map<String, Long> counts = published.stream()
                .collect(Collectors.groupingBy(a -> a.getCategory() == null ? "" : a.getCategory(),
                        Collectors.counting()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, String> e : CATEGORY_NAMES.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", e.getKey());
            item.put("name", e.getValue());
            item.put("count", counts.getOrDefault(e.getKey(), 0L));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<WikiQuestionVO> questions(Integer limit) {
        int target = limit == null ? 10 : Math.max(1, Math.min(limit, 100));
        List<WikiQuestion> all = questionMapper.selectList(new LambdaQueryWrapper<WikiQuestion>()
                .eq(WikiQuestion::getStatus, 1)
                .orderByAsc(WikiQuestion::getSort).orderByAsc(WikiQuestion::getId));
        Map<String, List<WikiQuestion>> byCategory = all.stream()
                .collect(Collectors.groupingBy(q -> q.getCategory() == null ? "" : q.getCategory()));

        // 每维两轮各取一题（每维 2 题），不足再用其余分类的题补齐，保证维度均衡
        List<WikiQuestion> picked = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (int round = 0; round < 2; round++) {
            for (String category : CATEGORY_NAMES.keySet()) {
                if (picked.size() >= target) {
                    break;
                }
                List<WikiQuestion> list = byCategory.getOrDefault(category, List.of());
                if (round < list.size()) {
                    WikiQuestion q = list.get(round);
                    picked.add(q);
                    used.add(q.getId());
                }
            }
        }
        if (picked.size() < target) {
            for (WikiQuestion q : all) {
                if (picked.size() >= target) {
                    break;
                }
                if (!used.contains(q.getId())) {
                    picked.add(q);
                    used.add(q.getId());
                }
            }
        }

        List<WikiQuestionVO> vos = new ArrayList<>();
        for (WikiQuestion q : picked) {
            WikiQuestionVO vo = new WikiQuestionVO();
            vo.setId(q.getId());
            vo.setCategory(q.getCategory());
            vo.setTitle(q.getTitle());
            vo.setOptions(shuffledOptions(q));
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public WikiQuizResultVO submit(QuizSubmitDTO dto, Long userId) {
        WikiQuizResultVO vo = new WikiQuizResultVO();
        List<QuizAnswerDTO> answers = dto == null || dto.getAnswers() == null
                ? List.of() : dto.getAnswers();
        List<Long> ids = answers.stream().map(QuizAnswerDTO::getQuestionId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, WikiQuestion> questionMap = ids.isEmpty() ? Map.of()
                : questionMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(WikiQuestion::getId, Function.identity(), (a, b) -> a));

        // stat[0] 答对数，stat[1] 该维度题数
        Map<String, int[]> stats = new LinkedHashMap<>();
        int correct = 0;
        for (QuizAnswerDTO answer : answers) {
            WikiQuestion q = questionMap.get(answer.getQuestionId());
            if (q == null) {
                continue;
            }
            String category = q.getCategory() == null ? "" : q.getCategory();
            int[] stat = stats.computeIfAbsent(category, k -> new int[2]);
            stat[1]++;
            List<String> shuffled = shuffledOptions(q);
            if (answer.getOptionIndex() != null && answer.getOptionIndex() == correctIndexAfterShuffle(q, shuffled)) {
                correct++;
                stat[0]++;
            }
        }

        int score = Math.min(correct * 10, 100);
        vo.setCorrectCount(correct);
        vo.setScore(score);
        vo.setPortrait(portrait(score));

        // 五维得分率（0-100）下发，供结果页画五维条；没答过的维度给 0 但保留 key，保证五维齐全
        Map<String, Integer> categoryScores = new LinkedHashMap<>();
        for (String code : CATEGORY_NAMES.keySet()) {
            int[] stat = stats.get(code);
            int rate = (stat == null || stat[1] == 0) ? 0 : (int) Math.round(stat[0] * 100.0 / stat[1]);
            categoryScores.put(code, rate);
        }
        vo.setCategoryScores(categoryScores);

        List<Map.Entry<String, int[]>> weakest = new ArrayList<>(stats.entrySet());
        weakest.sort(Comparator.comparingDouble((Map.Entry<String, int[]> e) ->
                (double) e.getValue()[0] / e.getValue()[1]));
        List<String> weakCodes = weakest.stream().limit(2).map(Map.Entry::getKey).collect(Collectors.toList());
        vo.setWeakCategories(weakCodes.stream()
                .map(c -> CATEGORY_NAMES.getOrDefault(c, c)).collect(Collectors.toList()));

        List<WikiArticle> recommended;
        if (score == 100) {
            // 满分：直接推荐进阶照护标签文章
            recommended = articleMapper.selectList(new LambdaQueryWrapper<WikiArticle>()
                    .eq(WikiArticle::getStatus, 1).like(WikiArticle::getTags, "进阶")
                    .orderByAsc(WikiArticle::getSort).last("LIMIT 4"));
        } else {
            recommended = new ArrayList<>();
            for (String code : weakCodes) {
                recommended.addAll(recommend(code, 2));
            }
        }
        // 按 id 去重，保持推荐顺序
        vo.setRecommended(recommended.stream()
                .collect(Collectors.toMap(WikiArticle::getId, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new)).values().stream().collect(Collectors.toList()));

        if (userId != null) {
            try {
                WikiQuizResult record = new WikiQuizResult();
                record.setUserId(userId);
                record.setAnswersJson(MAPPER.writeValueAsString(answers));
                record.setScore(score);
                record.setWeakCategoriesJson(MAPPER.writeValueAsString(vo.getWeakCategories()));
                record.setRecommendedJson(MAPPER.writeValueAsString(recommended.stream()
                        .map(WikiArticle::getId).collect(Collectors.toList())));
                record.setCreatedAt(LocalDateTime.now());
                quizResultMapper.insert(record);
            } catch (Exception e) {
                log.warn("测评记录落库失败: userId={}, err={}", userId, e.getMessage());
            }
        }
        return vo;
    }

    @Override
    public Page<WikiArticle> articles(String category, Integer pageNum, Integer pageSize) {
        Page<WikiArticle> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<WikiArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WikiArticle::getStatus, 1);
        if (category != null && !category.isBlank()) {
            wrapper.eq(WikiArticle::getCategory, category);
        }
        wrapper.orderByAsc(WikiArticle::getSort).orderByDesc(WikiArticle::getCreatedAt);
        return articleMapper.selectPage(page, wrapper);
    }

    @Override
    public WikiArticle article(Long id) {
        WikiArticle article = articleMapper.selectById(id);
        if (article == null || article.getStatus() == null || article.getStatus() != 1) {
            return null;
        }
        try {
            articleMapper.incrementView(id);
        } catch (Exception e) {
            log.warn("百科文章阅读数自增失败: id={}, err={}", id, e.getMessage());
        }
        return articleMapper.selectById(id);
    }

    @Override
    public List<WikiQuizResult> myQuiz(Long userId) {
        return quizResultMapper.selectList(new LambdaQueryWrapper<WikiQuizResult>()
                .eq(WikiQuizResult::getUserId, userId)
                .orderByDesc(WikiQuizResult::getCreatedAt));
    }

    /* ---------------- 后台维护 ---------------- */

    @Override
    public Page<WikiQuestion> adminQuestions(Integer status, Integer pageNum, Integer pageSize) {
        Page<WikiQuestion> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        LambdaQueryWrapper<WikiQuestion> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(WikiQuestion::getStatus, status);
        }
        wrapper.orderByAsc(WikiQuestion::getSort).orderByDesc(WikiQuestion::getId);
        return questionMapper.selectPage(page, wrapper);
    }

    @Override
    public WikiQuestion saveQuestion(Map<String, Object> body) {
        WikiQuestion q = new WikiQuestion();
        Long id = longVal(body.get("id"));
        if (id != null) {
            q.setId(id);
        }
        q.setCategory(strVal(body.get("category")));
        q.setTitle(strVal(body.get("title")));
        List<String> options = body.get("options") instanceof List<?> list
                ? list.stream().map(String::valueOf).collect(Collectors.toList())
                : new ArrayList<>();
        try {
            q.setOptionsJson(MAPPER.writeValueAsString(options));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "选项格式错误");
        }
        q.setAnswerIndex(intVal(body.get("answerIndex"), 0));
        q.setAnalysis(strVal(body.get("analysis")));
        q.setSort(intVal(body.get("sort"), 0));
        q.setStatus(intVal(body.get("status"), 0));
        if (q.getId() == null) {
            q.setCreatedAt(LocalDateTime.now());
            questionMapper.insert(q);
        } else {
            questionMapper.updateById(q);
        }
        return q;
    }

    @Override
    public void questionStatus(Long id, Integer status) {
        requireQuestion(id);
        WikiQuestion q = new WikiQuestion();
        q.setId(id);
        q.setStatus(status == null ? 0 : status);
        questionMapper.updateById(q);
    }

    @Override
    public void deleteQuestion(Long id) {
        requireQuestion(id);
        questionMapper.deleteById(id);
    }

    @Override
    public Page<WikiArticle> adminArticles(String category, Integer status, Integer pageNum, Integer pageSize) {
        Page<WikiArticle> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        LambdaQueryWrapper<WikiArticle> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            wrapper.eq(WikiArticle::getCategory, category);
        }
        if (status != null) {
            wrapper.eq(WikiArticle::getStatus, status);
        }
        wrapper.orderByAsc(WikiArticle::getSort).orderByDesc(WikiArticle::getId);
        return articleMapper.selectPage(page, wrapper);
    }

    @Override
    public WikiArticle saveArticle(Map<String, Object> body) {
        WikiArticle a = new WikiArticle();
        Long id = longVal(body.get("id"));
        if (id != null) {
            a.setId(id);
        }
        a.setTitle(strVal(body.get("title")));
        a.setSummary(strVal(body.get("summary")));
        a.setContent(strVal(body.get("content")));
        a.setCoverUrl(strVal(body.get("coverUrl")));
        a.setCategory(strVal(body.get("category")));
        a.setTags(strVal(body.get("tags")));
        a.setReadMinutes(intVal(body.get("readMinutes"), 3));
        a.setStatus(intVal(body.get("status"), 0));
        a.setSort(intVal(body.get("sort"), 0));
        if (a.getId() == null) {
            a.setViewCount(0);
            a.setCreatedAt(LocalDateTime.now());
            a.setUpdatedAt(LocalDateTime.now());
            articleMapper.insert(a);
        } else {
            a.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(a);
        }
        return a;
    }

    @Override
    public void articleStatus(Long id, Integer status) {
        requireArticle(id);
        WikiArticle a = new WikiArticle();
        a.setId(id);
        a.setStatus(status == null ? 0 : status);
        a.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(a);
    }

    @Override
    public void deleteArticle(Long id) {
        requireArticle(id);
        articleMapper.deleteById(id);
    }

    /* ---------------- 工具 ---------------- */

    /**
     * 选项按 questionId 作种子做确定性 shuffle：抽题和判分两次调用得到完全相同的顺序，
     * 用同一个顺序算出正确答案的新下标，提交时即可无状态判分（无需 Redis 保存映射）。
     */
    private List<String> shuffledOptions(WikiQuestion q) {
        List<String> options = parseOptions(q.getOptionsJson());
        if (options.size() > 1) {
            Collections.shuffle(options, new Random(q.getId() == null ? 0L : q.getId()));
        }
        return options;
    }

    private int correctIndexAfterShuffle(WikiQuestion q, List<String> shuffled) {
        List<String> original = parseOptions(q.getOptionsJson());
        int answer = q.getAnswerIndex() == null ? 0 : q.getAnswerIndex();
        if (original.isEmpty() || answer < 0 || answer >= original.size()) {
            return 0;
        }
        int index = shuffled.indexOf(original.get(answer));
        return index < 0 ? 0 : index;
    }

    private List<String> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("题目选项解析失败: {}", json);
            return new ArrayList<>();
        }
    }

    private String portrait(int score) {
        if (score >= 90) {
            return "资深猫管家";
        }
        if (score >= 70) {
            return "稳扎稳打型铲屎官";
        }
        if (score >= 50) {
            return "成长中的铲屎官";
        }
        return "新手上路";
    }

    /** 本分类 2 篇，不足按相邻维度顺序补，凑满需要的篇数 */
    private List<WikiArticle> recommend(String category, int need) {
        List<WikiArticle> own = articlesByCategory(category);
        List<WikiArticle> out = new ArrayList<>(own.subList(0, Math.min(need, own.size())));
        if (out.size() < need) {
            List<String> cats = new ArrayList<>(CATEGORY_NAMES.keySet());
            int index = cats.indexOf(category);
            List<String> others = cats.stream().filter(c -> !c.equals(category))
                    .sorted(Comparator.comparingInt(c -> Math.abs(cats.indexOf(c) - (index < 0 ? 0 : index))))
                    .collect(Collectors.toList());
            for (String code : others) {
                for (WikiArticle a : articlesByCategory(code)) {
                    if (out.size() >= need) {
                        break;
                    }
                    if (out.stream().noneMatch(x -> x.getId().equals(a.getId()))) {
                        out.add(a);
                    }
                }
                if (out.size() >= need) {
                    break;
                }
            }
        }
        return out;
    }

    private List<WikiArticle> articlesByCategory(String category) {
        return articleMapper.selectList(new LambdaQueryWrapper<WikiArticle>()
                .eq(WikiArticle::getStatus, 1).eq(WikiArticle::getCategory, category)
                .orderByAsc(WikiArticle::getSort).orderByDesc(WikiArticle::getCreatedAt));
    }

    private void requireQuestion(Long id) {
        if (questionMapper.selectById(id) == null) {
            throw new BusinessException("题目不存在");
        }
    }

    private void requireArticle(Long id) {
        if (articleMapper.selectById(id) == null) {
            throw new BusinessException("文章不存在");
        }
    }

    private Long longVal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private Integer intVal(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
    }

    private String strVal(Object value) {
        return value == null ? null : value.toString();
    }
}
