<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">操作日志</span>
      <el-button size="small" @click="loadActive">刷新</el-button>
    </div>

    <el-tabs v-model="activeTab" class="log-tabs" @tab-change="onTabChange">
      <!-- 管理员操作：沿用原有内容 -->
      <el-tab-pane label="管理员操作" name="ops">
        <div class="filter-row">
          <el-select v-model="action" placeholder="全部动作" style="width: 160px" @change="onFilterChange">
            <el-option label="全部动作" value="" />
            <el-option v-for="item in ACTION_OPTIONS" :key="item.value" :label="item.text" :value="item.value" />
          </el-select>
          <el-input
            v-model="keyword"
            placeholder="操作人 / 目标账号"
            clearable
            style="width: 220px"
            @keyup.enter="onFilterChange"
          />
          <el-button type="primary" size="small" @click="onFilterChange">搜索</el-button>
          <span class="muted">日志仅记录操作行为，绝不记录密码明文</span>
        </div>

        <el-alert v-if="loadError" type="error" show-icon :closable="false" class="retry-alert">
          <template #title>
            操作日志加载失败
            <el-button link type="primary" size="small" @click="load">重试</el-button>
          </template>
        </el-alert>

        <el-table :data="rows" v-loading="loading" border size="small" empty-text="暂无操作日志">
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作人" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.operatorName || '—' }}</template>
          </el-table-column>
          <el-table-column label="动作" width="110">
            <template #default="{ row }">
              <el-tag :type="ACTION_MAP[row.action]?.type || 'info'" size="small">
                {{ ACTION_MAP[row.action]?.text || row.action || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="目标用户" min-width="150">
            <template #default="{ row }">
              <span class="cell-ellipsis" :title="row.targetUsername || ''">{{ row.targetUsername || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="详情" min-width="190">
            <template #default="{ row }">
              <span class="cell-ellipsis" :title="row.detail || ''">{{ row.detail || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="IP" width="140">
            <template #default="{ row }"><span class="mono">{{ row.ip || '—' }}</span></template>
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
      </el-tab-pane>

      <!-- 安全事件：Q5 并入操作日志，不新增菜单 -->
      <el-tab-pane label="安全事件" name="security">
        <div class="filter-row">
          <el-input
            v-model="eventUserId"
            placeholder="账号 / 用户ID"
            clearable
            style="width: 180px"
            @keyup.enter="onEventFilterChange"
          />
          <el-select v-model="eventType" clearable placeholder="全部事件" style="width: 160px" @change="onEventFilterChange">
            <el-option label="全部事件" value="" />
            <el-option v-for="item in EVENT_TYPE_OPTIONS" :key="item.value" :label="item.text" :value="item.value" />
          </el-select>
          <el-date-picker
            v-model="eventStart"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="开始时间"
            style="width: 185px"
          />
          <el-date-picker
            v-model="eventEnd"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="结束时间"
            style="width: 185px"
          />
          <el-button type="primary" size="small" @click="onEventFilterChange">查询</el-button>
        </div>

        <el-table :data="eventRows" v-loading="eventLoading" border size="small" empty-text="暂无安全事件">
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="账号" width="140" show-overflow-tooltip>
            <template #default="{ row }"><span class="mono">{{ row.username || '—' }}</span></template>
          </el-table-column>
          <el-table-column label="事件" width="120">
            <template #default="{ row }">
              <el-tag :type="EVENT_TYPE_MAP[row.eventType]?.type || 'info'" size="small">
                {{ EVENT_TYPE_MAP[row.eventType]?.text || row.eventType || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="详情" min-width="220">
            <template #default="{ row }">
              <span class="cell-ellipsis" :title="row.detail || ''">{{ row.detail || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="来源IP" width="140">
            <template #default="{ row }"><span class="mono">{{ row.ip || '—' }}</span></template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="eventPageNum"
            :page-size="pageSize"
            :total="eventTotal"
            layout="total, prev, pager, next"
            @current-change="loadEvents"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { adminUserApi } from '../api';
import { formatTime } from '../utils/dict';

const ACTION_MAP = {
  CREATE_USER: { text: '新增用户', type: 'success' },
  RESET_PASSWORD: { text: '重置密码', type: 'danger' },
  RESET_REQUEST_HANDLE: { text: '找回处理', type: 'warning' }
};
const ACTION_OPTIONS = Object.entries(ACTION_MAP).map(([value, item]) => ({ value, text: item.text }));

// 安全事件类型：value 为后端 event_type 枚举 code，标签文案与颜色按原型后台 03
const EVENT_TYPE_MAP = {
  PWD_CHANGE: { text: '改密成功', type: 'success' },
  TEMP_LOCK: { text: '临时锁定', type: 'warning' },
  PERMANENT_LOCK: { text: '永久锁定', type: 'danger' },
  ADMIN_UNLOCK: { text: '管理员解锁', type: 'info' },
  EMAIL_BIND: { text: '邮箱绑定', type: 'success' },
  EMAIL_UNBIND: { text: '邮箱解绑', type: 'warning' },
  EMAIL_CODE_SEND: { text: '邮箱验证码', type: 'info' },
  ADMIN_PWD_RESET: { text: '管理员重置', type: 'danger' }
};
const EVENT_TYPE_OPTIONS = Object.entries(EVENT_TYPE_MAP).map(([value, item]) => ({ value, text: item.text }));

const activeTab = ref('ops');
const pageSize = 20;

// ---------- 管理员操作 ----------
const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const action = ref('');
const keyword = ref('');
const loading = ref(false);
const loadError = ref(false);

async function load() {
  loading.value = true;
  loadError.value = false;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (action.value) {
      params.action = action.value;
    }
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim();
    }
    const page = await adminUserApi.operationLog(params);
    rows.value = page.records || [];
    total.value = page.total || 0;
  } catch (err) {
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

function onFilterChange() {
  pageNum.value = 1;
  load();
}

// ---------- 安全事件 ----------
const eventRows = ref([]);
const eventTotal = ref(0);
const eventPageNum = ref(1);
const eventUserId = ref('');
const eventType = ref('');
const eventStart = ref('');
const eventEnd = ref('');
const eventLoading = ref(false);

async function loadEvents() {
  const raw = eventUserId.value.trim();
  eventLoading.value = true;
  try {
    // 参数名严格按约定：userId / eventType / pageNum / pageSize。
    // 后端 userId 兼容数字（精确查 user_id）与账号名（模糊查 username），原样传值即可。
    // 时间筛选：后端接口未定义对应参数名，不自造字段，故时间框仅作展示、不发送。
    const params = { pageNum: eventPageNum.value, pageSize };
    if (raw) {
      params.userId = raw;
    }
    if (eventType.value) {
      params.eventType = eventType.value;
    }
    const page = await adminUserApi.securityEvents(params);
    eventRows.value = page.records || [];
    eventTotal.value = page.total || 0;
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    eventLoading.value = false;
  }
}

function onEventFilterChange() {
  eventPageNum.value = 1;
  loadEvents();
}

function onTabChange(name) {
  if (name === 'security') {
    loadEvents();
  }
}

function loadActive() {
  if (activeTab.value === 'security') {
    loadEvents();
  } else {
    load();
  }
}

onMounted(load);
</script>

<style scoped>
.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 11px;
}

.retry-alert {
  margin-bottom: 14px;
}
</style>
