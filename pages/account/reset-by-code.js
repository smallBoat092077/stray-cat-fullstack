const { accountApi } = require('../../utils/api');

Page({
  data: {
    username: '',
    code: '',
    newPassword: '',
    confirmPassword: '',
    showNew: false,
    showConfirm: false,
    submitting: false
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  toggleShow(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: !this.data[field] });
  },

  submit() {
    const { username, code, newPassword, confirmPassword, submitting } = this.data;
    if (submitting) {
      return;
    }
    if (!username || !username.trim()) {
      wx.showToast({ title: '请填写账号', icon: 'none' });
      return;
    }
    if (!code || !code.trim()) {
      wx.showToast({ title: '请填写重置验证码', icon: 'none' });
      return;
    }
    if (!newPassword || newPassword.length < 6 || newPassword.length > 20) {
      wx.showToast({ title: '新密码需为 6–20 位', icon: 'none' });
      return;
    }
    if (newPassword !== confirmPassword) {
      wx.showToast({ title: '两次输入的新密码不一致', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    accountApi.resetPasswordByCode({
      username: username.trim(),
      code: code.trim(),
      newPassword
    }).then(() => {
      wx.showModal({
        title: '密码设置成功',
        content: '请使用新密码登录。所有已登录设备均已退出。',
        showCancel: false,
        confirmText: '去登录',
        success: () => {
          wx.redirectTo({ url: '/pages/login/index' });
        }
      });
    }).catch((err) => {
      // 重置码错误 / 过期 / 次数超限等完整文案用弹窗展示，不用 toast 截断
      wx.showModal({
        title: '设置失败',
        content: (err && err.message) || '请稍后重试',
        showCancel: false
      });
    }).finally(() => {
      this.setData({ submitting: false });
    });
  },

  goForgot() {
    wx.navigateTo({ url: '/pages/login/forgot-password' });
  }
});
