const { userApi, coinApi, feedingApi, messageApi, uploadFile, resolveImageUrl } = require('../../utils/api');
const { TAB_PAGES } = require('../../utils/constants.js');

const ROLE_MAP = { 0: '普通用户', 1: '志愿者', 2: '领养人', 3: '管理员' };

const EMPTY_VOLUNTEER = {
  feedingCount: 0,
  serviceHours: 0,
  level: 1,
  levelName: '见习志愿者',
  nextLevelName: '初级志愿者',
  remainToNext: 3,
  progressPercent: 0,
  nextReward: 10,
  completedCount: 0
};

Page({
  data: {
    userInfo: null,
    volunteerData: { ...EMPTY_VOLUNTEER },
    coinBalance: 0,
    coinTipped: 0,
    signedToday: false,
    signInStreak: { current: 0, max: 0 },
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
        volunteerData: { ...EMPTY_VOLUNTEER }
      });
      this.loadCoin();
      this.loadVolunteerStats();
      this.loadUnread();
      this.refreshRole();
    } else {
      this.setData({
        userInfo: null,
        coinBalance: 0,
        coinTipped: 0,
        signedToday: false,
        signInStreak: { current: 0, max: 0 },
        unreadCount: 0,
        volunteerData: { ...EMPTY_VOLUNTEER }
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

  // 投喂次数 / 服务时长 / 志愿者等级都来自同一个统计接口，等级由已审核打卡数推导
  async loadVolunteerStats() {
    try {
      const stats = await feedingApi.volunteerStats();
      if (!stats) {
        return;
      }
      this.setData({
        volunteerData: {
          feedingCount: stats.completedCount || 0,
          completedCount: stats.completedCount || 0,
          serviceHours: stats.serviceHours || 0,
          level: stats.level || 1,
          levelName: stats.levelName || '见习志愿者',
          nextLevelName: stats.nextLevelName || '',
          remainToNext: stats.remainToNext || 0,
          progressPercent: stats.progressPercent || 0,
          nextReward: stats.nextReward || 0
        }
      });
    } catch (err) {
      // 未登录或网络异常时保持默认值
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
      // chooseAvatar 给的是本机临时路径，必须上传到服务端才能持久化。
      // 注意路径因平台而异：Android 是 wxfile://，iOS 是 http://tmp/，
      // 所以这里不能只认 wxfile:// 前缀——只要不是已经上传过的网络地址就上传
      if (avatarUrl && !/^https?:\/\//i.test(avatarUrl)) {
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
      // 上传或保存失败必须给反馈，否则用户对着按钮毫无反应（曾因静默失败被当成 bug 报障）
      const msg = (err && (err.message || err.errMsg)) || '';
      wx.showToast({ title: msg ? '保存失败：' + msg : '保存失败，请重试', icon: 'none', duration: 3000 });
    }
  },

  async loadCoin() {
    try {
      const summary = await coinApi.getSummary();
      this.setData({
        coinBalance: summary.balance,
        coinTipped: summary.tipped,
        signedToday: summary.signedToday,
        signInStreak: summary.signInStreak || { current: 0, max: 0 }
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
      const streak = res.signInStreak || this.data.signInStreak;
      this.setData({
        coinBalance: res.balance,
        signedToday: true,
        signInStreak: streak
      });
      // 连续 7 天起给一点荣誉感（只改样式，不额外发猫粮，避免诱导性质被审）
      const tip = res.granted
        ? `签到成功，已连续 ${streak.current} 天`
        : '今日已签到';
      wx.showToast({ title: tip, icon: 'none' });
    } catch (err) {
      // 错误提示由 request 统一处理
    }
  },

  goToPage(e) {
    const raw = e.currentTarget.dataset.url;
    if (!raw) {
      return;
    }
    // 统一成带前导斜杠，兼容带 / 与不带 / 两种口径
    const url = '/' + String(raw).trim().replace(/^\/+/, '');
    const path = url.split('?')[0];
    // tabBar 页只能用 switchTab 打开，navigateTo 会直接报错
    if (TAB_PAGES.indexOf(path) >= 0) {
      wx.switchTab({ url: path });
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

  // 账号密码用户绑定微信，绑定后才能充值猫粮（虚拟支付需要 openid + session_key）
  onBindWechat() {
    wx.showModal({
      title: '绑定微信',
      content: '绑定后可以充值猫粮，下次微信登录也会直接进入本账号。确定绑定吗？',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        wx.showLoading({ title: '绑定中...' });
        getApp().bindWechat().then((user) => {
          wx.hideLoading();
          this.setData({
            userInfo: {
              ...user,
              avatarUrl: resolveImageUrl(user.avatarUrl),
              roleText: ROLE_MAP[user.role] || '普通用户'
            }
          });
          wx.showToast({ title: '绑定成功', icon: 'success' });
        }).catch(() => {
          // 失败原因由 request 统一弹 toast，这里不用重复提示
          wx.hideLoading();
        });
      }
    });
  },

  onAbout() {
    wx.showModal({
      title: '关于我们',
      content: '猫遇猫舍 v1.0.9\n遇一只猫，安一个家',
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
