<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">猫咪百科</span>
      <el-button size="small" @click="loadAll">刷新</el-button>
    </div>

    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      show-icon
      class="retry-tip"
      title="加载失败，请检查后端服务或网络后重试"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="loadAll">重试</el-button>
      </template>
    </el-alert>

    <el-tabs v-model="activeTab" class="wiki-tabs">
      <!-- 题库管理 -->
      <el-tab-pane label="题库管理" name="question">
        <div class="filter-row">
          <el-select v-model="questionCategory" placeholder="全部分类" clearable style="width: 180px">
            <el-option v-for="c in CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <span class="muted">共 {{ filteredQuestions.length }} 题 · 答题测评每次按维度抽 10 题</span>
          <el-button size="small" type="primary" class="filter-action" @click="openQuestionDialog()">新增题目</el-button>
        </div>

        <el-table :data="filteredQuestions" v-loading="loading" size="small" border empty-text="暂无题目">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="分类" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ categoryText(row.category) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="题目" min-width="220" show-overflow-tooltip />
          <el-table-column label="选项数" width="80">
            <template #default="{ row }">{{ parseOptions(row.optionsJson).length }}</template>
          </el-table-column>
          <el-table-column label="正确答案" width="110">
            <template #default="{ row }">
              <span v-if="row.answerIndex !== null && row.answerIndex !== undefined">
                {{ row.answerIndex }} · {{ optionLabel(row.answerIndex) }}
              </span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="解析" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.analysis || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '草稿' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="70" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openQuestionDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeQuestion(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 文章管理 -->
      <el-tab-pane label="文章管理" name="article">
        <div class="filter-row">
          <el-select v-model="articleCategory" placeholder="全部分类" clearable style="width: 180px">
            <el-option v-for="c in CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <span class="muted">共 {{ filteredArticles.length }} 篇 · 按分类筛选小程序百科文章</span>
          <el-button size="small" type="primary" class="filter-action" @click="openArticleDialog()">新增文章</el-button>
        </div>

        <el-table :data="filteredArticles" v-loading="loading" size="small" border empty-text="暂无文章">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="分类" width="120">
            <template #default="{ row }"><el-tag size="small">{{ categoryText(row.category) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="摘要" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.summary || '—' }}</template>
          </el-table-column>
          <el-table-column label="阅读时长" width="100">
            <template #default="{ row }">{{ row.readMinutes ?? '—' }} 分钟</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '发布' : '草稿' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="viewCount" label="浏览量" width="90" />
          <el-table-column prop="sort" label="排序" width="70" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openArticleDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeArticle(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 题目 dialog -->
    <el-dialog v-model="questionDialog.visible" :title="questionForm.id ? '编辑题目' : '新增题目'" width="620px">
      <el-form label-width="100px">
        <el-form-item label="分类" required>
          <el-select v-model="questionForm.category" style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目" required>
          <el-input v-model="questionForm.title" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="如：幼猫每天需要喝多少水？" />
        </el-form-item>
        <el-form-item label="选项" required>
          <div class="option-list">
            <div v-for="(opt, i) in questionForm.options" :key="i" class="option-row">
              <span class="option-key">{{ optionLabel(i) }}</span>
              <el-input v-model="questionForm.options[i]" :placeholder="`选项 ${i + 1}`" />
              <el-button
                size="small"
                :disabled="questionForm.options.length <= 3"
                @click="removeOption(i)"
              >删除</el-button>
            </div>
            <el-button size="small" :disabled="questionForm.options.length >= 6" @click="addOption">+ 添加选项</el-button>
            <span class="muted option-hint">至少 3 项、最多 6 项；提交时序列化为 optionsJson</span>
          </div>
        </el-form-item>
        <el-form-item label="正确答案" required>
          <el-select v-model="questionForm.answerIndex" style="width: 100%">
            <el-option
              v-for="(opt, i) in questionForm.options"
              :key="i"
              :label="`${optionLabel(i)} · ${opt || '(空)'}`"
              :value="i"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="questionForm.analysis" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="答对/答错时的解析说明" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="questionForm.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="questionForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="草稿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="questionDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveQuestion">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文章 dialog -->
    <el-dialog v-model="articleDialog.visible" :title="articleForm.id ? '编辑文章' : '新增文章'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="标题" required>
          <el-input v-model="articleForm.title" maxlength="80" placeholder="如：新手养猫第一周清单" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="articleForm.summary" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="articleForm.category" style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="articleForm.tags" placeholder="多个标签用逗号分隔，如：新手,喂养,幼猫" />
        </el-form-item>
        <el-form-item label="封面图 URL">
          <el-input v-model="articleForm.coverUrl" placeholder="/uploads/xxx.jpg 或 https://..." />
          <div v-if="articleForm.coverUrl" class="cover-preview-row">
            <el-image :src="imageUrl(articleForm.coverUrl)" fit="cover" class="cover-preview" />
          </div>
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="articleForm.content" type="textarea" :rows="8" placeholder="科普正文（纯文本 / 富文本源）" />
        </el-form-item>
        <el-form-item label="阅读时长">
          <el-input-number v-model="articleForm.readMinutes" :min="1" :max="999" controls-position="right" />
          <span class="muted unit">分钟</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="articleForm.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="articleForm.status" :active-value="1" :inactive-value="0" active-text="发布" inactive-text="草稿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="articleDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveArticle">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { wikiApi } from '../api';
import { imageUrl } from '../utils/dict';

const CATEGORIES = [
  { value: 'feeding', label: '喂养' },
  { value: 'health', label: '健康观察' },
  { value: 'behavior', label: '行为沟通' },
  { value: 'hygiene', label: '清洁护理' },
  { value: 'firstaid', label: '应急处理' }
];

const activeTab = ref('question');
const questionCategory = ref('');
const articleCategory = ref('');
const questions = ref([]);
const articles = ref([]);
const loading = ref(false);
const saving = ref(false);
const error = ref(false);

const questionDialog = reactive({ visible: false });
const articleDialog = reactive({ visible: false });

const emptyQuestion = () => ({
  id: null,
  category: 'feeding',
  title: '',
  options: ['', '', '', ''],
  answerIndex: 0,
  analysis: '',
  sort: 0,
  status: 1
});
const questionForm = reactive(emptyQuestion());

const emptyArticle = () => ({
  id: null,
  title: '',
  summary: '',
  content: '',
  coverUrl: '',
  category: 'feeding',
  tags: '',
  readMinutes: 3,
  status: 1,
  sort: 0
});
const articleForm = reactive(emptyArticle());

const filteredQuestions = computed(() =>
  questionCategory.value
    ? questions.value.filter((q) => q.category === questionCategory.value)
    : questions.value
);
const filteredArticles = computed(() =>
  articleCategory.value
    ? articles.value.filter((a) => a.category === articleCategory.value)
    : articles.value
);

function categoryText(value) {
  return CATEGORIES.find((c) => c.value === value)?.label || value || '—';
}

// 选项序号：0 -> A，1 -> B，...
function optionLabel(index) {
  return typeof index === 'number' && index >= 0 && index < 26
    ? String.fromCharCode(65 + index)
    : '—';
}

function parseOptions(json) {
  if (!json) return [];
  try {
    const arr = typeof json === 'string' ? JSON.parse(json) : json;
    return Array.isArray(arr) ? arr : [];
  } catch (err) {
    return [];
  }
}

async function loadAll() {
  loading.value = true;
  error.value = false;
  try {
    const [qList, aList] = await Promise.all([wikiApi.questions(), wikiApi.articles()]);
    questions.value = Array.isArray(qList) ? qList : (qList?.records || []);
    articles.value = Array.isArray(aList) ? aList : (aList?.records || []);
  } catch (err) {
    error.value = true;
    // 提示由 http 拦截器统一给
  } finally {
    loading.value = false;
  }
}

// ---------- 题库 ----------
function openQuestionDialog(row) {
  if (row) {
    const opts = parseOptions(row.optionsJson);
    Object.assign(questionForm, {
      id: row.id,
      category: row.category || 'feeding',
      title: row.title || '',
      options: opts.length ? [...opts] : ['', '', '', ''],
      answerIndex: row.answerIndex ?? 0,
      analysis: row.analysis || '',
      sort: row.sort ?? 0,
      status: row.status ?? 1
    });
  } else {
    Object.assign(questionForm, emptyQuestion());
  }
  questionDialog.visible = true;
}

function addOption() {
  if (questionForm.options.length < 6) questionForm.options.push('');
}

function removeOption(i) {
  if (questionForm.options.length <= 3) return;
  questionForm.options.splice(i, 1);
  if (questionForm.answerIndex >= questionForm.options.length) {
    questionForm.answerIndex = questionForm.options.length - 1;
  }
}

async function saveQuestion() {
  if (!questionForm.title.trim()) {
    ElMessage.warning('题目必填');
    return;
  }
  const opts = questionForm.options.map((s) => String(s || '').trim()).filter(Boolean);
  if (opts.length < 3) {
    ElMessage.warning('至少需要 3 个有效选项');
    return;
  }
  if (questionForm.answerIndex < 0 || questionForm.answerIndex >= opts.length) {
    ElMessage.warning('请选择题目的正确答案');
    return;
  }
  saving.value = true;
  try {
    await wikiApi.saveQuestion({
      ...questionForm,
      title: questionForm.title.trim(),
      optionsJson: JSON.stringify(opts),
      answerIndex: Number(questionForm.answerIndex),
      analysis: (questionForm.analysis || '').trim()
    });
    ElMessage.success('已保存');
    questionDialog.visible = false;
    loadAll();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    saving.value = false;
  }
}

async function removeQuestion(row) {
  try {
    await ElMessageBox.confirm(`确认删除题目「${row.title}」？删除后不可恢复。`, '删除题目', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    });
  } catch (cancel) {
    return;
  }
  try {
    await wikiApi.deleteQuestion(row.id);
    ElMessage.success('已删除');
    loadAll();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

// ---------- 文章 ----------
function openArticleDialog(row) {
  if (row) {
    Object.assign(articleForm, {
      id: row.id,
      title: row.title || '',
      summary: row.summary || '',
      content: row.content || '',
      coverUrl: row.coverUrl || '',
      category: row.category || 'feeding',
      tags: row.tags || '',
      readMinutes: row.readMinutes ?? 3,
      status: row.status ?? 1,
      sort: row.sort ?? 0
    });
  } else {
    Object.assign(articleForm, emptyArticle());
  }
  articleDialog.visible = true;
}

async function saveArticle() {
  if (!articleForm.title.trim()) {
    ElMessage.warning('标题必填');
    return;
  }
  if (!articleForm.content.trim()) {
    ElMessage.warning('正文必填');
    return;
  }
  saving.value = true;
  try {
    await wikiApi.saveArticle({
      ...articleForm,
      title: articleForm.title.trim(),
      summary: (articleForm.summary || '').trim(),
      content: articleForm.content,
      tags: (articleForm.tags || '').trim(),
      readMinutes: Number(articleForm.readMinutes),
      sort: Number(articleForm.sort)
    });
    ElMessage.success('已保存');
    articleDialog.visible = false;
    loadAll();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    saving.value = false;
  }
}

async function removeArticle(row) {
  try {
    await ElMessageBox.confirm(`确认删除文章「${row.title}」？删除后不可恢复。`, '删除文章', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    });
  } catch (cancel) {
    return;
  }
  try {
    await wikiApi.deleteArticle(row.id);
    ElMessage.success('已删除');
    loadAll();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

onMounted(loadAll);
</script>

<style scoped>
.wiki-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.filter-action {
  margin-left: auto;
}

.option-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-key {
  width: 20px;
  flex-shrink: 0;
  font-weight: 600;
  color: var(--accent);
  text-align: center;
}

.option-hint {
  font-size: 12px;
}

.cover-preview-row {
  margin-top: 8px;
}

.cover-preview {
  width: 96px;
  height: 60px;
  border-radius: 6px;
  border: 0.5px solid var(--border);
  display: block;
}

.unit {
  margin-left: 8px;
  font-size: 12px;
}

.retry-tip {
  margin-bottom: 14px;
}
</style>
