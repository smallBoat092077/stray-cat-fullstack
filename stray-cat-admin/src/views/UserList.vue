<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">用户与角色</span>
      <div style="display: flex; gap: 8px">
        <el-button size="small" @click="load">刷新</el-button>
        <el-button type="primary" size="small" @click="openCreate">＋ 新增用户</el-button>
      </div>
    </div>

    <div class="filter-row">
      <el-input v-model="keyword" placeholder="昵称或账号" clearable style="width: 220px" @keyup.enter="onFilterChange" />
      <el-select v-model="role" clearable placeholder="全部角色" style="width: 150px" @change="onFilterChange">
        <el-option v-for="key in [0, 1, 2, 3]" :key="key" :label="dictText(ROLE_MAP, key)" :value="key" />
      </el-select>
      <el-button type="primary" size="small" @click="onFilterChange">搜索</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="昵称" min-width="140">
        <template #default="{ row }">{{ row.nickname || '未设置' }}</template>
      </el-table-column>
      <el-table-column label="账号" min-width="130">
        <template #default="{ row }">{{ row.username || '微信登录' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="dictType(ROLE_MAP, row.role)">{{ dictText(ROLE_MAP, row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="信用积分" width="100">
        <template #default="{ row }">{{ row.creditScore ?? 100 }}</template>
      </el-table-column>
      <el-table-column label="猫粮余额" width="100">
        <template #default="{ row }">{{ row.coinBalance ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span :class="{ muted: row.status === 0 }">{{ row.status === 0 ? '已禁用' : '正常' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="安全状态" width="150">
        <template #default="{ row }">
          <el-tag :type="securityStatus(row).type" size="small">{{ securityStatus(row).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-select
            :model-value="row.role"
            size="small"
            style="width: 110px"
            @change="(value) => grantRole(row, value)"
          >
            <el-option v-for="key in GRANTABLE_ROLES" :key="key" :label="dictText(ROLE_MAP, key)" :value="key" />
          </el-select>
          <el-button size="small" @click="goLedger(row)">流水</el-button>
          <el-button v-if="row.lockStage > 0" link type="danger" size="small" @click="unlockAccount(row)">解锁</el-button>
          <el-button v-if="!isSelf(row)" link type="danger" size="small" @click="openResetDialog(row)">重置密码</el-button>
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

    <!-- 新增用户 -->
    <el-dialog v-model="createVisible" title="新增用户" width="460px" :close-on-click-modal="false" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="createForm.username" placeholder="3–50 字符，字母/数字/下划线" maxlength="50" />
          <div class="form-hint">全局唯一，用于账号密码登录</div>
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" show-password placeholder="6–20 字符" maxlength="20" />
          <div class="form-hint">请线下转告用户；系统以 BCrypt 加密存储，无法查看原密码</div>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createForm.nickname" placeholder="选填，留空则用账号" maxlength="30" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option v-for="key in CREATE_ROLES" :key="key" :label="dictText(ROLE_MAP, key)" :value="key" />
          </el-select>
          <div class="form-hint">不能直接授予「领养人」，该角色由领养审核自动流转</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createBusy" @click="submitCreate">确定新增</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码：先选投递方式 -->
    <el-dialog v-model="resetDialogVisible" title="重置密码" width="460px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="账号">
          <span>{{ resetTarget?.username || '—' }}</span>
        </el-form-item>
        <el-form-item label="重置方式">
          <el-radio-group v-model="resetForm.delivery">
            <el-radio value="email">发送重置验证码到邮箱（推荐）</el-radio>
            <el-radio value="manual">生成临时密码，线下转告</el-radio>
          </el-radio-group>
          <div class="form-hint" v-if="resetForm.delivery === 'email'">
            需该用户已绑定并验证邮箱；验证码 30 分钟有效，用户在小程序自行设置新密码，管理员不接触新密码。未绑定邮箱会提示改用临时密码方式。
          </div>
          <div class="form-hint" v-else>
            原密码立即失效并自动解锁；临时密码仅显示一次，请电话转告用户。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetBusy" @click="submitReset">
          {{ resetForm.delivery === 'email' ? '发送验证码' : '确认重置' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 重置密码结果：临时密码一次性只读展示 / 邮件发码成功提示 -->
    <el-dialog
      v-model="resetResultVisible"
      :title="resetResult.delivery === 'email' ? '重置验证码已发送' : '密码已重置'"
      width="460px"
      :close-on-click-modal="false"
      :show-close="true"
      @closed="clearResetResult"
    >
      <template v-if="resetResult.delivery === 'email'">
        <div class="pwd-card">
          <div class="pwd-label">账号 <span class="mono">{{ resetResult.username }}</span> 的重置验证码已发送至</div>
          <div class="pwd-value">{{ resetResult.email || '已验证邮箱' }}</div>
        </div>
        <div class="pwd-warn">
          验证码 <strong>30 分钟内有效</strong>，错 5 次作废。请告知用户在小程序「登录页 → 邮箱验证码重置密码」中自行设置新密码；新密码管理员不可见。若用户反馈未收到邮件，请确认邮箱无误后重新发送。
        </div>
      </template>
      <template v-else>
        <div class="pwd-card">
          <div class="pwd-label">账号 <span class="mono">{{ resetResult.username }}</span> 的临时密码</div>
          <div class="pwd-value">{{ resetResult.temporaryPassword }}</div>
          <el-button size="small" class="copy-btn" @click="copyPwd">一键复制</el-button>
        </div>
        <div class="pwd-warn">
          此密码<strong>仅显示这一次</strong>，关闭后无法再次查看；请立即线下电话转告用户。服务端不保存明文，操作日志也不记录密码。
        </div>
      </template>
      <template #footer>
        <el-button v-if="resetResult.delivery === 'email'" type="primary" @click="resetResultVisible = false">我知道了</el-button>
        <el-button v-else type="primary" @click="resetResultVisible = false">我已复制并保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { h, nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi, adminUserApi, userApi } from '../api';
import { useAuthStore } from '../stores/auth';
import { invalidateLookups } from '../utils/lookups';
import { ROLE_MAP, dictText, dictType, formatTime } from '../utils/dict';

const router = useRouter();
const auth = useAuthStore();
// 服务端只接受 0/1/3，「领养人(2)」由领养审核通过后自动流转，不能手授
const GRANTABLE_ROLES = [0, 1, 3];
// 新增用户可选角色：普通用户 / 志愿者 / 管理员（管理员需二次确认），不含领养人 2
const CREATE_ROLES = [0, 1, 3];
const rows = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
const keyword = ref('');
const role = ref(null);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const params = { pageNum: pageNum.value, pageSize };
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim();
    }
    if (role.value !== null && role.value !== '') {
      params.role = role.value;
    }
    const page = await adminApi.users(params);
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

// 安全状态：status=0（管理员禁用）优先级最高；lockStage 缺失时显示「—」，兼容后端未同步
function securityStatus(row) {
  if (row.status === 0) {
    return { type: 'info', text: '已禁用' };
  }
  if (row.lockStage === undefined || row.lockStage === null) {
    return { type: 'info', text: '—' };
  }
  if (row.lockStage === 1) {
    if (row.lockUntil) {
      const until = new Date(String(row.lockUntil).replace(' ', 'T')).getTime();
      const remain = until - Date.now();
      if (Number.isFinite(remain) && remain > 0) {
        return { type: 'warning', text: `临时锁 · 剩 ${Math.floor(remain / 3600000)} 小时` };
      }
    }
    return { type: 'warning', text: '临时锁 · 已到期' };
  }
  if (row.lockStage === 2) {
    return { type: 'danger', text: '永久锁' };
  }
  return { type: 'success', text: '正常' };
}

// 解锁：只清安全状态，不改密码、不恢复管理员手动禁用（status）
async function unlockAccount(row) {
  const account = row.username || `用户${row.id}`;
  const stageText = row.lockStage === 2 ? '永久锁' : '临时锁';
  try {
    await ElMessageBox.confirm(
      `账号 ${account} 当前为${stageText}状态。解锁后失败计数清零，可重新使用密码登录；密码本身不会被修改。如确认账号异常，请改用「重置密码」，该操作会自动解锁。`,
      '确认解除账号锁定？',
      { type: 'warning', confirmButtonText: '确认解锁', cancelButtonText: '取消' }
    );
  } catch (err) {
    return;
  }
  try {
    await adminUserApi.unlock(row.id);
    ElMessage.success('账号已解锁');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function grantRole(row, nextRole) {
  const label = dictText(ROLE_MAP, nextRole);
  try {
    await ElMessageBox.confirm(
      `把「${row.nickname || row.username || '用户' + row.id}」的角色改为${label}？志愿者可以接单投喂，管理员可以进这个后台。`,
      '确认修改角色',
      { type: 'warning' }
    );
  } catch (err) {
    return;
  }
  try {
    await userApi.grantRole(row.id, nextRole);
    ElMessage.success('角色已更新');
    invalidateLookups();
    load();
  } catch (err) {
    load();
  }
}

function goLedger(row) {
  router.push({ path: '/ledger', query: { userId: row.id } });
}

// ---------- 新增用户 ----------
const createVisible = ref(false);
const createBusy = ref(false);
const createFormRef = ref(null);
const createForm = ref({ username: '', password: '', nickname: '', role: 0 });
const createRules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{3,50}$/, message: '3–50 字符，仅字母/数字/下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6–20 字符', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
};

function openCreate() {
  createForm.value = { username: '', password: '', nickname: '', role: 0 };
  createVisible.value = true;
  nextTick(() => createFormRef.value?.clearValidate());
}

function resetCreateForm() {
  createForm.value = { username: '', password: '', nickname: '', role: 0 };
  createFormRef.value?.clearValidate();
}

async function submitCreate() {
  let valid = false;
  try {
    valid = await createFormRef.value.validate();
  } catch (err) {
    valid = false;
  }
  if (!valid) return;

  if (createForm.value.role === 3) {
    try {
      await ElMessageBox.confirm(
        '确认授予管理员权限？管理员可以进入后台并管理所有功能，请确认该账号确需此权限。',
        '授予管理员权限',
        { type: 'warning', confirmButtonText: '确认授予', cancelButtonText: '取消' }
      );
    } catch (err) {
      return;
    }
  }

  createBusy.value = true;
  try {
    await adminUserApi.create({
      username: createForm.value.username.trim(),
      password: createForm.value.password,
      nickname: createForm.value.nickname.trim(),
      role: createForm.value.role
    });
    ElMessage.success('用户已创建');
    createVisible.value = false;
    load();
  } catch (err) {
    // 重名 / 格式错误由 http 拦截器统一展示后端 message
  } finally {
    createBusy.value = false;
  }
}

// ---------- 重置密码 ----------
// 当前登录用户 id 取自 pinia auth store（登录时由 /user/login/password 写入，同步到 localStorage admin-user）
function isSelf(row) {
  return !!auth.user && row.id === auth.user.id;
}

const resetResultVisible = ref(false);
const resetResult = ref({ username: '', temporaryPassword: '', delivery: 'manual', email: '' });
// 方案 C：重置密码先选投递方式（邮件重置验证码 / 临时密码线下转告）
const resetDialogVisible = ref(false);
const resetTarget = ref(null);
const resetForm = ref({ delivery: 'email' });
const resetBusy = ref(false);

function openResetDialog(row) {
  resetTarget.value = row;
  resetForm.value = { delivery: 'email' };
  resetDialogVisible.value = true;
}

async function submitReset() {
  const row = resetTarget.value;
  if (!row || resetBusy.value) return;
  const name = row.nickname || row.username || `用户${row.id}`;
  const byEmail = resetForm.value.delivery === 'email';
  try {
    await ElMessageBox.confirm(
      h('div', [
        h('div', byEmail
          ? `向「${name}（${row.username || '—'}）」的已验证邮箱发送重置验证码？用户将在小程序自行设置新密码。`
          : `重置「${name}（${row.username || '—'}）」的密码？原密码立即失效，请确认由你本人操作。`),
        h('div', { style: 'margin-top:8px;color:var(--fg-muted)' },
          byEmail ? '需该用户已绑定验证邮箱；此方式不会生成临时密码' : '该操作会同时解除账号锁定状态')
      ]),
      byEmail ? '发送重置验证码' : '生成临时密码',
      { type: 'warning', confirmButtonText: byEmail ? '发送验证码' : '确认重置', cancelButtonText: '取消' }
    );
  } catch (err) {
    return;
  }
  resetBusy.value = true;
  try {
    const data = await adminUserApi.resetPassword(row.id, { delivery: resetForm.value.delivery });
    resetDialogVisible.value = false;
    resetResult.value = {
      username: data?.username || row.username || '',
      temporaryPassword: data?.temporaryPassword || '',
      delivery: data?.delivery || 'manual',
      email: data?.email || ''
    };
    resetResultVisible.value = true;
  } catch (err) {
    // 提示由 http 拦截器统一给（未绑定邮箱返回 1015）
  } finally {
    resetBusy.value = false;
  }
}

async function copyPwd() {
  const text = resetResult.value.temporaryPassword;
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制临时密码');
  } catch (err) {
    ElMessage.warning('自动复制失败，请手动选择密码复制');
  }
}

function clearResetResult() {
  resetResult.value = { username: '', temporaryPassword: '', delivery: 'manual', email: '' };
}

onMounted(load);
</script>

<style scoped>
.form-hint {
  font-size: 11px;
  color: var(--fg-muted);
  line-height: 1.5;
  margin-top: 4px;
}

.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
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
