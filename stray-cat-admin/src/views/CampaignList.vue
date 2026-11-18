<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">活动管理</span>
      <div class="head-actions">
        <el-button size="small" @click="load">刷新</el-button>
        <el-button size="small" type="primary" @click="openDialog()">新建活动</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon class="hint">
      活动代号与分享短码 campaign 对齐。运营投放：为活动生成带参小程序码，把图片插进公众号图文即可，
      用户扫码直达小程序活动页，访问与转化自动进渠道看板。
    </el-alert>

    <el-table :data="rows" v-loading="loading" size="small" border empty-text="暂无活动">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="code" label="活动代号" min-width="130" />
      <el-table-column prop="name" label="活动名" min-width="150" />
      <el-table-column label="状态" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" size="small">
            {{ scope.row.status === 1 ? '上线' : '下线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="起止时间" min-width="180">
        <template #default="scope">{{ timeText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="openDialog(scope.row)">编辑</el-button>
          <el-button size="small" @click="toggle(scope.row)">
            {{ scope.row.status === 1 ? '下线' : '上线' }}
          </el-button>
          <el-button size="small" @click="remind(scope.row)">发提醒</el-button>
          <el-button size="small" type="primary" :loading="codeBusy[scope.row.id]" @click="genCode(scope.row)">
            小程序码
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑/新建弹窗 -->
    <el-dialog v-model="dialog.visible" :title="form.id ? '编辑活动' : '新建活动'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="活动代号" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 diary-w3（限小写字母/数字/连字符）" />
        </el-form-item>
        <el-form-item label="活动名" required>
          <el-input v-model="form.name" maxlength="60" placeholder="如：一只猫的回家日记 · 第三周" />
        </el-form-item>
        <el-form-item label="活动介绍">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="封面图">
          <div class="cover-row">
            <input ref="coverInput" type="file" accept="image/*" style="display:none" @change="onCoverSelected" />
            <el-button size="small" @click="$refs.coverInput.click()">上传封面</el-button>
            <img v-if="form.coverUrl" :src="`/api${form.coverUrl}`" class="cover-preview" alt="封面" />
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上线" inactive-text="下线" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 小程序码结果 -->
    <el-dialog v-model="codeDialog.visible" title="活动小程序码" width="320px">
      <div class="code-box">
        <img v-if="codeDialog.url" :src="`/api${codeDialog.url}`" alt="小程序码" />
        <p class="code-meta">scene={{ codeDialog.scene }} · 渠道 mp_article · 活动 {{ codeDialog.code }}</p>
        <a v-if="codeDialog.url" :href="`/api${codeDialog.url}`" download>下载图片</a>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import http from '../api/http';
import { campaignApi, channelApi } from '../api';

const rows = ref([]);
const loading = ref(false);
const saving = ref(false);
const dialog = reactive({ visible: false });
const codeDialog = reactive({ visible: false, url: '', scene: '', code: '' });
const codeBusy = reactive({});
const coverInput = ref(null);
const form = reactive({ id: null, code: '', name: '', description: '', coverUrl: '', status: 1 });

async function load() {
  loading.value = true;
  try {
    const page = await campaignApi.list({ pageNum: 1, pageSize: 50 });
    rows.value = page.records || [];
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    loading.value = false;
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id, code: row.code, name: row.name,
      description: row.description || '', coverUrl: row.coverUrl || '', status: row.status
    });
  } else {
    Object.assign(form, { id: null, code: '', name: '', description: '', coverUrl: '', status: 1 });
  }
  dialog.visible = true;
}

async function save() {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.warning('活动代号与名称必填');
    return;
  }
  saving.value = true;
  try {
    await campaignApi.save({ ...form });
    ElMessage.success('已保存');
    dialog.visible = false;
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    saving.value = false;
  }
}

async function toggle(row) {
  try {
    await campaignApi.status(row.id, row.status === 1 ? 0 : 1);
    ElMessage.success(row.status === 1 ? '已下线' : '已上线');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function remind(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      '将发送给访问过本活动的用户（站内消息必达，已授权用户同时收到微信通知）',
      `发送活动提醒 · ${row.name}`,
      { inputPlaceholder: '提醒文案，留空用默认', inputValue: '' }
    );
    const result = await campaignApi.remind(row.id, value);
    ElMessage.success(`已发送 ${result.sent} 条`);
  } catch (err) {
    if (err === 'cancel' || err === 'close') return;
    // 提示由 http 拦截器统一给
  }
}

/**
 * 生成活动小程序码：先走公开的 /share/link 造一条 mp_article 短码
 * （campaign=活动代号，落地 pages/campaign/index），再用 P0 的码接口出图。
 * 注意活动页路径必须已随小程序发布，否则微信报 41030。
 */
async function genCode(row) {
  codeBusy[row.id] = true;
  try {
    const link = await http.post('/share/link', {
      channel: 'mp_article',
      campaign: row.code,
      bizType: 'campaign',
      bizId: row.id,
      landingPage: 'pages/campaign/index'
    });
    const code = await channelApi.createCode(link.sid, 'release');
    codeDialog.url = code.url;
    codeDialog.scene = code.scene;
    codeDialog.code = row.code;
    codeDialog.visible = true;
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    codeBusy[row.id] = false;
  }
}

function onCoverSelected(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  e.target.value = '';
  const fd = new FormData();
  fd.append('file', file);
  http.post('/common/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    .then((path) => { form.coverUrl = path; })
    .catch(() => {});
}

function timeText(row) {
  const s = row.startTime ? row.startTime.replace('T', ' ').slice(0, 10) : '不限';
  const e = row.endTime ? row.endTime.replace('T', ' ').slice(0, 10) : '不限';
  return `${s} ~ ${e}`;
}

onMounted(load);
</script>

<style scoped>
.hint {
  margin-bottom: 16px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.cover-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover-preview {
  width: 96px;
  height: auto;
  border: 1px solid #e6e3da;
  border-radius: 4px;
  display: block;
}

.code-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.code-box img {
  width: 200px;
  height: 200px;
  border: 1px solid #e6e3da;
}

.code-meta {
  font-size: 12px;
  color: #7b8a84;
  margin: 0;
}
</style>
