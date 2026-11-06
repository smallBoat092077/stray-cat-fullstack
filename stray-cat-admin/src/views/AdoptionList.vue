<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">领养申请审核</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="status" @change="onFilterChange">
        <el-radio-button :label="0">待审核</el-radio-button>
        <el-radio-button :label="2">已通过</el-radio-button>
        <el-radio-button :label="3">已拒绝</el-radio-button>
        <el-radio-button :label="null">全部</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="rows" v-loading="loading" row-key="id" border>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-descriptions :column="2" border size="small" class="detail">
            <el-descriptions-item label="养猫经验">{{ row.catExperience || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="领养原因">{{ row.adoptionReason || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="工作时间">{{ row.workSchedule || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="家人态度">{{ row.familyAgree ? '同意' : '未确认' }}</el-descriptions-item>
            <el-descriptions-item label="其他宠物">{{ row.hasOtherPets ? '有' : '无' }}</el-descriptions-item>
            <el-descriptions-item label="住房">{{ housingText(row) }}</el-descriptions-item>
            <el-descriptions-item label="微信号">{{ row.contactWechat || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="审核意见">{{ row.reviewComment || '—' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="猫咪" width="140">
        <template #default="{ row }">{{ catName(row.catId) }}</template>
      </el-table-column>
      <el-table-column label="申请人" min-width="140">
        <template #default="{ row }">
          {{ userName(row.applicantId) }}
          <span class="muted">(#{{ row.applicantId }})</span>
        </template>
      </el-table-column>
      <el-table-column label="联系方式" width="200">
        <template #default="{ row }">
          <el-link v-if="row.contactPhone" type="primary" :href="'tel:' + row.contactPhone">
            {{ row.contactPhone }}
          </el-link>
          <span v-else class="muted">未填写</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="dictType(ADOPTION_STATUS_MAP, row.status)">
            {{ dictText(ADOPTION_STATUS_MAP, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 0 || row.status === 1"
            size="small"
            type="primary"
            @click="openReview(row)"
          >
            审核
          </el-button>
          <span v-else class="muted">已处理</span>
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

    <el-dialog v-model="reviewVisible" title="审核领养申请" width="460px">
      <el-descriptions v-if="current" :column="1" size="small" border>
        <el-descriptions-item label="猫咪 / 申请人">
          {{ catName(current.catId) }} / {{ userName(current.applicantId) }}
        </el-descriptions-item>
        <el-descriptions-item label="联系方式">
          {{ current.contactPhone || '未填写' }}
          <span v-if="current.contactWechat"> · 微信 {{ current.contactWechat }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-position="top" class="review-form">
        <el-form-item label="审核结果">
          <el-radio-group v-model="form.status">
            <el-radio :label="2">通过</el-radio>
            <el-radio :label="3">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.status === 3 ? '拒绝理由（申请人可见）' : '审核备注'">
          <el-input v-model="form.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { adoptionApi } from '../api';
import { catNameMap, userNameMap, invalidateLookups } from '../utils/lookups';
import { ADOPTION_STATUS_MAP, HOUSING_MAP, dictText, dictType, formatTime } from '../utils/dict';

const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const status = ref(0);
const loading = ref(false);
const cats = ref({});
const users = ref({});

const reviewVisible = ref(false);
const submitting = ref(false);
const current = ref(null);
const form = reactive({ status: 2, comment: '' });

function catName(id) {
  return cats.value[id] ? cats.value[id].name : `#${id}`;
}

function userName(id) {
  const user = users.value[id];
  return user ? user.nickname || user.username : `用户${id}`;
}

function housingText(row) {
  return `${HOUSING_MAP[row.housingType] || '未填写'}${row.housingArea ? ` · ${row.housingArea}㎡` : ''}`;
}

async function loadLookups() {
  cats.value = await catNameMap();
  users.value = await userNameMap();
}

async function load() {
  loading.value = true;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (status.value !== null) {
      params.status = status.value;
    }
    const page = await adoptionApi.list(params);
    rows.value = page.records || [];
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

function openReview(row) {
  current.value = row;
  form.status = 2;
  form.comment = '';
  reviewVisible.value = true;
}

async function submit() {
  if (form.status === 3 && !form.comment.trim()) {
    ElMessage.warning('拒绝必须写理由');
    return;
  }
  submitting.value = true;
  try {
    await adoptionApi.review(current.value.id, form.status, form.comment.trim());
    ElMessage.success('审核已提交');
    reviewVisible.value = false;
    invalidateLookups();
    await loadLookups();
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await loadLookups();
  load();
});
</script>

<style scoped>
.detail {
  margin: 8px 16px;
}

.review-form {
  margin-top: 16px;
}
</style>
