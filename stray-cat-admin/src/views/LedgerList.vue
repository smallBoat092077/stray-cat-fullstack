<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">猫粮流水</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-input v-model="userId" placeholder="按用户 ID 筛选" clearable style="width: 170px" @keyup.enter="onFilterChange" />
      <el-select v-model="bizType" clearable placeholder="全部行为" style="width: 170px" @change="onFilterChange">
        <el-option v-for="(text, key) in BIZ_TYPE_MAP" :key="key" :label="text" :value="key" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置</el-button>
      <span class="muted">猫粮是站内虚拟积分，无现金价值</span>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="用户" min-width="160">
        <template #default="{ row }">
          {{ row.nickname || '用户已注销' }}
          <span class="muted">(#{{ row.userId }})</span>
        </template>
      </el-table-column>
      <el-table-column label="行为" width="130">
        <template #default="{ row }">
          <el-tag size="small">{{ BIZ_TYPE_MAP[row.bizType] || row.bizType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="变动" width="100">
        <template #default="{ row }">
          <span :class="row.amount >= 0 ? 'plus' : 'minus'">{{ row.amount >= 0 ? '+' : '' }}{{ row.amount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="balanceAfter" label="变动后余额" width="110" />
      <el-table-column label="关联业务" width="140">
        <template #default="{ row }">
          <router-link v-if="row.bizType === 'CHECKIN' && row.bizId" to="/tasks" class="link">
            任务 #{{ row.bizId }}
          </router-link>
          <span v-else>{{ row.bizId ?? '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
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
import { useRoute } from 'vue-router';
import { adminApi } from '../api';
import { BIZ_TYPE_MAP, formatTime } from '../utils/dict';

const route = useRoute();
const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const userId = ref(route.query.userId ? String(route.query.userId) : '');
const bizType = ref(null);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (userId.value.trim()) {
      params.userId = userId.value.trim();
    }
    if (bizType.value) {
      params.bizType = bizType.value;
    }
    const page = await adminApi.ledger(params);
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

function resetFilters() {
  userId.value = '';
  bizType.value = null;
  onFilterChange();
}

onMounted(load);
</script>

<style scoped>
.plus {
  color: #14493c;
}

.minus {
  color: #a8453b;
}

.link {
  color: #14493c;
}
</style>
