<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">志愿者申请审核</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="auditStatus" @change="onStatusChange">
        <el-radio-button :value="0">待审 {{ counts.pending || 0 }}</el-radio-button>
        <el-radio-button :value="1">已通过 {{ counts.approved || 0 }}</el-radio-button>
        <el-radio-button :value="2">已拒绝 {{ counts.rejected || 0 }}</el-radio-button>
      </el-radio-group>
      <span class="muted">审核通过后申请人将自动升级为志愿者角色，并收到站内消息通知</span>
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
        <el-button size="small" type="danger" plain @click="load">重试</el-button>
      </template>
    </el-alert>

    <el-table :data="rows" v-loading="loading" size="small" border empty-text="暂无志愿者申请">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="申请人" min-width="120">
        <template #default="{ row }">
          <span>{{ row.realName || '—' }}</span>
          <div class="muted">{{ row.applicantNickname ? '昵称：' + row.applicantNickname : '#用户' + row.applicantId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="联系方式" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.contact || '—' }}</template>
      </el-table-column>
      <el-table-column label="所在区域" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.region || '—' }}</template>
      </el-table-column>
      <el-table-column label="可服务时间" width="110">
        <template #default="{ row }">{{ row.serviceTime || '—' }}</template>
      </el-table-column>
      <el-table-column label="申请理由" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="APP_STATUS[row.status]?.type || 'info'" size="small">
            {{ APP_STATUS[row.status]?.text || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button size="small" type="primary" @click="approve(row)">通过</el-button>
            <el-button size="small" type="danger" @click="reject(row)">拒绝</el-button>
          </template>
          <template v-else>
            <div>审核人：#{{ row.reviewerId || '—' }}</div>
            <div class="muted">意见：{{ row.reviewComment || '—' }}</div>
          </template>
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { volunteerApi } from '../api';
import { formatTime } from '../utils/dict';

const APP_STATUS = {
  0: { text: '待审', type: 'warning' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已拒绝', type: 'danger' }
};

const auditStatus = ref(0);
const rows = ref([]);
const counts = ref({});
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const loading = ref(false);
const error = ref(false);

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const params = { pageNum: pageNum.value, pageSize, status: auditStatus.value };
    const [page, c] = await Promise.all([volunteerApi.list(params), volunteerApi.counts()]);
    rows.value = page?.records || [];
    total.value = page?.total || 0;
    counts.value = c || {};
  } catch (err) {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

function onStatusChange() {
  pageNum.value = 1;
  load();
}

async function approve(row) {
  try {
    await ElMessageBox.confirm(
      `确认通过「${row.realName}」的志愿者申请？通过后该用户将自动升级为志愿者角色。`,
      '审核通过',
      { type: 'info', confirmButtonText: '通过', cancelButtonText: '取消' }
    );
  } catch (cancel) {
    return;
  }
  try {
    await volunteerApi.review(row.id, 1, '');
    ElMessage.success('已通过，用户角色已升级为志愿者');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function reject(row) {
  let reason;
  try {
    const { value } = await ElMessageBox.prompt(
      '拒绝原因会展示给申请人，请填写具体原因',
      `拒绝申请 · ${row.realName}`,
      {
        inputPlaceholder: '如：资料不完整 / 暂不需要志愿者',
        inputValue: '',
        inputValidator: (val) => (val && val.trim() ? true : '拒绝原因不能为空')
      }
    );
    reason = value.trim();
  } catch (cancel) {
    return;
  }
  try {
    await volunteerApi.review(row.id, 2, reason);
    ElMessage.success('已拒绝');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

onMounted(load);
</script>

<style scoped>
.retry-tip {
  margin-bottom: 14px;
}
</style>
