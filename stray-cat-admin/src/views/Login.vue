<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="login-title">流浪猫喂养后台</h2>
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
  background: linear-gradient(160deg, #14493c, #2c6653);
}

.login-card {
  width: 360px;
  padding: 8px 12px 16px;
}

.login-title {
  margin: 8px 0 4px;
  font-size: 20px;
  color: #14493c;
}

.login-sub {
  margin: 0 0 18px;
  font-size: 13px;
  color: #8a968f;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
}
</style>
