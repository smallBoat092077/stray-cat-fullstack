<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">区域数据</span>
      <el-button size="small" @click="loadStats">刷新</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="行政区域为国标只读字典（省/市/区），由腾讯位置服务 district 接口全量同步，可重复执行（幂等）。同步后小程序三页的省市区筛选与逆地理编码即生效。"
    />

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-num">{{ stats.total ?? '—' }}</div>
        <div class="stat-label">已同步行政区条数</div>
      </div>
      <div class="stat-actions">
        <el-button type="primary" :loading="syncing" @click="sync">重新同步</el-button>
      </div>
    </div>

    <div class="muted tip" v-if="lastSync">
      上次同步结果：拉取 {{ lastSync.fetched }} 条，新增 {{ lastSync.inserted }} 条
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { regionApi } from '../api';

const stats = ref({});
const lastSync = ref(null);
const syncing = ref(false);

async function loadStats() {
  try {
    stats.value = (await regionApi.stats()) || {};
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function sync() {
  syncing.value = true;
  try {
    lastSync.value = await regionApi.sync();
    ElMessage.success(`同步完成：共 ${lastSync.value.total} 条`);
    loadStats();
  } catch (err) {
    // 提示由 http 拦截器统一给（未配置 key 会明确报错）
  } finally {
    syncing.value = false;
  }
}

onMounted(loadStats);
</script>

<style scoped>
.stats-row {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.stat-card {
  padding: 16px 28px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  text-align: center;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.tip {
  margin-top: 14px;
  font-size: 13px;
}
</style>
