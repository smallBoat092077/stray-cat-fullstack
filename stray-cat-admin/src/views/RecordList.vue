<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">投喂打卡审核</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="status" @change="load">
        <el-radio-button :label="0">待审核</el-radio-button>
        <el-radio-button :label="1">已通过</el-radio-button>
        <el-radio-button :label="2">已驳回</el-radio-button>
        <el-radio-button :label="null">全部</el-radio-button>
      </el-radio-group>
      <span class="muted">最多取 50 条，按打卡时间倒序</span>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="猫咪" width="130">
        <template #default="{ row }">{{ row.catName || `#${row.catId}` }}</template>
      </el-table-column>
      <el-table-column label="地点" min-width="150">
        <template #default="{ row }">{{ row.locationDesc || '—' }}</template>
      </el-table-column>
      <el-table-column label="志愿者" width="140">
        <template #default="{ row }">{{ row.volunteerName || '—' }}</template>
      </el-table-column>
      <el-table-column label="打卡时间" width="170">
        <template #default="{ row }">{{ formatTime(row.checkInTime) }}</template>
      </el-table-column>
      <el-table-column label="距离" width="90">
        <template #default="{ row }">{{ row.distance === null ? '—' : row.distance + ' 米' }}</template>
      </el-table-column>
      <el-table-column label="猫粮" width="90">
        <template #default="{ row }">{{ row.foodAmount ? row.foodAmount + ' 份' : (row.foodUsed || '自备') }}</template>
      </el-table-column>
      <el-table-column label="现场照片" width="230">
        <template #default="{ row }">
          <div class="cell-images" v-if="row.images && row.images.length">
            <img
              v-for="img in row.images"
              :key="img"
              :src="imageUrl(img)"
              alt="打卡照片"
              @click="preview(row.images, img)"
            />
          </div>
          <span v-else class="muted">无照片</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="dictType(RECORD_STATUS_MAP, row.status)">
            {{ dictText(RECORD_STATUS_MAP, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核意见" min-width="150">
        <template #default="{ row }">{{ row.reviewComment || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button size="small" type="success" @click="review(row, 1)">通过</el-button>
            <el-button size="small" type="danger" @click="review(row, 2)">驳回</el-button>
          </template>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="rejectVisible" title="驳回理由" width="420px">
      <el-input v-model="rejectComment" type="textarea" :rows="3" placeholder="写一句理由，志愿者能看到" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-image-viewer v-if="viewerUrl" :url-list="viewerList" :initial-index="viewerIndex" @close="viewerUrl = ''" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { feedingApi } from '../api';
import { RECORD_STATUS_MAP, dictText, dictType, formatTime, imageUrl } from '../utils/dict';

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

onMounted(load);
</script>
