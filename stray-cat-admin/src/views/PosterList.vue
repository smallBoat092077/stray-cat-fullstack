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
      <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onFileSelected" />
      <el-button type="primary" :disabled="uploading" @click="$refs.fileInput.click()">
        {{ uploading ? '上传中...' : '上传图片' }}
      </el-button>
      <span class="muted">支持 jpg / png / webp，建议宽度 1440px 以上</span>
    </div>

    <div v-if="uploading" class="progress-wrap">
      <div class="progress-info">
        <span>{{ fileName }}</span>
        <span>{{ uploadPercent }}%</span>
      </div>
      <el-progress :percentage="uploadPercent" :stroke-width="10" :show-text="false" />
    </div>

    <div class="poster-grid">
      <el-empty v-if="!loading && posters.length === 0" description="暂无海报" />
      <div v-for="poster in sortedPosters" :key="poster.id" class="poster-card">
        <img :src="POSTERS_BASE_URL + poster.imageUrl" class="poster-img" alt="海报" />
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

const POSTERS_BASE_URL = '';

const posters = ref([]);
const loading = ref(false);
const uploading = ref(false);
const uploadPercent = ref(0);
const fileName = ref('');
const fileInput = ref(null);

const sortedPosters = computed(() => {
  return [...posters.value].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
});

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

function onFileSelected(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  e.target.value = '';
  doUpload(file);
}

function doUpload(file) {
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件不能超过 20MB');
    return;
  }
  uploading.value = true;
  uploadPercent.value = 0;
  fileName.value = file.name;

  const fd = new FormData();
  fd.append('file', file);

  const token = localStorage.getItem('admin-token') || '';
  const xhr = new XMLHttpRequest();
  xhr.open('POST', '/api/poster/upload');
  xhr.setRequestHeader('Authorization', `Bearer ${token}`);

  xhr.upload.onprogress = (e) => {
    if (e.lengthComputable) {
      uploadPercent.value = Math.round((e.loaded / e.total) * 100);
    }
  };

  xhr.onload = () => {
    uploading.value = false;
    try {
      const res = JSON.parse(xhr.responseText);
      if (res && res.code === 200 && res.data) {
        posters.value.push(res.data);
        ElMessage.success('海报已添加');
      } else {
        ElMessage.error((res && res.message) || '上传失败');
      }
    } catch {
      ElMessage.error('上传响应异常');
    }
  };

  xhr.onerror = () => {
    uploading.value = false;
    ElMessage.error('上传失败，请检查网络');
  };

  xhr.send(fd);
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
  margin-bottom: 16px;
}

.progress-wrap {
  margin-bottom: 20px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
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
