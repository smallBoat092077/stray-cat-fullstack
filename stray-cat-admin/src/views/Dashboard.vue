<template>
  <div class="page">
    <div class="page-head">
      <span class="page-title">概览</span>
      <el-button size="small" @click="loadAll">刷新</el-button>
    </div>

    <div v-if="pendingTotal > 0" class="alert alert-warning">
      <span>有 {{ stats.adoptionPending || 0 }} 条领养申请、{{ stats.recordPending || 0 }} 条投喂打卡待审核</span>
    </div>

    <div class="stat-grid">
      <div v-for="item in allStats" :key="item.key" class="glass-card stat-card">
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value">{{ stats[item.key] ?? '-' }}</div>
      </div>
    </div>

    <div class="two-col">
      <div class="glass-card" style="padding:18px">
        <div class="group-label">访问趋势（PV / 新用户）</div>
        <TrendChart :rows="channel.dailyTrend || []" :days="days" />
      </div>
      <div class="glass-card" style="padding:18px">
        <div class="group-label">渠道效果 Top5</div>
        <table class="proto-table">
          <thead><tr><th>渠道</th><th>PV</th><th>新用户</th><th>激活率</th></tr></thead>
          <tbody>
            <tr v-for="(row, i) in topChannels" :key="i">
              <td>{{ row.name }}</td>
              <td>{{ Number(row.pv || 0).toLocaleString() }}</td>
              <td>{{ row.newUsers }}</td>
              <td><span :class="tagClass(row.activationRate)">{{ pct(row.activationRate) }}</span></td>
            </tr>
            <tr v-if="topChannels.length === 0"><td colspan="4" class="muted" style="text-align:center;padding:16px">暂无数据</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="channel.hasData === false" class="alert alert-info">
      <span>所选周期内还没有带短码的访问记录，先生成短码并分享一次即可看到数据</span>
    </div>

    <div class="glass-card section-gap" style="padding:18px">
      <div class="group-label">小程序码物料生成</div>
      <div class="code-row">
        <el-input v-model="codeSid" size="small" class="code-input" placeholder="输入短码 sid" />
        <el-select v-model="envVersion" size="small" class="env-select">
          <el-option value="release" label="正式版" />
          <el-option value="trial" label="体验版" />
        </el-select>
        <el-button size="small" type="primary" :loading="codeLoading" @click="genCode">生成小程序码</el-button>
        <span v-if="codeScene" class="code-scene">scene={{ codeScene }}</span>
      </div>
      <div v-if="codeUrl" class="code-preview">
        <img :src="`/api${codeUrl}`" alt="小程序码" />
        <a :href="`/api${codeUrl}`" target="_blank" rel="noopener">打开原图</a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { adminApi, channelApi } from '../api';
import TrendChart from '../components/TrendChart.vue';

const stats = ref({});
const channel = ref({});
const days = ref(30);
const codeSid = ref('');
const codeScene = ref('');
const codeUrl = ref('');
const codeLoading = ref(false);
const envVersion = ref('release');

const allStats = [
  { key: 'catTotal',        label: '猫咪总数' },
  { key: 'catFeeding',      label: '投喂中' },
  { key: 'catWaitingAdoption', label: '待领养' },
  { key: 'catAdopted',      label: '已领养' },
  { key: 'userTotal',       label: '用户总数' },
  { key: 'volunteerTotal',  label: '志愿者' },
  { key: 'recordPending',   label: '打卡待审' },
  { key: 'adoptionPending', label: '领养待审' }
];

const topChannels = computed(() => (channel.value.channelRank || []).slice(0, 5));

function tagClass(rate) {
  if (!rate && rate !== 0) return 'muted';
  const v = Number(rate);
  if (v >= 0.06) return 'tag tag-success';
  if (v >= 0.04) return 'tag tag-info';
  return 'tag tag-warning';
}

const pendingTotal = computed(
  () => (stats.value.adoptionPending || 0) + (stats.value.recordPending || 0)
);

