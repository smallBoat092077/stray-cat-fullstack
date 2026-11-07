const { userApi, coinApi, feedingApi, messageApi, uploadFile, resolveImageUrl } = require('../../utils/api');

const ROLE_MAP = { 0: '普通用户', 1: '志愿者', 2: '领养人', 3: '管理员' };
const TAB_PAGES = ['/pages/index/index', '/pages/cat/list', '/pages/feeding/list', '/pages/profile/index'];

Page({
  data: {
    userInfo: null,
    volunteerData: { feedingCount: 0, serviceHours: 0 },
    coinBalance: 0,
    coinTipped: 0,
    signedToday: false,
    unreadCount: 0,
    editing: false,
    editAvatar: '',
    editNickname: '',
    saving: false
  },

  onShow() {
    const app = getApp();
    const userInfo = app.globalData.userInfo;
    if (userInfo) {
      this.setData({
        userInfo: {
          ...userInfo,
          avatarUrl: resolveImageUrl(userInfo.avatarUrl),
          roleText: ROLE_MAP[userInfo.role] || '普通用户'
        },
        volunteerData: { feedingCount: 0, serviceHours: 0 }
      });
      this.loadCoin();
      this.loadFeedingCount();
      this.loadUnread();
      this.refreshRole();
    } else {
      this.setData({
        userInfo: null,
        coinBalance: 0,
        coinTipped: 0,
        signedToday: false,
        unreadCount: 0,
        volunteerData: { feedingCount: 0, serviceHours: 0 }
      });
    }
  },

  // 从消息中心返回时 onShow 会再跑一次，红点自然跟着清零
  async loadUnread() {
    try {
      const res = await messageApi.unreadCount();
      this.setData({ unreadCount: (res && res.count) || 0 });
    } catch (err) {
      // 未登录或网络异常时保持原值
    }
  },

  // 投喂次数就是自己打的打卡数，用记录接口的 total 直接给
  async loadFeedingCount() {
    try {
      const page = await feedingApi.listMyRecords({ pageNum: 1, pageSize: 1 });
      this.setData({ 'volunteerData.feedingCount': (page && page.total) || 0 });
    } catch (err) {
      // 未登录或网络异常时保持 0
    }
  },

  // 审核工作台的入口取决于角色，角色是服务端改的，所以每次进页面都拉一次最新值
  async refreshRole() {
    try {
      const user = await getApp().refreshUserInfo();
      this.setData({
        userInfo: {
          ...user,
          avatarUrl: resolveImageUrl(user.avatarUrl),
          roleText: ROLE_MAP[user.role] || '普通用户'
        }
      });
    } catch (err) {
      // 拉不到就沿用缓存里的角色，不阻塞页面
    }
  },

  onEditProfile() {
    const info = this.data.userInfo || {};
    this.setData({
      editing: true,
      editAvatar: info.avatarUrl || '',
      editNickname: info.nickname || ''
    });
  },

  onEditCancel() {
    this.setData({ editing: false, saving: false });
  },

  stopTap() {},

  onChooseAvatar(e) {
    const url = e.detail.avatarUrl;
    if (url) {
      this.setData({ editAvatar: url });
    }
  },

  onNicknameInput(e) {
    this.setData({ editNickname: e.detail.value });
  },

  async onEditSave() {
    if (this.data.saving) {
      return;
    }
    const nickname = (this.data.editNickname || '').trim();
    if (!nickname) {
      wx.showToast({ title: '请填写昵称', icon: 'none' });
      return;
    }
    this.setData({ saving: true });
    try {
      let avatarUrl = this.data.editAvatar;
      // chooseAvatar 给的是本机临时路径，必须上传到服务端才能持久化
      if (avatarUrl && avatarUrl.indexOf('wxfile://') === 0) {
        avatarUrl = await uploadFile(avatarUrl);
      }
      const user = await userApi.updateInfo({ nickname, avatarUrl });
      const app = getApp();
      app.globalData.userInfo = user;
      wx.setStorageSync('userInfo', user);
      this.setData({
        userInfo: { ...user, avatarUrl: resolveImageUrl(user.avatarUrl), roleText: ROLE_MAP[user.role] || '普通用户' },
        editing: false,
        saving: false
      });
      wx.showToast({ title: '已保存', icon: 'success' });
    } catch (err) {
      this.setData({ saving: false });
    }
  },

  async loadCoin() {
    try {
      const summary = await coinApi.getSummary();
      this.setData({
        coinBalance: summary.balance,
        coinTipped: summary.tipped,
        signedToday: summary.signedToday
      });
    } catch (err) {
      // 未登录或网络异常时保持原值
    }
  },

  onLogin() {
    wx.showLoading({ title: '登录中...' });
    const app = getApp();
    app.login().then((user) => {
      wx.hideLoading();
      this.setData({
        userInfo: {
          ...user,
          avatarUrl: resolveImageUrl(user.avatarUrl),
          roleText: ROLE_MAP[user.role] || '普通用户'
        }
      });
      wx.showToast({ title: '登录成功', icon: 'success' });
      this.loadCoin();
    }).catch((err) => {
      wx.hideLoading();
      wx.showToast({ title: err.message || '登录失败', icon: 'none' });
    });
  },

  async onSignIn() {
    if (this.data.signedToday) {
      return;
    }
    try {
      const res = await coinApi.signIn();
      this.setData({ coinBalance: res.balance, signedToday: true });
      wx.showToast({ title: res.granted ? `签到成功 +${res.reward}` : '今日已签到', icon: 'none' });
    } catch (err) {
      // 错误提示由 request 统一处理
    }
  },

  goToPage(e) {
    const url = e.currentTarget.dataset.url;
    // tabBar 页只能用 switchTab 打开，navigateTo 会直接报错
    if (TAB_PAGES.indexOf(url) >= 0) {
      wx.switchTab({ url });
      return;
    }
    wx.navigateTo({ url });
  },

  goToLogin() {
    wx.navigateTo({ url: '/pages/login/index' });
  },

  onVolunteerApply() {
    if (!this.data.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '志愿者申请',
      content: '完成 3 次投喂打卡后，联系管理员提交志愿者申请即可。',
      confirmText: '我知道了',
      showCancel: false
    });
  },

  onAbout() {
    wx.showModal({
      title: '关于我们',
      content: '流浪猫喂养小程序 v1.0.9\n关爱流浪动物，从每一次投喂开始',
      showCancel: false
    });
  },

  onClearCache() {
    wx.showModal({
      title: '清除缓存',
      content: '确定要清除本地缓存吗？不会影响服务器上的数据。',
      success: (res) => {
        if (res.confirm) {
          const token = wx.getStorageSync('token');
          const userInfo = wx.getStorageSync('userInfo');
          wx.clearStorageSync();
          if (token) {
            wx.setStorageSync('token', token);
            wx.setStorageSync('userInfo', userInfo);
          }
          wx.showToast({ title: '缓存已清除', icon: 'success' });
        }
      }
    });
  },

  onDeactivate() {
    wx.showModal({
      title: '注销账号',
      content: '注销后将清除你的登录信息、昵称、头像和联系方式，且无法恢复。确定继续吗？',
      confirmText: '确认注销',
      confirmColor: '#A8453B',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        userApi.deactivate().then(() => {
          getApp().clearSession();
          this.setData({ userInfo: null });
          wx.showToast({ title: '账号已注销', icon: 'success' });
        }).catch(() => {});
      }
    });
  },

  onLogout() {
    wx.showModal({
      title: '确认退出',
      content: '退出后需要重新登录',
      success: (res) => {
        if (res.confirm) {
          getApp().clearSession();
          this.setData({ userInfo: null });
          wx.showToast({ title: '已退出登录', icon: 'success' });
        }
      }
    });
  }
});
