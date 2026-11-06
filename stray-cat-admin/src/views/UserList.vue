<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">用户与角色</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-input v-model="keyword" placeholder="昵称或账号" clearable style="width: 220px" @keyup.enter="onFilterChange" />
      <el-select v-model="role" clearable placeholder="全部角色" style="width: 150px" @change="onFilterChange">
        <el-option v-for="key in [0, 1, 2, 3]" :key="key" :label="dictText(ROLE_MAP, key)" :value="key" />
      </el-select>
      <el-button type="primary" size="small" @click="onFilterChange">搜索</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="昵称" min-width="140">
        <template #default="{ row }">{{ row.nickname || '未设置' }}</template>
      </el-table-column>
      <el-table-column label="账号" min-width="130">
        <template #default="{ row }">{{ row.username || '微信登录' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="dictType(ROLE_MAP, row.role)">{{ dictText(ROLE_MAP, row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="信用积分" width="100">
        <template #default="{ row }">{{ row.creditScore ?? 100 }}</template>
      </el-table-column>
      <el-table-column label="猫粮余额" width="100">
        <template #default="{ row }">{{ row.coinBalance ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span :class="{ muted: row.status === 0 }">{{ row.status === 0 ? '已禁用' : '正常' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-select
            :model-value="row.role"
            size="small"
            style="width: 110px"
            @change="(value) => grantRole(row, value)"
          >
            <el-option v-for="key in GRANTABLE_ROLES" :key="key" :label="dictText(ROLE_MAP, key)" :value="key" />
          </el-select>
          <el-button size="small" @click="goLedger(row)">流水</el-button>
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
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi, userApi } from '../api';
import { invalidateLookups } from '../utils/lookups';
import { ROLE_MAP, dictText, dictType, formatTime } from '../utils/dict';

const router = useRouter();
// 服务端只接受 0/1/3，「领养人(2)」由领养审核通过后自动流转，不能手授
const GRANTABLE_ROLES = [0, 1, 3];
const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const keyword = ref('');
const role = ref(null);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim();
    }
    if (role.value !== null && role.value !== '') {
      params.role = role.value;
    }
    const page = await adminApi.users(params);
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

async function grantRole(row, nextRole) {
  const label = dictText(ROLE_MAP, nextRole);
  try {
    await ElMessageBox.confirm(
      `把「${row.nickname || row.username || '用户' + row.id}」的角色改为${label}？志愿者可以接单投喂，管理员可以进这个后台。`,
      '确认修改角色',
      { type: 'warning' }
    );
  } catch (err) {
    return;
  }
  try {
    await userApi.grantRole(row.id, nextRole);
    ElMessage.success('角色已更新');
    invalidateLookups();
    load();
  } catch (err) {
    load();
  }
}

function goLedger(row) {
  router.push({ path: '/ledger', query: { userId: row.id } });
}

onMounted(load);
</script>
