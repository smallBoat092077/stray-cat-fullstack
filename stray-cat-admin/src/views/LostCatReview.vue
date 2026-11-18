<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">寻猫审核</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="auditStatus" @change="load">
        <el-radio-button :value="0">待审</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <span class="muted">先审后发：仅审核通过的启事会进入小程序寻猫广场</span>
    </div>

    <el-table :data="rows" v-loading="loading" size="small" border empty-text="暂无启事">
      <el-table-column prop="catName" label="猫咪名" min-width="110" show-overflow-tooltip />
      <el-table-column prop="coatDesc" label="毛色花纹" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.coatDesc || '—' }}</template>
      </el-table-column>
      <el-table-column label="图片" min-width="180">
        <template #default="{ row }">
          <div v-if="row.imageList && row.imageList.length" class="review-images">
            <el-image
              v-for="img in row.imageList"
              :key="img"
              :src="img"
              :preview-src-list="row.imageList"
              :preview-teleported="true"
              fit="cover"
              class="thumb"
            />
          </div>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="丢失时间 / 地点" min-width="200">
        <template #default="{ row }">
          <div>{{ formatTime(row.lostAt) || '—' }}</div>
          <div class="muted">{{ row.addressDesc || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="归属区域" width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.provinceCode || row.cityCode || row.districtCode">{{ regionNames(row) }}</span>
          <span v-else class="muted">未归属</span>
        </template>
      </el-table-column>
      <el-table-column label="发布人" width="150">
        <template #default="{ row }">
          {{ row.nickname || row.userName || '未知' }}
          <span class="muted">(#{{ row.userId }})</span>
        </template>
      </el-table-column>
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="AUDIT_STATUS[row.auditStatus]?.type || 'info'" size="small">
            {{ AUDIT_STATUS[row.auditStatus]?.text || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="驳回原因" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.auditRemark || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="row.auditStatus === 0">
            <el-button size="small" type="primary" @click="approve(row)">通过</el-button>
            <el-button size="small" type="danger" @click="reject(row)">驳回</el-button>
          </template>
          <el-button size="small" @click="openRegion(row)">改区域</el-button>
          <span v-if="row.auditStatus !== 0" class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="regionVisible" title="修正归属区域" width="460px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="status-tip"
        title="归属区域决定寻猫广场的区域过滤范围；清空归属后该启事只在「全部」口径可见。"
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import http from '../api/http';
import { lostCatApi } from '../api';
import { formatTime } from '../utils/dict';
import { loadRegionTree, regionNames } from '../utils/region';

const AUDIT_STATUS = {
  0: { text: '待审', type: 'warning' },
  1: { text: '通过', type: 'success' },
  2: { text: '驳回', type: 'danger' }
};

const auditStatus = ref(0);
const rows = ref([]);
const loading = ref(false);

// 后端存的是 /uploads/... 相对路径，用 http.defaults.baseURL（/api）拼成静态资源完整地址
function resolveImage(path) {
  const p = (path || '').trim();
  if (!p) return '';
  if (/^https?:\/\//i.test(p)) return p;
  const base = http.defaults.baseURL || '';
  return base && !p.startsWith(base) ? `${base}${p}` : p;
}

async function load() {
  loading.value = true;
  try {
    const params = {};
    if (auditStatus.value !== null && auditStatus.value !== '') {
      params.auditStatus = auditStatus.value;
    }
    const res = await lostCatApi.list(params);
    const list = Array.isArray(res) ? res : (res?.records || []);
    rows.value = list.map((item) => ({
      ...item,
      imageList: item.images
        ? (Array.isArray(item.images) ? item.images : String(item.images).split(','))
            .filter(Boolean).map(resolveImage).filter(Boolean)
        : []
    }));
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    loading.value = false;
  }
}

async function approve(row) {
  try {
    await ElMessageBox.confirm(
      `确认通过「${row.catName}」的寻猫启事？通过后将在寻猫广场展示。`,
      '审核通过',
      { type: 'info', confirmButtonText: '通过', cancelButtonText: '取消' }
    );
  } catch (cancel) {
    return;
  }
  try {
    await lostCatApi.audit(row.id, { auditStatus: 1 });
    ElMessage.success('已通过');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function reject(row) {
  let reason;
  try {
    const { value } = await ElMessageBox.prompt(
      '驳回原因会展示给发布人，请填写具体原因',
      `驳回寻猫启事 · ${row.catName}`,
      {
        inputPlaceholder: '如：图片不清晰 / 含未脱敏联系方式',
        inputValue: '',
        inputValidator: (val) => (val && val.trim() ? true : '驳回原因不能为空')
      }
    );
    reason = value.trim();
  } catch (err) {
    return;
  }
  try {
    await lostCatApi.audit(row.id, { auditStatus: 2, auditRemark: reason });
    ElMessage.success('已驳回');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

// ---------- 归属区域修正（区域划分需求 R5/D4） ----------
const regionVisible = ref(false);
const regionBusy = ref(false);
const regionOptions = ref([]);
const regionPath = ref([]);
const currentRow = ref(null);

async function openRegion(row) {
  currentRow.value = row;
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
  if (!currentRow.value) return;
  const [p = '', c = '', d = ''] = regionPath.value;
  regionBusy.value = true;
  try {
    await lostCatApi.updateRegion(currentRow.value.id, {
      provinceCode: p || '',
      cityCode: c || '',
      districtCode: d || ''
    });
    ElMessage.success('归属区域已更新');
    regionVisible.value = false;
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
.review-images {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.thumb {
  width: 56px;
  height: 56px;
  border-radius: 4px;
  display: block;
}

.status-tip {
  margin-bottom: 14px;
}
</style>
