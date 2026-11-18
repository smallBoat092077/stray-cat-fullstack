const { forgotPasswordApi } = require('../../utils/api');

const PHONE_RE = /^1\d{10}$/;
const REASON_MAX = 200;

// 结果页只展示打码后的手机号：前 3 + **** + 后 4
function maskPhone(phone) {
  const value = String(phone || '');
  if (value.length === 11) {
    return value.slice(0, 3) + '****' + value.slice(7);
  }
  return value;
}

Page({
  data: {
    form: { username: '', nickname: '', phone: '', reason: '' },
    submitting: false,
    submitted: false,
    phoneMasked: ''
  },

  onFieldInput(e) {
    const field = e.currentTarget.dataset.field;
    if (!field) {
      return;
    }
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  async submit() {
    if (this.data.submitting) {
      return;
    }
    const form = this.data.form;
    const username = (form.username || '').trim();
    const nickname = (form.nickname || '').trim();
    const phone = (form.phone || '').trim();
    const reason = (form.reason || '').trim();

    if (!username || !nickname) {
      wx.showToast({ title: '请填写账号和昵称', icon: 'none' });
      return;
    }
    if (!PHONE_RE.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' });
      return;
    }
    if (reason.length > REASON_MAX) {
      wx.showToast({ title: `情况说明最多 ${REASON_MAX} 字`, icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    try {
      await forgotPasswordApi.apply({ username, nickname, phone, reason });
      this.setData({
        submitted: true,
        phoneMasked: maskPhone(phone)
      });
    } catch (err) {
      // 错误 toast（业务报错 / 限频 / 网络错误）已由 app.request 统一弹出，这里不重复提示
    } finally {
      this.setData({ submitting: false });
    }
  },

  goLogin() {
    const pages = getCurrentPages();
    const loginIndex = pages.findIndex((page) => page.route === 'pages/login/index');
    if (loginIndex >= 0) {
      // 从登录页进来的，直接退回登录页
      wx.navigateBack({ delta: pages.length - 1 - loginIndex });
    } else {
      // 从「我的」页进来的，重定向到登录页，避免返回又落回结果页
      wx.redirectTo({ url: '/pages/login/index' });
    }
  }
});
