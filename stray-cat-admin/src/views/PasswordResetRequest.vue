<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">密码找回申请</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="status" @change="onStatusChange">
        <el-radio-button :value="0">待处理</el-radio-button>
        <el-radio-button :value="1">已重置</el-radio-button>
        <el-radio-button :value="2">已忽略</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <span class="muted">请电话联系用户核实身份后再重置，临时密码仅线下转告</span>
    </div>

    <el-alert v-if="loadError" type="error" show-icon :closable="false" class="retry-alert">
      <template #title>
        找回申请加载失败
        <el-button link type="primary" size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <el-table :data="rows" v-loading="loading" border size="small" empty-text="暂无找回申请">
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="账号" min-width="140">
        <template #default="{ row }"><span class="mono">{{ row.username || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="昵称" width="110">
        <template #default="{ row }">{{ row.nickname || '—' }}</template>
      </el-table-column>
      <el-table-column label="手机号" width="140">
        <template #default="{ row }"><span class="mono">{{ maskPhone(row.phone) }}</span></template>
      </el-table-column>
      <el-table-column label="说明" min-width="200">
        <template #default="{ row }">
          <span class="cell-ellipsis" :title="row.reason || ''">{{ row.reason || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS_MAP[row.status]?.type || 'info'" size="small">
            {{ STATUS_MAP[row.status]?.text || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理人" min-width="150">
        <template #default="{ row }">
          <template v-if="row.status === 0"><span class="muted">—</span></template>
          <template v-else>
            <div>{{ row.operatorName || '—' }}</div>
            <div class="muted">{{ formatTime(row.handledAt) }}</div>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button link type="primary" size="small" @click="openVerify(row)">核实并重置</el-button>
            <el-button link type="danger" size="small" @click="ignoreRequest(row)">忽略</el-button>
          </template>
          <span v-else class="muted">已处理</span>
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

    <!-- 核实并重置 -->
    <el-dialog v-model="verifyVisible" title="核实并重置密码" width="540px" :close-on-click-modal="false">
      <div class="info-grid">
        <div class="info-item"><span class="info-label">用户提交的账号</span><span class="info-val mono">{{ currentRow?.username || '—' }}</span></div>
        <div class="info-item"><span class="info-label">用户填的昵称</span><span class="info-val">{{ currentRow?.nickname || '—' }}</span></div>
        <div class="info-item"><span class="info-label">手机号</span><span class="info-val mono">{{ currentRow?.phone || '—' }}</span></div>
        <div class="info-item"><span class="info-label">情况说明</span><span class="info-val">{{ currentRow?.reason || '—' }}</span></div>
      </div>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="verify-tip"
        title="请电话联系用户核实身份后，点击下方按钮生成临时密码。原密码会立即失效。"
      />

      <div class="backend-section">
        <div class="section-title">后台真实资料（核对用）</div>
        <div v-if="backendLoading" class="muted">正在核对后台资料…</div>
        <div v-else-if="backendUser" class="backend-card">
          昵称 <b>{{ backendUser.nickname || '未设置' }}</b> · 角色 <b>{{ dictText(ROLE_MAP, backendUser.role) }}</b> · 注册 <b>{{ formatTime(backendUser.createdAt) }}</b>
        </div>
        <div v-else class="backend-miss">未查到该账号的后台资料，请谨慎核实用户身份，防止冒领。</div>
      </div>

      <el-form label-width="80px" class="remark-form">
        <el-form-item label="处理备注">
          <el-input v-model="remark" type="textarea" :rows="2" maxlength="200" placeholder="选填，记录核实情况" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="verifyVisible = false">取消</el-button>
        <el-button type="danger" @click="ignoreRequest()">忽略</el-button>
        <el-button type="primary" :loading="verifyBusy" @click="confirmReset">确认核实并重置</el-button>
      </template>
    </el-dialog>

    <!-- 临时密码一次性只读展示 -->
    <el-dialog
      v-model="resultVisible"
      title="密码已重置"
      width="460px"
      :close-on-click-modal="false"
      @closed="clearResult"
    >
      <div class="pwd-card">
        <div class="pwd-label">账号 <span class="mono">{{ result.username }}</span> 的临时密码</div>
        <div class="pwd-value">{{ result.temporaryPassword }}</div>
        <el-button size="small" class="copy-btn" @click="copyPwd">一键复制</el-button>
      </div>
      <div class="pwd-warn">
        此密码<strong>仅显示这一次</strong>，关闭后无法再次查看；请立即线下电话转告用户预留的手机号。服务端不保存明文，操作日志也不记录密码。
      </div>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">我已复制并保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi, adminUserApi } from '../api';
import { ROLE_MAP, dictText, formatTime } from '../utils/dict';

const STATUS_MAP = {
  0: { text: '待处理', type: 'warning' },
  1: { text: '已重置', type: 'success' },
  2: { text: '已忽略', type: 'info' }
};

const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const status = ref(0);
const loading = ref(false);
const loadError = ref(false);

function maskPhone(phone) {
  const p = String(phone || '');
  if (p.length < 7) return p || '—';
  return `${p.slice(0, 3)}****${p.slice(-4)}`;
}

async function load() {
  loading.value = true;
  loadError.value = false;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (status.value !== '' && status.value !== null) {
      params.status = status.value;
    }
    const page = await adminUserApi.resetRequests(params);
    rows.value = page.records || [];
    total.value = page.total || 0;
  } catch (err) {
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

function onStatusChange() {
  pageNum.value = 1;
  load();
}

// ---------- 核实并重置 ----------
const verifyVisible = ref(false);
const verifyBusy = ref(false);
const currentRow = ref(null);
const backendUser = ref(null);
const backendLoading = ref(false);
const backendMiss = ref(false);
const remark = ref('');

async function openVerify(row) {
  currentRow.value = row;
  remark.value = '';
  backendUser.value = null;
  backendMiss.value = false;
  verifyVisible.value = true;
  backendLoading.value = true;
  try {
    // 复用现有用户列表接口按账号查询，取账号完全匹配的一条供管理员比对
    const page = await adminApi.users({ keyword: row.username, pageNum: 1, pageSize: 20 });
    const list = page.records || [];
    const matched = list.find((u) => u.username === row.username) || list[0] || null;
    backendUser.value = matched;
    backendMiss.value = !matched;
  } catch (err) {
    backendMiss.value = true;
  } finally {
    backendLoading.value = false;
  }
}

const resultVisible = ref(false);
const result = ref({ username: '', temporaryPassword: '' });

async function confirmReset() {
  if (!currentRow.value) return;
  verifyBusy.value = true;
  try {
    const data = await adminUserApi.handleResetRequest(currentRow.value.id, {
      status: 1,
      remark: remark.value.trim()
    });
    verifyVisible.value = false;
    result.value = {
      username: data?.username || currentRow.value.username || '',
      temporaryPassword: data?.temporaryPassword || ''
    };
    resultVisible.value = true;
    ElMessage.success('已核实并重置密码');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    verifyBusy.value = false;
  }
}

async function ignoreRequest(row) {
  const target = row || currentRow.value;
  if (!target) return;
  let reason;
  try {
    const { value } = await ElMessageBox.prompt('请填写忽略理由，便于后续追溯', '忽略找回申请', {
      inputPlaceholder: '如：多次联系未接通 / 身份信息不符',
      inputValidator: (val) => (val && val.trim() ? true : '请填写忽略理由')
    });
    reason = value.trim();
  } catch (err) {
    return;
  }
  try {
    await adminUserApi.handleResetRequest(target.id, { status: 2, remark: reason });
    ElMessage.success('已忽略该申请');
    verifyVisible.value = false;
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function copyPwd() {
  const text = result.value.temporaryPassword;
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制临时密码');
  } catch (err) {
    ElMessage.warning('自动复制失败，请手动选择密码复制');
  }
}

function clearResult() {
  result.value = { username: '', temporaryPassword: '' };
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

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 18px;
  margin-bottom: 14px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.info-label {
  font-size: 11px;
  color: var(--fg-muted);
}

.info-val {
  font-size: 13px;
  color: var(--fg);
  word-break: break-all;
}

.verify-tip {
  margin-bottom: 14px;
}

.backend-section {
  border-top: 0.5px solid var(--border);
  padding-top: 12px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  margin-bottom: 8px;
}

.backend-card {
  background: var(--input-bg);
  border: 0.5px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.7;
}

.backend-miss {
  background: color-mix(in srgb, #f56c6c 10%, transparent);
  border: 0.5px solid color-mix(in srgb, #f56c6c 30%, transparent);
  color: #e06c5a;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
}

.remark-form {
  margin-top: 14px;
}

.pwd-card {
  background: color-mix(in srgb, var(--accent) 8%, var(--input-bg));
  border: 0.5px solid var(--border);
  border-radius: 12px;
  padding: 18px;
  text-align: center;
}

.pwd-label {
  font-size: 12px;
  color: var(--fg-muted);
}

.pwd-value {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 26px;
  letter-spacing: 2px;
  color: var(--accent);
  font-weight: 700;
  margin: 12px 0;
  user-select: all;
  word-break: break-all;
}

.copy-btn {
  border-radius: 20px;
}

.pwd-warn {
  background: color-mix(in srgb, #f56c6c 10%, transparent);
  border: 0.5px solid color-mix(in srgb, #f56c6c 30%, transparent);
  color: #e06c5a;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.7;
  margin-top: 12px;
}
</style>
