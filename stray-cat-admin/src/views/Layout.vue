<template>
  <el-container class="shell">
    <el-header class="shell-header">
      <div class="brand">
        <span class="brand-mark">喵</span>
        <span>猫遇猫舍 · 后台管理</span>
      </div>

      <div class="header-right">
        <ThemeSwitcher />

        <div class="account">
          <span class="muted">{{ auth.user?.nickname || auth.user?.username }}</span>
          <el-button link type="primary" @click="onRefresh">刷新身份</el-button>
          <el-button link @click="onLogout">退出</el-button>
        </div>
      </div>
    </el-header>

    <el-container>
      <el-aside width="190px" class="shell-aside">
        <el-menu ref="menuRef" :default-active="route.path" router unique-opened>
          <el-menu-item index="/dashboard">概览</el-menu-item>

          <el-sub-menu index="g-audit">
            <template #title>审核中心</template>
            <el-menu-item index="/adoption">领养申请审核</el-menu-item>
            <el-menu-item index="/records">投喂打卡审核</el-menu-item>
            <el-menu-item index="/lost-cat-review">寻猫审核</el-menu-item>
            <el-menu-item index="/transfer-review">爱猫搬家审核</el-menu-item>
            <el-menu-item index="/lost-cat-clue-review">线索审核</el-menu-item>
            <el-menu-item index="/reset-requests">密码找回申请</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="g-cat">
            <template #title>猫咪与投喂</template>
            <el-menu-item index="/cats">猫咪管理</el-menu-item>
            <el-menu-item index="/tasks">投喂任务</el-menu-item>
            <el-menu-item index="/ledger">猫粮流水</el-menu-item>
            <el-menu-item index="/tips">打赏记录</el-menu-item>
            <el-menu-item index="/recognizer-sample">花纹样本</el-menu-item>
            <el-menu-item index="/region-data">区域数据</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="g-user">
            <template #title>用户中心</template>
            <el-menu-item index="/users">用户与角色</el-menu-item>
            <el-menu-item index="/operation-log">操作日志</el-menu-item>
            <el-menu-item index="/feedback">用户反馈</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="g-content">
            <template #title>内容运营</template>
            <el-menu-item index="/posters">海报管理</el-menu-item>
            <el-menu-item index="/campaigns">活动管理</el-menu-item>
            <el-menu-item index="/home-content">首页内容</el-menu-item>
            <el-menu-item index="/wiki-manage">猫咪百科</el-menu-item>
            <el-menu-item index="/about-config">关于我们</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>

      <el-main class="shell-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';
import ThemeSwitcher from '../components/ThemeSwitcher.vue';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const menuRef = ref();

/** 路由 → 所属分组，用于导航时自动展开对应二级菜单 */
const GROUP_OF = {
  '/adoption': 'g-audit',
  '/records': 'g-audit',
  '/lost-cat-review': 'g-audit',
  '/transfer-review': 'g-audit',
  '/lost-cat-clue-review': 'g-audit',
  '/reset-requests': 'g-audit',
  '/cats': 'g-cat',
  '/tasks': 'g-cat',
  '/ledger': 'g-cat',
  '/tips': 'g-cat',
  '/recognizer-sample': 'g-cat',
  '/region-data': 'g-cat',
  '/users': 'g-user',
  '/operation-log': 'g-user',
  '/feedback': 'g-user',
  '/posters': 'g-content',
  '/campaigns': 'g-content',
  '/home-content': 'g-content',
  '/wiki-manage': 'g-content',
  '/about-config': 'g-content',
};

watch(
  () => route.path,
  (path) => {
    const group = GROUP_OF[path];
    if (group) {
      menuRef.value?.open(group);
    }
  },
  { immediate: true },
);

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
