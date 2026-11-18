<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">爱猫搬家审核</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="auditStatus" @change="onStatusChange">
        <el-radio-button :value="0">待审</el-radio-button>
        <el-radio-button :value="1">在架</el-radio-button>
        <el-radio-button :value="2">已转让</el-radio-button>
        <el-radio-button :value="3">已下架</el-radio-button>
        <el-radio-button :value="4">驳回</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <span class="muted">个人转让信息先审后发：仅审核通过的在架信息会在小程序转让列表展示</span>
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

    <el-table :data="rows" v-loading="loading" size="small" border empty-text="暂无转让信息">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="猫咪" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.catName || '—' }}</span>
          <div class="muted">{{ row.breed || '品种未填' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="发布人ID" width="100">
        <template #default="{ row }">#{{ row.userId }}</template>
      </el-table-column>
      <el-table-column label="转让原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || '—' }}</template>
      </el-table-column>
      <el-table-column label="照片" min-width="190">
        <template #default="{ row }">
          <div v-if="row.photoList && row.photoList.length" class="review-images">
            <el-image
              v-for="(img, i) in row.photoList"
              :key="`${img}-${i}`"
              :src="img"
              :preview-src-list="row.photoList"
              :initial-index="i"
              :preview-teleported="true"
              fit="cover"
              class="thumb"
            />
          </div>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="联系方式" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.contactName || '—' }}
          <span class="muted">/ {{ row.contactPhone || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="TRANSFER_STATUS[row.status]?.type || 'info'" size="small">
            {{ TRANSFER_STATUS[row.status]?.text || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="230" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button size="small" type="primary" @click="approve(row)">通过</el-button>
            <el-button size="small" type="danger" @click="reject(row)">驳回</el-button>
          </template>
          <template v-else>
            <div>审核人：{{ row.reviewerName || row.reviewer || '—' }}</div>
            <div class="muted">备注：{{ row.reviewRemark || '—' }}</div>
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
import { transferApi } from '../api';
import { formatTime, imageUrl } from '../utils/dict';

const TRANSFER_STATUS = {
  0: { text: '待审', type: 'warning' },
  1: { text: '在架', type: 'success' },
  2: { text: '已转让', type: 'success' },
  3: { text: '已下架', type: 'info' },
  4: { text: '驳回', type: 'danger' }
};

const auditStatus = ref(0);
const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const loading = ref(false);
const error = ref(false);

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (auditStatus.value !== '' && auditStatus.value !== null) {
      params.status = auditStatus.value;
    }
    const page = await transferApi.list(params);
    const list = Array.isArray(page) ? page : (page?.records || []);
    rows.value = list.map((item) => ({
      ...item,
      photoList: item.photos
        ? String(item.photos).split(',').map((s) => s.trim()).filter(Boolean).map(imageUrl)
        : []
    }));
    total.value = Array.isArray(page) ? page.length : (page?.total || 0);
  } catch (err) {
    error.value = true;
    // 提示由 http 拦截器统一给
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
      `确认通过「${row.catName}」的转让信息？通过后将在小程序转让列表展示。`,
      '审核通过',
      { type: 'info', confirmButtonText: '通过', cancelButtonText: '取消' }
    );
  } catch (cancel) {
    return;
  }
  try {
    await transferApi.audit(row.id, { status: 1, remark: '通过' });
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
      '驳回原因会展示给发布人，请填写具体原因',
      `驳回转让信息 · ${row.catName}`,
      {
        inputPlaceholder: '如：含疑似交易信息 / 照片不清晰',
        inputValue: '',
        inputValidator: (val) => (val && val.trim() ? true : '驳回原因不能为空')
      }
    );
    reason = value.trim();
  } catch (cancel) {
    return;
  }
  try {
    await transferApi.audit(row.id, { status: 4, remark: reason });
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

.retry-tip {
  margin-bottom: 14px;
}
</style>
