<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">概览</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="pendingTotal > 0"
      class="todo-alert"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #title>
        有 {{ stats.adoptionPending || 0 }} 条领养申请、{{ stats.recordPending || 0 }} 条投喂打卡待审核
      </template>
    </el-alert>

    <el-row :gutter="16">
      <el-col v-for="group in groups" :key="group.label" :span="8" class="group-col">
        <h4 class="group-label">{{ group.label }}</h4>
        <div class="stat-grid">
          <div v-for="item in group.items" :key="item.key" class="stat-card">
            <div class="stat-label">{{ item.label }}</div>
            <div class="stat-value">{{ stats[item.key] ?? '-' }}</div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { adminApi } from '../api';

const stats = ref({});

const groups = [
  {
    label: '审核待办',
    items: [
      { key: 'adoptionPending', label: '领养申请待审' },
      { key: 'recordPending', label: '打卡记录待审' },
      { key: 'taskPending', label: '待接单任务' }
    ]
  },
  {
    label: '猫咪与投喂',
    items: [
      { key: 'catTotal', label: '猫咪总数' },
      { key: 'catFeeding', label: '投喂中' },
      { key: 'catWaitingAdoption', label: '待领养' },
      { key: 'catAdopted', label: '已领养' },
      { key: 'taskAccepted', label: '已接单' },
      { key: 'taskCheckedIn', label: '已投喂待审' },
      { key: 'taskDone', label: '投喂已完成' },
      { key: 'adoptionApproved', label: '领养已通过' },
      { key: 'adoptionRejected', label: '领养已拒绝' }
    ]
  },
  {
    label: '用户与猫粮',
    items: [
      { key: 'userTotal', label: '用户总数' },
      { key: 'volunteerTotal', label: '志愿者' },
      { key: 'adminTotal', label: '管理员' },
      { key: 'tipTotal', label: '打赏笔数' },
      { key: 'tipCoinTotal', label: '累计打赏猫粮' },
      { key: 'coinGrantedToday', label: '今日发放猫粮' }
    ]
  }
];

const pendingTotal = computed(
  () => (stats.value.adoptionPending || 0) + (stats.value.recordPending || 0)
);

async function load() {
  try {
    stats.value = await adminApi.stats();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

onMounted(load);
</script>

<style scoped>
.todo-alert {
  margin-bottom: 18px;
}

.group-col {
  margin-bottom: 20px;
}

.group-label {
  margin: 0 0 10px;
  font-size: 13px;
  letter-spacing: 2px;
  color: #7b8a84;
}
</style>
