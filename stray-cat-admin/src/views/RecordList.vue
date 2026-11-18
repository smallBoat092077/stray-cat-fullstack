<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">投喂打卡审核</span>
      <button class="btn-outline btn-sm" @click="load">刷新</button>
    </div>

    <div class="filter-row">
      <div class="radio-group">
        <button class="radio-btn" :class="{ active: status === 0 }" @click="setStatus(0)">待审核</button>
        <button class="radio-btn" :class="{ active: status === 1 }" @click="setStatus(1)">已通过</button>
        <button class="radio-btn" :class="{ active: status === 2 }" @click="setStatus(2)">已驳回</button>
        <button class="radio-btn" :class="{ active: status === null }" @click="setStatus(null)">全部</button>
      </div>
      <span class="muted">最多取 50 条，按打卡时间倒序</span>
    </div>

    <div class="table-wrap">
      <table class="proto-table" v-loading="loading">
        <thead>
          <tr>
            <th>ID</th>
            <th>猫咪</th>
            <th>地点</th>
            <th>志愿者</th>
            <th>打卡时间</th>
            <th>距离</th>
            <th>猫粮</th>
            <th>照片</th>
            <th>状态</th>
            <th>审核意见</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>#{{ row.id }}</td>
            <td>{{ row.catName || '#' + row.catId }}</td>
            <td>{{ row.locationDesc || '—' }}</td>
            <td>{{ row.volunteerName || '—' }}</td>
            <td>{{ formatTime(row.checkInTime) }}</td>
            <td>{{ row.distance === null ? '—' : row.distance + ' m' }}</td>
            <td>{{ row.foodAmount ? row.foodAmount + ' 份' : (row.foodUsed || '自备') }}</td>
            <td>
              <div class="photo-row" v-if="row.images && row.images.length">
                <img
                  v-for="img in row.images"
                  :key="img"
                  class="photo-thumb"
                  :src="imageUrl(img)"
                  alt="打卡照片"
                  @click="preview(row.images, img)"
                />
              </div>
              <span v-else class="no-photo">无照片</span>
            </td>
            <td>
              <span class="tag" :class="statusTagClass(row.status)">{{ dictText(RECORD_STATUS_MAP, row.status) }}</span>
            </td>
            <td>{{ row.reviewComment || '—' }}</td>
            <td>
              <template v-if="row.status === 0">
                <button class="btn-sm btn-success" @click="review(row, 1)">通过</button>
                <button class="btn-sm btn-danger" @click="review(row, 2)">驳回</button>
              </template>
              <span v-else class="muted">已处理</span>
            </td>
          </tr>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="11" class="empty-msg">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="dialog-backdrop" :class="{ open: rejectVisible }" @click.self="rejectVisible = false">
      <div class="glass-dialog">
        <div class="dialog-title">驳回理由</div>
        <el-input v-model="rejectComment" type="textarea" :rows="3" placeholder="写一句理由，志愿者能看到" />
        <div class="dialog-footer">
          <button class="btn-outline btn-sm" @click="rejectVisible = false">取消</button>
          <button class="btn-sm btn-danger" :disabled="submitting" @click="submitReject">确认驳回</button>
        </div>
      </div>
    </div>

    <el-image-viewer v-if="viewerUrl" :url-list="viewerList" :initial-index="viewerIndex" @close="viewerUrl = ''" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { feedingApi } from '../api';
import { RECORD_STATUS_MAP, dictText, formatTime, imageUrl } from '../utils/dict';

const rows = ref([]);
const status = ref(0);
const loading = ref(false);
const rejectVisible = ref(false);
const rejectComment = ref('');
const target = ref(null);
const submitting = ref(false);

const viewerUrl = ref('');
const viewerList = ref([]);
const viewerIndex = ref(0);

function setStatus(s) {
  status.value = s;
  load();
}

async function load() {
  loading.value = true;
  try {
    const params = { limit: 50 };
    if (status.value !== null) {
      params.status = status.value;
    }
    rows.value = (await feedingApi.recordList(params)) || [];
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    loading.value = false;
  }
}

async function review(row, result) {
  if (result === 2) {
    target.value = row;
    rejectComment.value = '';
    rejectVisible.value = true;
    return;
  }
  try {
    await ElMessageBox.confirm(
      `通过后这条打卡记录会在猫咪 ${row.catName || row.catId} 的详情页公开，任务转「已完成」，志愿者信用积分 +1。`,
      '确认通过',
      { type: 'success' }
    );
  } catch (err) {
    return;
  }
  await submitReview(row.id, 1, '');
}

