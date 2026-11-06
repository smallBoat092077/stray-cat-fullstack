<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">投喂任务</span>
      <div>
        <el-button size="small" @click="load">刷新</el-button>
        <el-button size="small" type="primary" @click="openCreate">新建任务</el-button>
      </div>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="status" @change="onFilterChange">
        <el-radio-button :label="null">全部</el-radio-button>
        <el-radio-button v-for="key in [0, 1, 2, 3]" :key="key" :label="key">
          {{ dictText(TASK_STATUS_MAP, key) }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="猫咪 / 地点" min-width="200">
        <template #default="{ row }">
          {{ row.catName || `#${row.catId}` }}
          <span class="muted">· {{ row.locationDesc || '未填地点' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="dictType(TASK_STATUS_MAP, row.status)">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.tipId" type="warning" size="small">打赏委托</el-tag>
          <span v-else class="muted">后台派单</span>
        </template>
      </el-table-column>
      <el-table-column label="接单人" min-width="220">
        <template #default="{ row }">{{ volunteerText(row) }}</template>
      </el-table-column>
      <el-table-column label="猫粮" width="90">
        <template #default="{ row }">{{ row.amount ? row.amount + ' 份' : (row.foodQuantity ? row.foodQuantity + ' 份' : '—') }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
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

    <el-dialog v-model="createVisible" title="新建投喂任务" width="480px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="猫咪">
          <el-select v-model="form.catId" filterable placeholder="选择猫咪" style="width: 100%">
            <el-option
              v-for="cat in catOptions"
              :key="cat.id"
              :label="`${cat.name}（${cat.locationDesc || '无地点'}）`"
              :value="cat.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" style="width: 100%">
            <el-option label="日常投喂" :value="0" />
            <el-option label="紧急投喂" :value="1" />
            <el-option label="医疗救助" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="猫粮份数">
          <el-input-number v-model="form.foodQuantity" :min="0" :max="200" />
        </el-form-item>
        <el-form-item label="自备猫粮">
          <el-input v-model="form.foodType" placeholder="如：皇家 / 伟嘉，留空表示志愿者自备" />
        </el-form-item>
        <el-form-item label="打卡半径">
          <el-input-number v-model="form.radius" :min="20" :max="2000" :step="50" />
          <span class="muted"> 米</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { feedingApi } from '../api';
import { catNameMap } from '../utils/lookups';
import { TASK_STATUS_MAP, dictText, dictType, formatTime } from '../utils/dict';

const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const status = ref(null);
const loading = ref(false);
const cats = ref({});

const createVisible = ref(false);
const submitting = ref(false);
const form = reactive({ catId: null, taskType: 0, foodQuantity: 2, foodType: '', radius: 100 });

const catOptions = computed(() => Object.values(cats.value));

function volunteerText(row) {
  if (!row.volunteerId) {
    return '尚未接单';
  }
  const role = dictText(ROLE_SHORT, row.volunteerRole, '志愿者');
  const verb = { 1: '已接单', 2: '已投喂待审核', 3: '已完成投喂' }[row.status] || '接单中';
  return `${role}-${row.volunteerName}-${verb}，该${role}已完成 ${row.volunteerDoneCount || 0} 次投喂`;
}

const ROLE_SHORT = { 0: '爱心用户', 1: '志愿者', 3: '管理员' };

async function load() {
  loading.value = true;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (status.value !== null) {
      params.status = status.value;
    }
    const page = await feedingApi.taskList(params);
    rows.value = (page.records || []).map((row) => ({
      ...row,
      statusText: dictText(TASK_STATUS_MAP, row.status)
    }));
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

async function openCreate() {
  cats.value = await catNameMap();
  form.catId = catOptions.value.length ? catOptions.value[0].id : null;
  createVisible.value = true;
}

async function submitCreate() {
  const cat = cats.value[form.catId];
  if (!cat) {
    ElMessage.warning('请选择猫咪');
    return;
  }
  if (!cat.latitude || !cat.longitude) {
    ElMessage.warning('这只猫没有坐标，先去猫咪管理补经纬度');
    return;
  }
  submitting.value = true;
  try {
    await feedingApi.createTask({
      catId: cat.id,
      taskType: form.taskType,
      foodQuantity: form.foodQuantity,
      foodType: form.foodType || null,
      radius: form.radius,
      // 坐标取猫咪上报位置，志愿者必须到那里打卡
      latitude: cat.latitude,
      longitude: cat.longitude
    });
    ElMessage.success('任务已创建，等待接单');
    createVisible.value = false;
    pageNum.value = 1;
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  cats.value = await catNameMap();
  load();
});
</script>
