<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">花纹样本</span>
      <div class="head-actions">
        <el-button size="small" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="hint"
      title="三期口径：每只猫建议登记 3 张不同角度实拍（正面/左侧/右侧），主体占画面 1/3 以上，避免多猫重叠；基准图预览与删除仅对本次登记后的新样本生效，旧样本无原图。"
    />

    <el-alert
      v-if="stats.degraded"
      type="warning"
      :closable="false"
      show-icon
      class="degraded-alert"
      title="识别服务暂不可用，统计数据为兜底值"
    />

    <!-- 顶部统计卡 -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">总特征数</div>
        <div class="stat-value">{{ stats.totalFeatures ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已登记猫数</div>
        <div class="stat-value">{{ stats.totalCats ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">索引大小</div>
        <div class="stat-value">{{ stats.indexSize ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本地登记数</div>
        <div class="stat-value">{{ stats.localSamples ?? 0 }}</div>
      </div>
    </div>

    <!-- 灰度状态与阈值（可选展示） -->
    <div class="config-row">
      <span>
        灰度开关：
        <el-tag :type="config.enabled ? 'success' : 'info'" size="small">
          {{ config.enabled ? '已开启' : '未开启' }}
        </el-tag>
      </span>
      <span class="muted">
        匹配阈值 {{ config.matchThreshold ?? '-' }} · 人工确认阈值 {{ config.manualThreshold ?? '-' }} · topK {{ config.topK ?? '-' }}
      </span>
    </div>

    <!-- 登记表单 -->
    <h4 class="section-title">登记基准样本</h4>
    <div class="register-row">
      <el-select v-model="registerCatId" placeholder="选择猫咪" clearable filterable style="width: 220px">
        <el-option v-for="cat in cats" :key="cat.id" :label="cat.name" :value="cat.id" />
      </el-select>
      <input ref="photoInput" type="file" accept="image/*" style="display:none" @change="onPhotoSelected" />
      <el-button size="small" @click="$refs.photoInput.click()">选择基准图</el-button>
      <span class="muted photo-name">{{ photoName || '未选择图片' }}</span>
      <el-button size="small" type="primary" :loading="registering" @click="doRegister">登记样本</el-button>
    </div>

    <!-- 样本列表 -->
    <h4 class="section-title">已登记样本</h4>
    <div class="filter-row">
      <el-input
        v-model="filterCatId"
        placeholder="按猫咪 ID 筛选，留空查全部"
        clearable
        style="width: 240px"
        @keyup.enter="loadSamples"
      />
      <el-button size="small" @click="loadSamples">查询</el-button>
      <el-button size="small" @click="clearFilter">全部</el-button>
      <span class="muted">共 {{ rows.length }} 条</span>
    </div>

    <el-table :data="rows" v-loading="samplesLoading" size="small" border empty-text="暂无样本">
      <el-table-column prop="feature_id" label="特征 ID" width="100" />
      <el-table-column prop="cat_id" label="猫咪 ID" width="100" />
      <el-table-column label="基准图" min-width="140">
        <template #default="{ row }">
          <el-image
            v-if="resolveImage(row.image_url)"
            :src="resolveImage(row.image_url)"
            :preview-src-list="[resolveImage(row.image_url)]"
            :preview-teleported="true"
            fit="cover"
            class="thumb"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="登记时间" width="180">
        <template #default="{ row }">{{ formatTime(row.created_at) || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="danger" plain @click="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import http from '../api/http';
import { catApi, recognizerApi } from '../api';
import { formatTime } from '../utils/dict';

// 统计初值给全 0：即便 /stats 失败也能正常渲染，不白屏
const stats = ref({ totalFeatures: 0, totalCats: 0, indexSize: 0, localSamples: 0, degraded: false });
const config = ref({});
const cats = ref([]);
const rows = ref([]);
const samplesLoading = ref(false);
const registering = ref(false);
const registerCatId = ref(null);
const photoFile = ref(null);
const photoName = ref('');
const photoInput = ref(null);
const filterCatId = ref('');

// 与 LostCatReview.vue 的 resolveImage 一致：后端存 /uploads/... 相对路径，用 baseURL 拼完整地址
function resolveImage(path) {
  const p = (path || '').trim();
  if (!p) return '';
  if (/^https?:\/\//i.test(p)) return p;
  const base = http.defaults.baseURL || '';
  return base && !p.startsWith(base) ? `${base}${p}` : p;
}

// 所有 load 都遵循：仅成功时覆盖数据，失败保留已有值（错误由 http.js 拦截器统一 ElMessage.error 提示），避免白屏
async function loadStats() {
  try {
    const data = await recognizerApi.stats();
    if (data && typeof data === 'object') {
      stats.value = { ...data };
    }
  } catch (err) {
    // 提示由 http 拦截器统一给；保留已有统计
  }
}

async function loadConfig() {
  try {
    const data = await recognizerApi.config();
    if (data && typeof data === 'object') {
      config.value = data;
    }
  } catch (err) {
    // config 为可选展示，失败静默降级，不阻断页面
  }
}

async function loadCats() {
  try {
    const page = await catApi.list({ pageNum: 1, pageSize: 100 });
    cats.value = Array.isArray(page) ? page : (page?.records || []);
  } catch (err) {
    // 提示由 http 拦截器统一给；保留已有下拉数据
  }
}

async function loadSamples() {
  samplesLoading.value = true;
  try {
    const res = await recognizerApi.samples(filterCatId.value);
    rows.value = Array.isArray(res) ? res : (res?.records || []);
  } catch (err) {
    // 提示由 http 拦截器统一给；保留已有列表，不清空
  } finally {
    samplesLoading.value = false;
  }
}

function clearFilter() {
  filterCatId.value = '';
  loadSamples();
}

// 照 CampaignList.vue：先只留存 File，等点「登记样本」再手动组 FormData 提交（register 需要 catId，不能选图即传）
function onPhotoSelected(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  e.target.value = '';
  photoFile.value = file;
  photoName.value = file.name;
}

async function doRegister() {
  if (!registerCatId.value) {
    ElMessage.warning('请先选择猫咪');
    return;
  }
  if (!photoFile.value) {
    ElMessage.warning('请先选择基准图');
    return;
  }
  registering.value = true;
  try {
    const result = await recognizerApi.register(registerCatId.value, photoFile.value);
    ElMessage.success(result?.message || '登记成功');
    // 清空选择
    registerCatId.value = null;
    photoFile.value = null;
    photoName.value = '';
    // 成功后刷新统计与列表
    loadStats();
    loadSamples();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    registering.value = false;
  }
}

// 三期：删除基准样本（Python 删行 + 重建索引），二次确认防误删
async function doDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除猫咪 ${row.cat_id} 的特征样本 #${row.feature_id}？删除后立即从识别索引移除。`,
      '删除样本',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    );
  } catch (e) {
    return; // 用户取消
  }
  try {
    const result = await recognizerApi.removeSample(row.feature_id);
    ElMessage.success(result?.message || '删除成功');
    loadStats();
    loadSamples();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

function loadAll() {
  loadStats();
  loadConfig();
  loadSamples();
}

onMounted(() => {
  loadCats();
  loadAll();
});
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
}

.hint {
  margin-bottom: 16px;
}

.degraded-alert {
  margin-bottom: 16px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  border: 1px solid #e6e3da;
  border-radius: 6px;
  padding: 14px 16px;
  background: #fff;
}

.stat-label {
  font-size: 12px;
  color: #7b8a84;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #14493c;
}

.config-row {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  margin-bottom: 8px;
}

.section-title {
  margin: 18px 0 10px;
  font-size: 13px;
  color: #7b8a84;
}

.register-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.photo-name {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.muted {
  color: #7b8a84;
}

.thumb {
  width: 56px;
  height: 56px;
  border-radius: 4px;
  display: block;
}
</style>
