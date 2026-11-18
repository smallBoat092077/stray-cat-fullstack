const { accountApi } = require('../../utils/api');

// 账号脱敏：首尾各留一个字符
function maskAccount(value) {
  const v = String(value || '');
  if (!v) return '—';
  if (v.length <= 2) return v.charAt(0) + '*';
  return v.charAt(0) + '***' + v.charAt(v.length - 1);
}

// 兼容后端 ISO 'yyyy-MM-ddTHH:mm:ss' 与 'yyyy-MM-dd HH:mm:ss'；
// iOS 内核不认带横杠 / 带 T 的写法，统一归一化成 'yyyy/MM/dd HH:mm:ss'
function toTimestamp(value) {
  if (!value) return 0;
  const normalized = String(value).replace('T', ' ').replace(/-/g, '/');
  const t = new Date(normalized).getTime();
  return isNaN(t) ? 0 : t;
}

function remainText(lockUntil) {
  const end = toTimestamp(lockUntil);
  if (!end) return '';
  const diff = end - Date.now();
  if (diff <= 0) return '即将解锁';
  const total = Math.floor(diff / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n) => (n < 10 ? '0' + n : '' + n);
  if (h > 0) return `${h} 小时 ${pad(m)} 分 ${pad(s)} 秒`;
  if (m > 0) return `${m} 分 ${pad(s)} 秒`;
  return `${s} 秒`;
}

Page({
  data: {
    loading: true,
    accountMasked: '—',
    hasPassword: false,
    wechatBound: false,
    emailMasked: '',
    emailVerified: false,
    lockStage: 0,
    lockUntil: null,
    remain: ''
  },

  onLoad() {
    const info = getApp().globalData.userInfo || {};
    this.setData({ accountMasked: maskAccount(info.username || info.nickname) });
  },

  onShow() {
    this.loadStatus();
  },

  onHide() {
    this.stopTimer();
  },

  onUnload() {
    this.stopTimer();
  },

  async loadStatus() {
    try {
      const s = (await accountApi.securityStatus()) || {};
      const app = getApp();
      const info = app.globalData.userInfo || {};
      const lockStage = Number(s.lockStage) || 0;
      this.setData({
        loading: false,
        accountMasked: maskAccount(s.username || info.username || info.nickname),
        // 仅当后端明确返回 false 时视为纯微信用户，避免字段缺省时误隐藏入口
        hasPassword: s.hasPassword !== false,
        wechatBound: s.wechatBound !== undefined ? !!s.wechatBound : !!info.openid,
        // 后端返回的 email 已是脱敏串，前端不能再脱一次
        emailMasked: s.email || '',
        emailVerified: !!s.emailVerified,
        lockStage,
        lockUntil: s.lockUntil || null,
        remain: lockStage === 1 ? remainText(s.lockUntil) : ''
      });
      if (lockStage === 1) {
        this.startTimer(s.lockUntil);
      } else {
        this.stopTimer();
      }
    } catch (err) {
      // 错误提示由 app.request 统一处理；保留默认态，不误判成纯微信用户
      this.setData({ loading: false });
      this.stopTimer();
    }
  },

  startTimer(lockUntil) {
    this.stopTimer();
    this._lockUntil = lockUntil;
    this._timer = setInterval(() => {
      const text = remainText(this._lockUntil);
      this.setData({ remain: text });
      if (!text || text === '即将解锁') {
        this.stopTimer();
      }
    }, 1000);
  },

  stopTimer() {
    if (this._timer) {
      clearInterval(this._timer);
      this._timer = null;
    }
  },

  goChangePassword() {
    wx.navigateTo({ url: '/pages/account/change-password' });
  },

  goBindEmail(e) {
    const mode = e && e.currentTarget && e.currentTarget.dataset.mode;
    wx.navigateTo({
      url: mode ? `/pages/account/bind-email?mode=${mode}` : '/pages/account/bind-email'
    });
  },

  goForgotPassword() {
    wx.navigateTo({ url: '/pages/login/forgot-password' });
  }
});
