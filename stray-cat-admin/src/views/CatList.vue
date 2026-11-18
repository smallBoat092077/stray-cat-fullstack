<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">猫咪管理</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-select v-model="lifeStatus" clearable placeholder="全部生活状态" style="width: 180px" @change="onFilterChange">
        <el-option v-for="key in [0, 1, 2, 3, 4]" :key="key" :label="dictText(LIFE_STATUS_MAP, key)" :value="key" />
      </el-select>
      <el-input v-model="keyword" placeholder="按名字/地点筛选" clearable style="width: 220px" />
      <span class="muted">共 {{ filteredRows.length }} 只</span>
    </div>

    <el-table :data="filteredRows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="90">
        <template #default="{ row }">
          <img v-if="row.coverImage" :src="imageUrl(row.coverImage)" class="thumb" alt="封面" />
          <span v-else class="muted">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名字" width="120" />
      <el-table-column label="性别" width="80">
        <template #default="{ row }">{{ GENDER_MAP[row.gender] ?? '未知' }}</template>
      </el-table-column>
      <el-table-column label="品种 / 毛色" min-width="140">
        <template #default="{ row }">{{ row.breed || '—' }} / {{ row.color || '—' }}</template>
      </el-table-column>
      <el-table-column label="健康" width="110">
        <template #default="{ row }">{{ HEALTH_STATUS_MAP[row.healthStatus] || '未检查' }}</template>
      </el-table-column>
      <el-table-column label="生活状态" width="130">
        <template #default="{ row }">
          <el-tag :type="dictType(LIFE_STATUS_MAP, row.lifeStatus)">
            {{ dictText(LIFE_STATUS_MAP, row.lifeStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="归属区域" width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.provinceCode || row.cityCode || row.districtCode">{{ regionNames(row) }}</span>
          <span v-else class="muted">未归属</span>
        </template>
      </el-table-column>
      <el-table-column label="地点" min-width="180">
        <template #default="{ row }">
          {{ row.locationDesc || '未填写' }}
          <div class="muted" v-if="row.latitude">
            {{ Number(row.latitude).toFixed(5) }}, {{ Number(row.longitude).toFixed(5) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="累计猫粮" width="100">
        <template #default="{ row }">{{ row.coinTotal || 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" @click="openRegion(row)">改区域</el-button>
          <el-button size="small" type="primary" @click="openStatus(row)">改状态</el-button>
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

    <el-dialog v-model="editVisible" title="编辑猫咪信息" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="猫咪头像">
          <div class="avatar-editor">
            <div class="avatar-row">
              <img v-if="current && current.coverImage" :src="imageUrl(current.coverImage)" class="avatar-preview" alt="头像" />
              <div v-else class="avatar-preview avatar-empty">无头像</div>
              <div class="avatar-actions">
                <el-button size="small" :disabled="coverBusy" @click="coverInput && coverInput.click()">上传新头像</el-button>
                <input
                  ref="coverInput"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style="display: none"
                  @change="onCoverUpload"
                />
                <div class="muted avatar-tip">默认取上报图片第一张；上传或点选下方图片即可更换</div>
              </div>
            </div>
            <div v-if="pickableImages.length" class="pick-grid">
              <div
                v-for="p in pickableImages"
                :key="p.path"
                class="pick-item"
                :class="{ picked: p.path === (current && current.coverImage) }"
                @click="pickCover(p.path)"
              >
                <img :src="p.url" alt="上报图片" />
                <span class="pick-badge" v-if="p.path === (current && current.coverImage)">当前</span>
              </div>
            </div>
            <div v-else class="muted">该猫暂无上报本体图，可直接上传头像</div>
          </div>
        </el-form-item>
        <el-form-item label="名字"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="性别">
          <el-select v-model="form.gender" style="width: 100%">
            <el-option label="母" :value="0" />
            <el-option label="公" :value="1" />
            <el-option label="未知" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="年龄估计"><el-input v-model="form.ageEstimate" /></el-form-item>
        <el-form-item label="品种"><el-input v-model="form.breed" /></el-form-item>
        <el-form-item label="毛色"><el-input v-model="form.color" /></el-form-item>
        <el-form-item label="性格"><el-input v-model="form.personality" /></el-form-item>
        <el-form-item label="健康状态">
          <el-select v-model="form.healthStatus" style="width: 100%">
            <el-option v-for="(text, key) in HEALTH_STATUS_MAP" :key="key" :label="text" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="地点描述"><el-input v-model="form.locationDesc" /></el-form-item>
        <el-form-item label="纬度"><el-input v-model="form.latitude" placeholder="如 30.4729792" /></el-form-item>
        <el-form-item label="经度"><el-input v-model="form.longitude" placeholder="如 114.4412024" /></el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="regionVisible" title="修正归属区域" width="460px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="status-tip"
        title="归属区域决定小程序三个页面的区域过滤范围；清空归属后该猫只在「全部」口径可见。"
      />
      <el-cascader
        v-model="regionPath"
        :options="regionOptions"
        :props="{ value: 'code', label: 'name', children: 'children', emitPath: true }"
        placeholder="选择省 / 市 / 区（可只选到市）"
        clearable
        style="width: 100%"
      />
      <template #footer>
        <el-button @click="regionVisible = false">取消</el-button>
        <el-button type="primary" :loading="regionBusy" @click="submitRegion">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusVisible" title="纠正生活状态" width="420px">
      <el-alert type="info" :closable="false" show-icon class="status-tip">
        状态流转有约束（例如「已领养」只能转「领养回访中」），不在允许范围内服务端会直接拒绝。
      </el-alert>
      <el-select v-model="newStatus" style="width: 100%">
        <el-option v-for="key in [0, 1, 2, 3, 4]" :key="key" :label="dictText(LIFE_STATUS_MAP, key)" :value="key" />
      </el-select>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStatus">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, computed, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import { catApi } from '../api';
import { invalidateLookups } from '../utils/lookups';
import { loadRegionTree, regionNames } from '../utils/region';
import {
  GENDER_MAP,
  HEALTH_STATUS_MAP,
  LIFE_STATUS_MAP,
  dictText,
  dictType,
  imageUrl
} from '../utils/dict';

const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const lifeStatus = ref(null);
const keyword = ref('');
const loading = ref(false);

const editVisible = ref(false);
const statusVisible = ref(false);
const submitting = ref(false);
const current = ref(null);
const newStatus = ref(0);
const form = reactive({
  name: '',
  gender: 2,
  ageEstimate: '',
  breed: '',
  color: '',
  personality: '',
  healthStatus: 0,
  locationDesc: '',
  latitude: '',
  longitude: '',
  description: ''
});

const filteredRows = computed(() => {
  const text = keyword.value.trim();
  if (!text) {
    return rows.value;
  }
  return rows.value.filter(
    (cat) => (cat.name || '').includes(text) || (cat.locationDesc || '').includes(text)
  );
});

// ---------- 猫咪头像（猫咪头像功能设计方案 §3.3） ----------
const coverInput = ref(null);
const coverBusy = ref(false);

/** 可挑选的候选头像：cover_image + 上报本体图 images（D2：环境图不参与） */
const pickableImages = computed(() => {
  const row = current.value;
  if (!row) {
    return [];
  }
  const list = [];
  if (row.coverImage) {
    list.push({ path: row.coverImage, url: imageUrl(row.coverImage) });
  }
  (row.images || '')
    .split(',')
    .map((p) => p.trim())
    .filter(Boolean)
    .forEach((p) => {
      if (!list.some((x) => x.path === p)) {
        list.push({ path: p, url: imageUrl(p) });
      }
    });
  return list;
});

async function pickCover(path) {
  if (coverBusy.value || !current.value) return;
  if (path === current.value.coverImage) return;
  coverBusy.value = true;
  try {
    const res = await catApi.updateCover(current.value.id, path);
    current.value.coverImage = (res && res.coverImage) || path;
    ElMessage.success('头像已更新');
    invalidateLookups();
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    coverBusy.value = false;
  }
}

async function onCoverUpload(e) {
  const file = e.target.files && e.target.files[0];
  if (!file || !current.value) return;
  coverBusy.value = true;
  try {
    const res = await catApi.uploadCover(current.value.id, file);
    current.value.coverImage = (res && res.coverImage) || current.value.coverImage;
    ElMessage.success('头像已更新');
    invalidateLookups();
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    coverBusy.value = false;
    e.target.value = '';
  }
}

async function load() {
  loading.value = true;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (lifeStatus.value !== null && lifeStatus.value !== '') {
      params.lifeStatus = lifeStatus.value;
    }
    const page = await catApi.list(params);
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

function openEdit(row) {
  current.value = row;
  Object.assign(form, {
    name: row.name,
    gender: row.gender ?? 2,
    ageEstimate: row.ageEstimate || '',
    breed: row.breed || '',
    color: row.color || '',
    personality: row.personality || '',
    healthStatus: row.healthStatus ?? 0,
    locationDesc: row.locationDesc || '',
    latitude: row.latitude ?? '',
    longitude: row.longitude ?? '',
    description: row.description || ''
  });
  editVisible.value = true;
}

async function submitEdit() {
  submitting.value = true;
  try {
    await catApi.update(current.value.id, { ...form });
    ElMessage.success('已保存');
    editVisible.value = false;
    invalidateLookups();
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    submitting.value = false;
  }
}

function openStatus(row) {
  current.value = row;
  newStatus.value = row.lifeStatus ?? 0;
  statusVisible.value = true;
}

async function submitStatus() {
  submitting.value = true;
  try {
    await catApi.updateLifeStatus(current.value.id, newStatus.value);
    ElMessage.success('状态已更新');
    statusVisible.value = false;
    invalidateLookups();
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    submitting.value = false;
  }
}

// ---------- 归属区域修正（区域划分需求 R5/D4） ----------
const regionVisible = ref(false);
const regionBusy = ref(false);
const regionOptions = ref([]);
const regionPath = ref([]);

async function openRegion(row) {
  current.value = row;
  regionPath.value = [row.provinceCode, row.cityCode, row.districtCode].filter(Boolean);
  if (!regionOptions.value.length) {
    try {
      regionOptions.value = await loadRegionTree();
    } catch (err) {
      // 提示由 http 拦截器统一给
    }
  }
  regionVisible.value = true;
}

async function submitRegion() {
  if (!current.value) return;
  const [p = '', c = '', d = ''] = regionPath.value;
  regionBusy.value = true;
  try {
    await catApi.updateRegion(current.value.id, {
      provinceCode: p || '',
      cityCode: c || '',
      districtCode: d || ''
    });
    ElMessage.success('归属区域已更新');
    regionVisible.value = false;
    invalidateLookups();
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    regionBusy.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.thumb {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 4px;
}

.avatar-editor {
  width: 100%;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-preview {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 50%;
  flex-shrink: 0;
  border: 1px solid #e4e7ed;
}

.avatar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
}

.avatar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-tip {
  font-size: 12px;
}

.pick-grid {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pick-item {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
}

.pick-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.pick-item.picked {
  border-color: var(--el-color-primary, #409eff);
}

.pick-badge {
  position: absolute;
  left: 0;
  bottom: 0;
  right: 0;
  text-align: center;
  font-size: 11px;
  line-height: 18px;
  color: #fff;
  background: rgba(64, 158, 255, 0.85);
}

.status-tip {
  margin-bottom: 14px;
}
</style>
