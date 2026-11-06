<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">海报管理</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <el-alert type="info" :closable="false" show-icon class="hint">
      上传新海报后会追加到列表末尾；拖拽可调整顺序，删除后位置由后续海报补齐。
    </el-alert>

    <div class="upload-row">
      <el-upload
        action="/api/poster/upload"
        name="file"
        accept="image/*"
        :show-file-list="false"
        :headers="uploadHeaders"
        @success="onUploadSuccess"
      >
        <el-button type="primary">上传图片</el-button>
      </el-upload>
      <span class="muted">支持 jpg / png / webp，建议宽度 1440px 以上</span>
    </div>

    <div class="poster-grid">
      <el-empty v-if="!loading && posters.length === 0" description="暂无海报" />
      <div v-for="poster in sortedPosters" :key="poster.id" class="poster-card">
        <img :src="baseUrl + poster.imageUrl" class="poster-img" alt="海报" />
        <div class="poster-actions">
          <span class="muted">#{{ poster.sortOrder }}</span>
          <el-button size="small" type="danger" @click="onDelete(poster)">删除</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { posterApi } from '../api';

const POSTERS_BASE_URL = 'https://straycat.fun';

const posters = ref([]);
const loading = ref(false);

const sortedPosters = computed(() => {
  return [...posters.value].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
});

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('admin-token') || ''}`
}));

async function load() {
  loading.value = true;
  try {
    posters.value = await posterApi.getList();
  } catch (err) {
    // http interceptor handles message
  } finally {
    loading.value = false;
  }
}

async function onUploadSuccess(res) {
  if (!res || !res.data) {
    ElMessage.error('上传响应异常');
    return;
  }
  posters.value.push(res.data);
  ElMessage.success('海报已添加');
}

async function onDelete(poster) {
  try {
    await posterApi.delete(poster.id);
    posters.value = posters.value.filter((p) => p.id !== poster.id);
    // 重排序号
    posters.value.forEach((p, i) => {
      p.sortOrder = i;
    });
    ElMessage.success('已删除');
  } catch (err) {
    // http interceptor handles message
  }
}

onMounted(load);
</script>

<style scoped>
.hint {
  margin-bottom: 16px;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.poster-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.poster-card {
  border: 1px solid #e6e3da;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.poster-img {
  width: 100%;
  height: 180px;
  object-fit: cover;
  display: block;
}

.poster-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
}
</style>
