<template>
  <el-container class="shell">
    <el-header class="shell-header">
      <div class="brand">
        <span class="brand-mark">喵</span>
        <span>流浪猫喂养 · 后台管理</span>
      </div>
      <div class="account">
        <span class="muted">{{ auth.user?.nickname || auth.user?.username }}</span>
        <el-button link type="primary" @click="onRefresh">刷新身份</el-button>
        <el-button link @click="onLogout">退出</el-button>
      </div>
    </el-header>

    <el-container>
      <el-aside width="190px" class="shell-aside">
        <el-menu :default-active="route.path" router>
          <el-menu-item index="/dashboard">概览</el-menu-item>
          <el-menu-item index="/adoption">领养申请审核</el-menu-item>
          <el-menu-item index="/records">投喂打卡审核</el-menu-item>
          <el-menu-item index="/tasks">投喂任务</el-menu-item>
          <el-menu-item index="/cats">猫咪管理</el-menu-item>
          <el-menu-item index="/users">用户与角色</el-menu-item>
          <el-menu-item index="/ledger">猫粮流水</el-menu-item>
          <el-menu-item index="/tips">打赏记录</el-menu-item>
          <el-menu-item index="/posters">海报管理</el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="shell-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();

async function onRefresh() {
  try {
    await auth.refresh();
    ElMessage.success('身份已刷新');
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

function onLogout() {
  auth.logout();
  router.replace('/login');
}
</script>

<style scoped>
.shell {
  height: 100vh;
}

.shell-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #14493c;
  color: #f7f2e6;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  letter-spacing: 1px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #c3a15f;
  color: #14493c;
  font-weight: 700;
}

.account {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.account :deep(.el-button) {
  color: #f7f2e6;
}

.account .muted {
  color: #d8cdb4;
}

.shell-aside {
  background: #fff;
  border-right: 1px solid #e6e3da;
}

.shell-main {
  padding: 0;
  overflow: auto;
}
</style>
