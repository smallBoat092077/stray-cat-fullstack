const { accountApi } = require('../../utils/api');

Page({
  data: {
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
    showCurrent: false,
    showNew: false,
    showConfirm: false,
    submitting: false
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    if (!field) {
      return;
    }
    this.setData({ [field]: e.detail.value });
  },

  toggleShow(e) {
    const field = e.currentTarget.dataset.field;
    if (!field) {
      return;
    }
    this.setData({ [field]: !this.data[field] });
  },

  async submit() {
    if (this.data.submitting) {
      return;
    }
    const { currentPassword, newPassword, confirmPassword } = this.data;
    if (!currentPassword || !newPassword || !confirmPassword) {
      wx.showToast({ title: '请填写完整密码信息', icon: 'none' });
      return;
    }
    if (newPassword.length < 6 || newPassword.length > 20) {
      wx.showToast({ title: '新密码需为 6–20 位', icon: 'none' });
      return;
    }
    if (newPassword !== confirmPassword) {
      wx.showToast({ title: '两次输入的新密码不一致', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    try {
      await accountApi.changePassword({ currentPassword, newPassword, confirmPassword });
      wx.showModal({
        title: '密码修改成功',
        content: '密码修改成功，已在所有设备退出登录，请使用新密码重新登录。',
        showCancel: false,
        confirmText: '返回登录',
        success: () => {
          getApp().clearSession();
          wx.redirectTo({ url: '/pages/login/index' });
        }
      });
    } catch (err) {
      // 锁定 / 剩余次数等完整提示由后端写在 message 里，用 modal 展示，避免 toast 截断
      wx.showModal({
        title: '修改失败',
        content: (err && err.message) || '修改失败，请稍后重试',
        showCancel: false,
        confirmText: '知道了'
      });
    } finally {
      this.setData({ submitting: false });
    }
  }
});
