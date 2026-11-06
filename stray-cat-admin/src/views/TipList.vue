<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">打赏记录</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-select v-model="catId" clearable filterable placeholder="全部猫咪" style="width: 220px" @change="onFilterChange">
        <el-option v-for="cat in catOptions" :key="cat.id" :label="cat.name" :value="cat.id" />
      </el-select>
      <span class="muted">后台看到完整昵称，小程序公开列表里是脱敏的</span>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="猫咪" width="140">
        <template #default="{ row }">{{ row.catName || `#${row.catId}` }}</template>
      </el-table-column>
      <el-table-column label="打赏人" min-width="150">
        <template #default="{ row }">
          {{ row.nickname || '未知' }}
          <span class="muted">(#{{ row.fromUserId }})</span>
        </template>
      </el-table-column>
      <el-table-column label="猫粮" width="100">
        <template #default="{ row }">{{ row.coinAmount }} 份</template>
      </el-table-column>
      <el-table-column prop="message" label="留言" min-width="200" />
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
import { computed, onMounted, ref } from 'vue';
import { adminApi } from '../api';
import { catNameMap } from '../utils/lookups';
import { formatTime } from '../utils/dict';

const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const catId = ref(null);
const loading = ref(false);
const cats = ref({});

const catOptions = computed(() => Object.values(cats.value));

async function load() {
  loading.value = true;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (catId.value) {
      params.catId = catId.value;
    }
    const page = await adminApi.tips(params);
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

onMounted(async () => {
  cats.value = await catNameMap();
  load();
});
</script>
