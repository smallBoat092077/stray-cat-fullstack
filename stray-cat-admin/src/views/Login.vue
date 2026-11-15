<template>
  <div class="login-wrap">
    <!-- 右上角外观按钮 -->
    <div class="theme-btn-wrap">
      <ThemeSwitcher />
    </div>

    <el-card class="login-card fade-up">
      <h2 class="login-title">猫遇猫舍后台</h2>
      <p class="login-sub">管理员账号登录（role=3）</p>
      <el-form :model="form" label-position="top" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="后台管理员账号" @keyup.enter="onSubmit" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="密码"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';
import ThemeSwitcher from '../components/ThemeSwitcher.vue';

const auth = useAuthStore();
const router = useRouter();
const loading = ref(false);
const form = reactive({ username: '', password: '' });

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写账号和密码');
    return;
  }
  loading.value = true;
  try {
    const user = await auth.login(form.username, form.password);
    if (user.role !== 3) {
      auth.logout();
      ElMessage.error('该账号不是管理员，无法进入后台');
      return;
    }
    router.replace('/dashboard');
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient);
  background-attachment: fixed;
  position: relative;
}

.theme-btn-wrap {
  position: absolute;
  top: 20px;
  right: 24px;
  z-index: 10;
}

.login-card {
  width: 360px;
  padding: 8px 12px 16px;
  background: var(--glass);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 0.5px solid var(--border);
  box-shadow: var(--shadow);
  color: var(--fg);
}

.login-card :deep(.el-card__body) {
  padding: 16px 8px 8px;
}

.login-card :deep(.el-form-item__label) {
  color: var(--fg-muted);
}

.login-card :deep(.el-input__wrapper) {
  background: var(--input-bg);
  box-shadow: 0 0 0 0.5px var(--border) !important;
}

.login-card :deep(.el-input__inner) {
  color: var(--fg);
}

.login-title {
  margin: 4px 0 4px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, var(--fg) 0%, var(--accent) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.login-sub {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--fg-muted);
}

.login-btn {
  width: 100%;
  margin-top: 8px;
  background: var(--accent);
  border-color: var(--accent);
}

.login-btn:hover {
  opacity: 0.88;
}
</style>
