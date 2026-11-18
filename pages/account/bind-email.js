const { accountApi } = require('../../utils/api');

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// 邮箱脱敏：用户名首尾各留一个字符，域名原样
function maskEmail(value) {
  const v = String(value || '');
  const at = v.indexOf('@');
  if (at <= 0) return v || '—';
  const name = v.slice(0, at);
  const domain = v.slice(at);
  const shown = name.length <= 2
    ? name.charAt(0) + '***'
    : name.charAt(0) + '***' + name.charAt(name.length - 1);
  return shown + domain;
}

Page({
  data: {
    bound: false,
    emailMasked: '',
    formMode: 'bind', // bind / change / unbind
    email: '',
    code: '',
    password: '',
    countdown: 0,
    submitting: false
  },

  onLoad(options) {
    if (options && options.mode === 'unbind') {
      this.setData({ formMode: 'unbind' });
    }
  },

  onShow() {
    this.loadStatus();
  },

  onHide() {
    this.clearTimer();
  },

  onUnload() {
    this.clearTimer();
  },

  async loadStatus() {
    try {
      const s = (await accountApi.securityStatus()) || {};
      this.setData({
        bound: !!s.emailVerified,
        // 后端返回的 email 已是脱敏串，直接展示；仅本地刚输入完整邮箱时才前端脱敏
        emailMasked: s.email || ''
      });
    } catch (err) {
      // 错误提示由 app.request 统一处理，这里保持原状态
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    if (!field) {
      return;
    }
    this.setData({ [field]: e.detail.value });
  },

  async sendCode() {
    if (this.data.countdown > 0) {
      return;
    }
    const email = (this.data.email || '').trim();
    if (!EMAIL_RE.test(email)) {
      wx.showToast({ title: '请输入正确的邮箱地址', icon: 'none' });
      return;
    }
    try {
      await accountApi.sendEmailCode({ email, scene: 'BIND' });
      wx.showToast({ title: '验证码已发送', icon: 'success' });
      this.setData({ countdown: 60 });
      this.startTimer();
    } catch (err) {
      this.handleError(err);
    }
  },

  startTimer() {
    this.clearTimer();
    this._timer = setInterval(() => {
      const next = this.data.countdown - 1;
      if (next <= 0) {
        this.setData({ countdown: 0 });
        this.clearTimer();
      } else {
        this.setData({ countdown: next });
      }
    }, 1000);
  },

  clearTimer() {
    if (this._timer) {
      clearInterval(this._timer);
      this._timer = null;
    }
  },

  async confirmBind() {
    if (this.data.submitting) {
      return;
    }
    const email = (this.data.email || '').trim();
    const code = (this.data.code || '').trim();
    if (!EMAIL_RE.test(email)) {
      wx.showToast({ title: '请输入正确的邮箱地址', icon: 'none' });
      return;
    }
    if (!code) {
      wx.showToast({ title: '请输入验证码', icon: 'none' });
      return;
    }
    this.setData({ submitting: true });
    try {
      await accountApi.bindEmail({ email, code });
      wx.showToast({ title: '绑定成功', icon: 'success' });
      this.setData({
        bound: true,
        emailMasked: maskEmail(email),
        formMode: 'bind',
        code: '',
        countdown: 0
      });
      this.clearTimer();
    } catch (err) {
      this.handleError(err);
    } finally {
      this.setData({ submitting: false });
    }
  },

  async confirmUnbind() {
    if (this.data.submitting) {
      return;
    }
    const password = this.data.password || '';
    if (!password) {
      wx.showToast({ title: '请输入当前密码', icon: 'none' });
      return;
    }
    this.setData({ submitting: true });
    try {
      await accountApi.unbindEmail({ password });
      wx.showToast({ title: '解绑成功', icon: 'success' });
      this.setData({ bound: false, emailMasked: '', password: '', formMode: 'bind' });
    } catch (err) {
      this.handleError(err);
    } finally {
      this.setData({ submitting: false });
    }
  },

  handleError(err) {
    const code = err && err.code;
    // 1008/1009/1003 已被 app.request 排除自动 toast，这里用 modal 保证完整反馈；
    // 其他错误码仍由 app.request 统一 toast，不重复弹窗。
    if (code === 1008 || code === 1009 || code === 1003) {
      wx.showModal({
        title: '操作失败',
        content: (err && err.message) || '操作失败，请稍后重试',
        showCancel: false,
        confirmText: '知道了'
      });
    }
  },

  startChange() {
    this.setData({ formMode: 'change', email: '', code: '', countdown: 0 });
    this.clearTimer();
  },

  startUnbind() {
    this.setData({ formMode: 'unbind', password: '' });
  },

  cancelForm() {
    this.setData({ formMode: 'bind' });
  }
});
