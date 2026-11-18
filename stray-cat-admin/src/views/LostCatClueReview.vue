<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">线索审核</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="auditStatus" @change="load">
        <el-radio-button :value="0">待审</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <span class="muted">先审后发：审核通过的线索才会通知启事发布者，驳回仅回填原因</span>
    </div>

    <el-table :data="rows" v-loading="loading" size="small" border empty-text="暂无线索">
      <el-table-column label="关联启事" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.catName || '—' }}</template>
      </el-table-column>
      <el-table-column label="发现时间" width="170">
        <template #default="{ row }">{{ formatTime(row.foundAt) || '—' }}</template>
      </el-table-column>
      <el-table-column label="发现地点" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.addressDesc || '—' }}</template>
      </el-table-column>
      <el-table-column label="照片" min-width="180">
        <template #default="{ row }">
          <div v-if="row.imageList && row.imageList.length" class="review-images">
            <el-image
              v-for="img in row.imageList"
              :key="img"
              :src="img"
              :preview-src-list="row.imageList"
              :preview-teleported="true"
              fit="cover"
              class="thumb"
            />
          </div>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '—' }}</template>
      </el-table-column>
      <el-table-column label="上报人" width="150">
        <template #default="{ row }">
          {{ row.nickname || row.userName || '未知' }}
          <span class="muted">(#{{ row.reporterId }})</span>
        </template>
      </el-table-column>
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="AUDIT_STATUS[row.auditStatus]?.type || 'info'" size="small">
            {{ AUDIT_STATUS[row.auditStatus]?.text || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="驳回原因" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.auditRemark || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.auditStatus === 0" size="small" type="primary" @click="approve(row)">通过</el-button>
          <el-button v-if="row.auditStatus === 0" size="small" type="danger" @click="reject(row)">驳回</el-button>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import http from '../api/http';
import { lostCatClueApi } from '../api';
import { formatTime } from '../utils/dict';

const AUDIT_STATUS = {
  0: { text: '待审', type: 'warning' },
  1: { text: '通过', type: 'success' },
  2: { text: '驳回', type: 'danger' }
};

const auditStatus = ref(0);
const rows = ref([]);
const loading = ref(false);

// 后端存的是 /uploads/... 相对路径，用 http.defaults.baseURL（/api）拼成静态资源完整地址
function resolveImage(path) {
  const p = (path || '').trim();
  if (!p) return '';
  if (/^https?:\/\//i.test(p)) return p;
  const base = http.defaults.baseURL || '';
  return base && !p.startsWith(base) ? `${base}${p}` : p;
}

async function load() {
  loading.value = true;
  try {
    const params = {};
    if (auditStatus.value !== null && auditStatus.value !== '') {
      params.auditStatus = auditStatus.value;
    }
    const res = await lostCatClueApi.list(params);
    const list = Array.isArray(res) ? res : (res?.records || []);
    rows.value = list.map((item) => ({
      ...item,
      imageList: item.images
        ? (Array.isArray(item.images) ? item.images : String(item.images).split(','))
            .filter(Boolean).map(resolveImage).filter(Boolean)
        : []
    }));
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    loading.value = false;
  }
}

async function approve(row) {
  try {
    await ElMessageBox.confirm(
      `确认通过关于「${row.catName || '该启事'}」的线索？通过后将通知启事发布者。`,
      '审核通过',
      { type: 'info', confirmButtonText: '通过', cancelButtonText: '取消' }
    );
  } catch (cancel) {
    return;
  }
  try {
    await lostCatClueApi.audit(row.id, { auditStatus: 1 });
    ElMessage.success('已通过');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function reject(row) {
  let reason;
  try {
    const { value } = await ElMessageBox.prompt(
      '驳回原因会展示给上报人，请填写具体原因',
      `驳回线索 · ${row.catName || '该启事'}`,
      {
        inputPlaceholder: '如：照片不清晰 / 描述与启事不符',
        inputValue: '',
        inputValidator: (val) => (val && val.trim() ? true : '驳回原因不能为空')
      }
    );
    reason = value.trim();
  } catch (err) {
    return;
  }
  try {
    await lostCatClueApi.audit(row.id, { auditStatus: 2, auditRemark: reason });
    ElMessage.success('已驳回');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

onMounted(load);
</script>

<style scoped>
.review-images {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.thumb {
  width: 56px;
  height: 56px;
  border-radius: 4px;
  display: block;
}
</style>
