<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">用户反馈</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-select v-model="status" clearable placeholder="全部状态" style="width: 160px" @change="onFilterChange">
        <el-option label="待处理" :value="0" />
        <el-option label="已回复" :value="1" />
        <el-option label="已忽略" :value="2" />
      </el-select>
      <span class="muted">待处理的排在最前</span>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeText(row.type) }}</template>
      </el-table-column>
      <el-table-column label="内容" min-width="260">
        <template #default="{ row }">
          <div class="content-cell">{{ row.content }}</div>
          <div v-if="row.imageList && row.imageList.length" class="img-row">
            <el-image
              v-for="img in row.imageList"
              :key="img"
              :src="img"
              :preview-src-list="row.imageList"
              fit="cover"
              class="thumb"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column label="提交人" width="150">
        <template #default="{ row }">
          {{ row.nickname || '未知' }}
          <span class="muted">(#{{ row.userId }})</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning" size="small">待处理</el-tag>
          <el-tag v-else-if="row.status === 1" type="success" size="small">已回复</el-tag>
          <el-tag v-else type="info" size="small">已忽略</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="回复" min-width="200">
        <template #default="{ row }">
          <span v-if="row.reply">{{ row.reply }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="openReply(row)">回复</el-button>
          <el-button v-if="row.status !== 2" size="small" @click="ignore(row)">忽略</el-button>
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

    <el-dialog v-model="replyVisible" title="回复反馈" width="520px">
      <div class="reply-origin">{{ replying?.content }}</div>
      <el-input
        v-model="replyText"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="回复会展示在小程序的反馈页，并通知到对方的消息中心"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="confirmReply">发送回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { feedbackApi } from '../api';
import { formatTime } from '../utils/dict';

const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const status = ref(null);
const loading = ref(false);

const replyVisible = ref(false);
const replying = ref(null);
const replyText = ref('');
const handling = ref(false);

const TYPE_TEXT = ['功能建议', '问题反馈', '其他'];

function typeText(type) {
  return TYPE_TEXT[type] || '其他';
}

async function load() {
  loading.value = true;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (status.value !== null && status.value !== '') {
      params.status = status.value;
    }
    const page = await feedbackApi.list(params);
    rows.value = (page.records || []).map((item) => ({
      ...item,
      imageList: item.images
        ? item.images.split(',').filter(Boolean).map((p) => `/api${p}`)
        : []
    }));
    total.value = page.total || 0;
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    loading.value = false;
  }
}

function onFilterChange() {
  pageNum.value = 1;
  load();
}

function openReply(row) {
  replying.value = row;
  replyText.value = row.reply || '';
  replyVisible.value = true;
}

async function confirmReply() {
  if (!replyText.value.trim()) {
    ElMessage.warning('回复内容不能为空');
    return;
  }
  handling.value = true;
  try {
    await feedbackApi.handle(replying.value.id, 1, replyText.value.trim());
    ElMessage.success('已回复，用户会收到站内通知');
    replyVisible.value = false;
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    handling.value = false;
  }
}

async function ignore(row) {
  try {
    await ElMessageBox.confirm('忽略后该反馈标记为已忽略，不会通知用户。确认忽略？', '忽略反馈', {
      type: 'warning',
      confirmButtonText: '忽略',
      cancelButtonText: '取消'
    });
  } catch (cancel) {
    return;
  }
  try {
    await feedbackApi.handle(row.id, 2, '');
    ElMessage.success('已忽略');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

onMounted(load);
</script>

<style scoped>
.content-cell {
  white-space: pre-wrap;
  word-break: break-all;
}

.img-row {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.thumb {
  width: 56px;
  height: 56px;
  border-radius: 4px;
  display: block;
}

.reply-origin {
  background: #f5f7f6;
  border-left: 3px solid #14493c;
  padding: 8px 12px;
  border-radius: 4px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #3a362e;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
