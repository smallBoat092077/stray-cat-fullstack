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

  onForgotPassword() {
    wx.navigateTo({ url: '/pages/login/forgot-password' });
  },

  onResetByCode() {
    wx.navigateTo({ url: '/pages/account/reset-by-code' });
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
        const code = err && err.code;
        const message = (err && err.message) || '操作失败';

        // 1008 临时锁：完整展示后端 message（含解锁时间），不让 toast 截断
        if (code === 1008) {
          wx.showModal({
            title: '账号已临时锁定',
            content: message,
            confirmText: '使用微信登录',
            cancelText: '知道了',
            success: (res) => {
              if (!res.confirm) {
                return;
              }
              wx.showLoading({ title: '登录中...' });
              app.login().then(() => {
                wx.hideLoading();
                wx.showToast({ title: '登录成功', icon: 'success' });
                setTimeout(() => wx.navigateBack(), 1200);
              }).catch((wxErr) => {
                wx.hideLoading();
                wx.showToast({ title: (wxErr && wxErr.message) || '登录失败', icon: 'none' });
              });
            }
          });
          return;
        }

        // 1009 永久锁：引导走已有的找回申请，管理员处理时会自动解锁
        if (code === 1009) {
          wx.showModal({
            title: '账号已被锁定',
            content: message,
            confirmText: '提交找回申请',
            cancelText: '知道了',
            success: (res) => {
              if (res.confirm) {
                wx.navigateTo({ url: '/pages/login/forgot-password' });
              }
            }
          });
          return;
        }

        // 1003 失败次数提示：剩余次数由后端给出，原样展示
        if (code === 1003) {
          wx.showModal({
            title: '登录失败',
            content: message,
            showCancel: false,
            confirmText: '知道了'
          });
          return;
        }

        wx.showToast({ title: message, icon: 'none' });
      })
      .finally(() => {
        this.setData({ submitting: false });
      });
  }
});
