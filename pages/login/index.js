const app = getApp();

Page({
  data: {
    mode: 'login',
    username: '',
    password: '',
    agreed: false,
    submitting: false
  },

  switchMode(e) {
    this.setData({ mode: e.currentTarget.dataset.mode });
  },

  onToggleAgree() {
    this.setData({ agreed: !this.data.agreed });
  },

  onViewPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/index' });
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value });
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value });
  },

  onBack() {
    wx.navigateBack();
  },

  onSubmit() {
    const { mode, username, password, agreed } = this.data;

    if (!username || !password) {
      wx.showToast({ title: '请填写账号和密码', icon: 'none' });
      return;
    }
    if (mode === 'register' && password.length < 6) {
      wx.showToast({ title: '密码至少6位', icon: 'none' });
      return;
    }
    if (!agreed) {
      wx.showToast({ title: '请先阅读并同意隐私政策', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    const action = mode === 'login'
      ? app.passwordLogin(username, password)
      : app.register(username, password);

    action
      .then(() => {
        wx.showToast({ title: mode === 'login' ? '登录成功' : '注册成功', icon: 'success' });
        setTimeout(() => wx.navigateBack(), 1200);
      })
      .catch((err) => {
        wx.showToast({ title: err.message || '操作失败', icon: 'none' });
      })
      .finally(() => {
        this.setData({ submitting: false });
      });
  }
});