const channelCards = computed(() => {
  const summary = channel.value.sourceSummary || [];
  const covered = summary
    .filter((row) => row.name !== 'direct')
    .reduce((sum, row) => sum + Number(row.users || 0), 0);
  const rank = channel.value.campaignRank || [];
  const pv = rank.reduce((sum, row) => sum + Number(row.pv || 0), 0);
  const newUsers = rank.reduce((sum, row) => sum + Number(row.newUsers || 0), 0);
  return [
    { label: '带来源用户数', value: covered },
    { label: '活动访问 PV', value: pv },
    { label: '归因新用户', value: newUsers },
    { label: '整体激活率', value: pv ? `${((newUsers / pv) * 100).toFixed(1)}%` : '-' }
  ];
});

function pct(value) {
  if (value === undefined || value === null) return '-';
  return `${(Number(value) * 100).toFixed(1)}%`;
}

async function load() {
  try {
    stats.value = await adminApi.stats();
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function loadChannel() {
  try {
    channel.value = await channelApi.stats(days.value);
  } catch (err) {
    // 提示由 http 拦截器统一给
  }
}

async function genCode() {
  const sid = codeSid.value.trim();
  if (!sid) return;
  codeLoading.value = true;
  try {
    const result = await channelApi.createCode(sid, envVersion.value);
    codeUrl.value = result.url;
    codeScene.value = result.scene;
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    codeLoading.value = false;
  }
}

function loadAll() {
  load();
  loadChannel();
}

onMounted(loadAll);
</script>

<style scoped>
.page { padding: 28px 32px 40px; position: relative; z-index: 1; }

.alert { padding: 12px 16px; border-radius: 12px; margin-bottom: 18px; font-size: 13px; display: flex; align-items: center; gap: 10px; }
.alert-warning { background: rgba(251,191,36,0.12); border: 0.5px solid rgba(251,191,36,0.25); color: var(--warning, #fbbf24); }
.alert-info   { background: rgba(139,156,247,0.10);  border: 0.5px solid rgba(139,156,247,0.20); color: var(--accent, #8b9cf7); }

.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 14px; margin-bottom: 24px; }
.group-label { font-size: 12px; letter-spacing: 2px; color: var(--fg-muted); margin: 0 0 12px; text-transform: uppercase; opacity: 0.7; }
.section-gap { margin-bottom: 24px; }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; }
@media (max-width: 900px) { .two-col { grid-template-columns: 1fr; } }

.channel-head { display: flex; align-items: center; justify-content: space-between; margin: 8px 0 16px; }
.days-select  { width: 120px; }

.proto-table { width: 100%; border-collapse: separate; border-spacing: 0; font-size: 13px; }
.proto-table th { text-align: left; padding: 10px 14px; font-size: 12px; color: var(--fg-muted); font-weight: 500; border-bottom: 0.5px solid var(--border); }
.proto-table td { padding: 10px 14px; border-bottom: 0.5px solid var(--border); }
.proto-table tr:last-child td { border-bottom: none; }
.proto-table tr:hover td { background: var(--glass-hi); }

.tag { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 20px; font-size: 11px; font-weight: 500; }
.tag-success { background: rgba(52,211,153,0.15); color: var(--success, #34d399); }
.tag-danger  { background: rgba(248,113,113,0.15); color: var(--danger, #f87171); }
.tag-warning { background: rgba(251,191,36,0.15); color: var(--warning, #fbbf24); }
.tag-info    { background: rgba(139,156,247,0.15); color: var(--accent, #8b9cf7); }

.code-row  { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.code-input { width: 200px; }
.env-select { width: 110px; }
.code-scene { font-size: 12px; color: var(--fg-muted); }
.code-preview { margin-top: 12px; display: flex; align-items: flex-end; gap: 16px; }
.code-preview img { width: 180px; height: 180px; border: 0.5px solid var(--border); border-radius: 8px; }
</style>
