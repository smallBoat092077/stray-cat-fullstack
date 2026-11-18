<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">首页内容</span>
      <div class="head-actions">
        <el-button size="small" @click="load">刷新</el-button>
        <el-button size="small" type="primary" @click="openDialog()">新增内容</el-button>
      </div>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="type" @change="onTypeChange">
        <el-radio-button value="HOTSPOT">热点轮播</el-radio-button>
        <el-radio-button value="NOTICE">系统通告</el-radio-button>
      </el-radio-group>
      <span class="muted">热点轮播与系统通告共用一张内容表，切换类型查看</span>
    </div>

    <el-table :data="rows" v-loading="loading" size="small" border empty-text="暂无内容">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
      <el-table-column label="角标" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.tag" size="small" type="warning">{{ row.tag }}</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image
            v-if="row.coverUrl"
            :src="imageUrl(row.coverUrl)"
            :preview-src-list="[imageUrl(row.coverUrl)]"
            :preview-teleported="true"
            fit="cover"
            class="thumb"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="跳转类型" width="100">
        <template #default="{ row }">{{ LINK_TYPE_TEXT[row.linkType] || row.linkType || '—' }}</template>
      </el-table-column>
      <el-table-column prop="linkUrl" label="跳转路径" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.linkUrl || '—' }}</template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column label="置顶" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.pinned === 1" size="small" type="success">置顶</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上线' : '下线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效时间窗" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ timeText(row) }}</template>
      </el-table-column>
      <el-table-column prop="pv" label="PV" width="70" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" @click="toggle(row)">
            {{ row.status === 1 ? '下线' : '上线' }}
          </el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="form.id ? '编辑内容' : '新增内容'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="热点轮播" value="HOTSPOT" />
            <el-option label="系统通告" value="NOTICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="80" placeholder="热点标题 / 通告标题" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="封面图">
          <div class="cover-row">
            <input ref="coverInput" type="file" accept="image/*" style="display:none" @change="onCoverSelected" />
            <el-button size="small" @click="$refs.coverInput.click()">上传封面</el-button>
            <img v-if="form.coverUrl" :src="imageUrl(form.coverUrl)" class="cover-preview" alt="封面" />
          </div>
        </el-form-item>
        <el-form-item label="角标">
          <el-input v-model="form.tag" maxlength="20" placeholder="如 热点 / 公告" />
        </el-form-item>
        <el-form-item label="跳转类型">
          <el-select v-model="form.linkType" style="width: 100%">
            <el-option label="活动 campaign" value="campaign" />
            <el-option label="猫咪 cat" value="cat" />
            <el-option label="站内页 page" value="page" />
            <el-option label="不跳转 none" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item label="跳转路径">
          <el-input
            v-model="form.linkUrl"
            :disabled="form.linkType === 'none'"
            placeholder="pages/campaign/index?code=winter2026"
          />
          <div class="muted form-hint">仅允许站内路径 pages/...，外链或其他格式会被拒绝</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="form.pinned" :active-value="1" :inactive-value="0" active-text="置顶" inactive-text="否" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上线" inactive-text="下线" />
        </el-form-item>
        <el-form-item label="生效时间窗">
          <div class="time-row">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="开始时间（空=长期）"
            />
            <span class="muted">~</span>
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="结束时间（空=长期）"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import http from '../api/http';
import { homeApi } from '../api';
import { formatTime, imageUrl } from '../utils/dict';

// 方案 5.2：linkUrl 过站内路径正则，拒绝外链
const LINK_PATTERN = /^pages\/[A-Za-z0-9_\/-]+(\?[A-Za-z0-9_=&-]+)?$/;
const LINK_TYPE_TEXT = { campaign: '活动', cat: '猫咪', page: '站内页', none: '不跳转' };

const type = ref('HOTSPOT');
const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const loading = ref(false);
const saving = ref(false);
const dialog = reactive({ visible: false });
const coverInput = ref(null);

const emptyForm = () => ({
  id: null,
  type: 'HOTSPOT',
  title: '',
  summary: '',
  coverUrl: '',
  tag: '',
  linkType: 'none',
  linkUrl: '',
  sortOrder: 0,
  pinned: 0,
  status: 1,
  startTime: '',
  endTime: ''
});
const form = reactive(emptyForm());

async function load() {
  loading.value = true;
  try {
    const page = await homeApi.contentList({ type: type.value, pageNum: pageNum.value, pageSize });
    rows.value = Array.isArray(page) ? page : (page?.records || []);
    total.value = Array.isArray(page) ? page.length : (page?.total || 0);
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    loading.value = false;
  }
}

function onTypeChange() {
  pageNum.value = 1;
  load();
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      type: row.type,
      title: row.title || '',
      summary: row.summary || '',
      coverUrl: row.coverUrl || '',
      tag: row.tag || '',
      linkType: row.linkType || 'none',
      linkUrl: row.linkUrl || '',
      sortOrder: row.sortOrder ?? 0,
      pinned: row.pinned ?? 0,
      status: row.status ?? 1,
      startTime: formatTime(row.startTime),
      endTime: formatTime(row.endTime)
    });
  } else {
    Object.assign(form, emptyForm(), { type: type.value });
  }
  dialog.visible = true;
}

function validateLinkUrl() {
  const url = (form.linkUrl || '').trim();
  if (form.linkType === 'none' || !url) {
    return true;
  }
  if (/^https?:\/\//i.test(url)) {
    ElMessage.error('跳转路径不支持外链，仅允许站内路径 pages/...');
    return false;
  }
  if (!LINK_PATTERN.test(url)) {
    ElMessage.error('跳转路径格式不合法，需为站内路径，如 pages/campaign/index?code=winter2026');
    return false;
  }
  return true;
}

async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('标题必填');
    return;
  }
  if (!validateLinkUrl()) {
    return;
  }
  saving.value = true;
  try {
    await homeApi.saveContent({
      ...form,
      linkUrl: form.linkType === 'none' ? '' : (form.linkUrl || '').trim(),
      startTime: form.startTime || null,
      endTime: form.endTime || null
    });
    ElMessage.success('已保存');
    dialog.visible = false;
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    saving.value = false;
  }
}

async function toggle(row) {
  try {
    await homeApi.setContentStatus(row.id, row.status === 1 ? 0 : 1);
    ElMessage.success(row.status === 1 ? '已下线' : '已上线');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除内容', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    });
  } catch (cancel) {
    return;
  }
  try {
    await homeApi.deleteContent(row.id);
    ElMessage.success('已删除');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

// 与 CampaignList.vue 一致：走通用上传接口，不会像 /poster/upload 那样多建一条海报记录
function onCoverSelected(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  e.target.value = '';
  const fd = new FormData();
  fd.append('file', file);
  http.post('/common/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    .then((path) => { form.coverUrl = path; })
    .catch(() => {});
}

function timeText(row) {
  const s = row.startTime ? formatTime(row.startTime) : '不限';
  const e = row.endTime ? formatTime(row.endTime) : '不限';
  return `${s} ~ ${e}`;
}

onMounted(load);
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
}

.cover-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover-preview {
  width: 96px;
  height: auto;
  border: 1px solid #e6e3da;
  border-radius: 4px;
  display: block;
}

.thumb {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  display: block;
}

.form-hint {
  font-size: 12px;
  line-height: 1.4;
  margin-top: 4px;
}

.time-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
