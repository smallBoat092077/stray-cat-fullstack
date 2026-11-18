<template>
  <div class="page">
    <div class="page-head">
      <h2>关于我们配置</h2>
      <p class="head-hint">配置小程序「我的 → 关于我们」页面内容，保存后客户端进入页面即拉到最新值，无需发版。</p>
    </div>

    <el-form :model="form" label-width="110px" class="about-form" v-loading="loading">
      <el-form-item label="应用名">
        <el-input v-model="form.appName" maxlength="50" show-word-limit />
      </el-form-item>
      <el-form-item label="版本号">
        <el-input v-model="form.version" maxlength="20" style="width: 220px" placeholder="如 v1.1.0" />
        <div class="field-hint">建议随发版更新；客户端版本徽标直接展示此处内容</div>
      </el-form-item>
      <el-form-item label="品牌 slogan">
        <el-input v-model="form.slogan" maxlength="100" placeholder="如：遇一只猫 · 安一个家" />
      </el-form-item>
      <el-form-item label="理念主句">
        <el-input v-model="form.mottoMain" type="textarea" :rows="2" maxlength="200" show-word-limit
                  placeholder="每行一句，如：让领养代替购买 / 爱不流浪" />
        <div class="field-hint">换行即为一行，页面上每行居中加粗展示</div>
      </el-form-item>
      <el-form-item label="理念副句">
        <el-input v-model="form.mottoSub" maxlength="200" placeholder="如：遇一只猫，安一个家" />
      </el-form-item>
      <el-form-item label="管理员电话">
        <el-input v-model="form.phone" maxlength="11" style="width: 220px" placeholder="11 位手机号" />
        <div class="field-hint">用户点击直接拨打；仅支持 11 位手机号</div>
      </el-form-item>
      <el-form-item label="联系邮箱">
        <el-input v-model="form.email" maxlength="120" style="width: 320px" />
        <div class="field-hint">用户点击复制到剪贴板</div>
      </el-form-item>
      <el-form-item label="联系提示">
        <el-input v-model="form.contactTip" type="textarea" :rows="2" maxlength="300" show-word-limit
                  placeholder="如：欢迎在工作时间（9:00–21:00）与我们联系" />
      </el-form-item>
      <el-form-item label="版权文案">
        <el-input v-model="form.copyright" type="textarea" :rows="2" maxlength="300" show-word-limit />
        <div class="field-hint">页面底部居中展示，可换行</div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button @click="load">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="preview-title">预览（近似小程序实际排版）</div>
    <div class="phone-preview">
      <div class="pv-brand">
        <div class="pv-paw">🐾</div>
        <div class="pv-name">{{ form.appName || '—' }}</div>
        <div class="pv-slogan">{{ form.slogan || '—' }}</div>
        <div class="pv-ver">当前版本 {{ form.version || '—' }}</div>
      </div>
      <div class="pv-card">
        <div class="pv-title">我们的理念</div>
        <div class="pv-motto">
          <div v-for="(line, i) in mottoLines" :key="i" class="pv-motto-line">{{ line }}</div>
        </div>
        <div class="pv-orn"></div>
        <div class="pv-sub" v-if="form.mottoSub">{{ form.mottoSub }}</div>
      </div>
      <div class="pv-card">
        <div class="pv-title">联系我们</div>
        <div class="pv-row" v-if="form.phone">
          <span class="pv-ico">📞</span>
          <span class="pv-body"><small>管理员电话</small><b>{{ form.phone }}</b></span>
          <span class="pv-act">点击拨打</span>
        </div>
        <div class="pv-row" v-if="form.email">
          <span class="pv-ico">✉️</span>
          <span class="pv-body"><small>联系邮箱</small><b>{{ form.email }}</b></span>
          <span class="pv-act">点击复制</span>
        </div>
        <div class="pv-tip" v-if="form.contactTip">{{ form.contactTip }}</div>
      </div>
      <div class="pv-cp">
        <div v-for="(line, i) in copyrightLines" :key="i">{{ line }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { aboutApi } from '../api';

const loading = ref(false);
const saving = ref(false);
const form = ref({
  appName: '', version: '', slogan: '', mottoMain: '', mottoSub: '',
  phone: '', email: '', contactTip: '', copyright: ''
});

const mottoLines = computed(() => String(form.value.mottoMain || '').split('\n').filter(Boolean));
const copyrightLines = computed(() => String(form.value.copyright || '').split('\n').filter(Boolean));

async function load() {
  loading.value = true;
  try {
    const data = await aboutApi.get();
    form.value = {
      appName: data?.appName || '', version: data?.version || '', slogan: data?.slogan || '',
      mottoMain: data?.mottoMain || '', mottoSub: data?.mottoSub || '',
      phone: data?.phone || '', email: data?.email || '',
      contactTip: data?.contactTip || '', copyright: data?.copyright || ''
    };
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (form.value.phone && !/^1\d{10}$/.test(form.value.phone.trim())) {
    ElMessage.warning('电话需为 11 位手机号');
    return;
  }
  saving.value = true;
  try {
    await aboutApi.save({ ...form.value });
    ElMessage.success('已保存，客户端即时生效');
    load();
  } catch (err) {
    // 提示由 http 拦截器统一给
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.page-head h2 { margin: 0 0 6px; font-size: 18px; }
.head-hint { font-size: 12px; color: var(--fg-muted, #909399); margin: 0 0 18px; }
.about-form { max-width: 640px; }
.field-hint { font-size: 12px; color: var(--fg-muted, #909399); line-height: 1.6; margin-top: 4px; }

.preview-title { font-size: 14px; font-weight: 600; margin: 26px 0 12px; }
.phone-preview {
  width: 320px; background: #FDFBF4; border: 1px solid #EAE3D2; border-radius: 16px;
  padding: 24px 22px 20px;
}
.pv-brand { text-align: center; padding-bottom: 14px; }
.pv-paw { width: 56px; height: 56px; margin: 0 auto 10px; border-radius: 16px;
  background: #4A6B57; display: flex; align-items: center; justify-content: center; font-size: 28px; }
.pv-name { font-size: 18px; font-weight: 700; letter-spacing: 2px; }
.pv-slogan { font-size: 12px; color: #8C8574; margin-top: 4px; letter-spacing: 3px; }
.pv-ver { display: inline-block; margin-top: 8px; padding: 2px 12px; background: #F7F1E3;
  border: 1px solid #E7DDC4; border-radius: 999px; font-size: 11px; color: #B98A2F; font-weight: 600; }
.pv-card { background: #fff; border: 1px solid #EAE3D2; border-radius: 12px; padding: 14px 16px; margin-top: 12px; }
.pv-title { font-size: 11px; color: #B98A2F; font-weight: 700; letter-spacing: 2px; margin-bottom: 8px; }
.pv-motto { text-align: center; }
.pv-motto-line { font-size: 15px; font-weight: 700; color: #3A5A47; line-height: 1.9; letter-spacing: 1px; }
.pv-orn { width: 100px; height: 1px; margin: 10px auto 0;
  background: linear-gradient(90deg, transparent, #E7DDC4, transparent); }
.pv-sub { margin-top: 8px; text-align: center; font-size: 12px; color: #8C8574; letter-spacing: 2px; }
.pv-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.pv-row + .pv-row { border-top: 1px dashed #EAE3D2; }
.pv-ico { width: 32px; height: 32px; border-radius: 8px; background: #F7F1E3;
  border: 1px solid #E7DDC4; display: flex; align-items: center; justify-content: center; font-size: 15px; }
.pv-body { flex: 1; display: flex; flex-direction: column; }
.pv-body small { font-size: 10px; color: #8C8574; }
.pv-body b { font-size: 13px; margin-top: 1px; }
.pv-act { font-size: 10px; color: #B98A2F; }
.pv-tip { font-size: 10px; color: #8C8574; margin-top: 6px; line-height: 1.6; }
.pv-cp { text-align: center; font-size: 10px; color: #8C8574; margin-top: 16px; line-height: 1.8; }
</style>
