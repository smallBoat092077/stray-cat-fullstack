const ROLE_MAP = { 0: '普通用户', 1: '志愿者', 2: '领养人', 3: '管理员' };

Page({
  data: {
    userInfo: null,
    volunteerData: { feedingCount: 0, serviceHours: 0 }
  },

  onShow() {
    const app = getApp();
    const userInfo = app.globalData.userInfo;
    if (userInfo) {
      this.setData({
        userInfo: {
          ...userInfo,
          roleText: ROLE_MAP[userInfo.role] || '普通用户'
        }
      });
    }
  },

  goToPage(e) {
    const url = e.currentTarget.dataset.url;
    wx.navigateTo({ url });
  },

  onVolunteerApply() {
    wx.showToast({ title: '志愿者申请功能开发中', icon: 'none' });
  },

  onSettings() {
    wx.showToast({ title: '设置功能开发中', icon: 'none' });
  },

  onLogout() {
    wx.showModal({
      title: '确认退出',
      content: '退出后需要重新登录',
      success: (res) => {
        if (res.confirm) {
          const app = getApp();
          app.globalData.token = null;
          app.globalData.userInfo = null;
          wx.removeStorageSync('token');
          wx.removeStorageSync('userInfo');
          this.setData({ userInfo: null });
          wx.showToast({ title: '已退出登录', icon: 'success' });
        }
      }
    });
  }
});