async function submitReject() {
  if (!rejectComment.value.trim()) {
    ElMessage.warning('驳回必须写理由');
    return;
  }
  await submitReview(target.value.id, 2, rejectComment.value.trim());
  rejectVisible.value = false;
}

async function submitReview(id, result, comment) {
  submitting.value = true;
  try {
    await feedingApi.reviewRecord(id, result, comment);
    ElMessage.success('审核已提交');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    submitting.value = false;
  }
}

function preview(images, current) {
  viewerList.value = images.map((path) => imageUrl(path));
  viewerIndex.value = images.indexOf(current);
  viewerUrl.value = imageUrl(current);
}

function statusTagClass(statusVal) {
  if (statusVal === 1) return 'tag-success';
  if (statusVal === 2) return 'tag-danger';
  return 'tag-warning';
}

onMounted(load);
</script>

<style scoped>
.page { padding: 28px 32px 40px; position: relative; z-index: 1; }

.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.page-title { font-size: 22px; font-weight: 600; color: var(--fg); letter-spacing: -0.02em; }
.muted { color: var(--fg-muted); font-size: 13px; }

.filter-row { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.radio-group { display: flex; gap: 4px; }
.radio-btn {
  padding: 5px 14px; border-radius: 20px; font-size: 12px;
  color: var(--fg-muted); cursor: pointer;
  border: 0.5px solid var(--border); background: none;
  transition: all 0.2s;
}
.radio-btn:hover { color: var(--fg); background: var(--glass-hi); }
.radio-btn.active { background: var(--accent); color: white; border-color: var(--accent); }

.table-wrap {
  background: var(--glass);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 0.5px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
}

.proto-table { width: 100%; border-collapse: separate; border-spacing: 0; font-size: 13px; }
.proto-table th { text-align: left; padding: 10px 14px; font-size: 12px; color: var(--fg-muted); font-weight: 500; border-bottom: 0.5px solid var(--border); }
.proto-table td { padding: 12px 14px; border-bottom: 0.5px solid var(--border); vertical-align: middle; }
.proto-table tr:last-child td { border-bottom: none; }
.proto-table tr:hover td { background: var(--glass-hi); }

.empty-msg { text-align: center; padding: 32px; color: var(--fg-muted); }

.tag { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 20px; font-size: 11px; font-weight: 500; }
.tag-warning  { background: rgba(251,191,36,0.15); color: var(--warning, #fbbf24); }
.tag-success { background: rgba(52,211,153,0.15); color: var(--success, #34d399); }
.tag-danger  { background: rgba(248,113,113,0.15); color: var(--danger, #f87171); }

.photo-row { display: flex; gap: 6px; }
.photo-thumb { width: 48px; height: 48px; border-radius: 8px; object-fit: cover; border: 0.5px solid var(--border); cursor: pointer; transition: transform 0.15s; }
.photo-thumb:hover { transform: scale(1.08); }
.no-photo { color: var(--fg-muted); font-size: 12px; }

.btn-sm { padding: 5px 12px; border-radius: 8px; font-size: 12px; cursor: pointer; border: none; transition: all 0.2s; font-weight: 500; }
.btn-outline { background: var(--glass); color: var(--fg); border: 0.5px solid var(--border); padding: 5px 14px; border-radius: 8px; font-size: 13px; cursor: pointer; transition: all 0.2s; }
.btn-outline:hover { background: var(--glass-hi); }
.btn-success { background: rgba(52,211,153,0.15); color: var(--success, #34d399); }
.btn-success:hover { background: rgba(52,211,153,0.25); }
.btn-danger { background: rgba(248,113,113,0.15); color: var(--danger, #f87171); }
.btn-danger:hover { background: rgba(248,113,113,0.25); }

/* Dialog */
.dialog-backdrop {
  position: fixed; inset: 0; z-index: 200;
  background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center;
  opacity: 0; pointer-events: none;
  transition: opacity 0.25s ease;
}
.dialog-backdrop.open { opacity: 1; pointer-events: auto; }

.glass-dialog {
  background: var(--glass);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 0.5px solid var(--border);
  border-radius: 16px;
  box-shadow: var(--shadow);
  padding: 24px;
  width: 400px;
  max-width: 90vw;
  transition: transform 0.25s ease;
}
.dialog-backdrop.open .glass-dialog { transform: translateY(0); }
.dialog-title { font-size: 16px; font-weight: 600; color: var(--fg); margin-bottom: 16px; letter-spacing: -0.01em; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }

/* VLoading overlay inside table-wrap */
:deep(.el-loading-mask) {
  background: rgba(0,0,0,0.25) !important;
  border-radius: 14px;
}
</style>
