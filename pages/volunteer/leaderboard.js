const { homeApi, feedingApi } = require('../../utils/api.js');
const attribution = require('../../utils/attribution');

const VALID_TYPES = ['feeding', 'food', 'active'];
const VALID_PERIODS = ['week', 'month', 'all'];

function decorate(list) {
  return (list || []).map((item, i) => ({
    ...item,
    rank: item.rank || (i + 1),
    value: item.value != null ? item.value : 0,
    unit: item.unit || '次投喂'
  }));
}

// 旧志愿者榜字段不同，降级时统一成同一套结构
function decorateLegacy(list) {
  return (list || []).map((item, i) => ({
    rank: item.rank || (i + 1),
    nickname: item.nickname,
    levelName: item.levelName || '',
    value: item.completedCount || 0,
    unit: '次投喂'
  }));
}

// 榜单是荣誉型传播：分享出去也不涉及利益，符合「不做返利做故事」的主线
Page({
  data: {
    ranks: [],
    loading: false,
    // 已登录用户自己的名次（跟随当前榜类型 / 榜期）
    mine: null,
    // onShareAppMessage 必须同步返回，短码 onLoad 预生成
    shareSid: '',
    boardTypes: [
      { key: 'feeding', label: '喂养次数榜' },
      { key: 'food', label: '猫粮份数榜' },
      { key: 'active', label: '用户活跃榜' }
    ],
    periods: [
      { key: 'week', label: '周榜' },
      { key: 'month', label: '月榜' },
      { key: 'all', label: '总榜' }
    ],
    type: 'feeding',
    period: 'month',
    legacyTip: ''
  },

  // 首页「完整榜单」入口会把当前榜类型 / 榜期带过来
  onLoad(options) {
    const type = (options && options.type) || 'feeding';
    const period = (options && options.period) || 'month';
    this.setData({
      type: VALID_TYPES.indexOf(type) >= 0 ? type : 'feeding',
      period: VALID_PERIODS.indexOf(period) >= 0 ? period : 'month'
    });
    this.prepareShareSid();
  },

  onShow() {
    this.loadBoard();
  },

  onPullDownRefresh() {
    this.loadBoard().then(() => wx.stopPullDownRefresh());
  },

  onTypeChange(e) {
    const type = e.currentTarget.dataset.type;
    if (type === this.data.type) {
      return;
    }
    this.setData({ type, legacyTip: '' });
    this.loadBoard();
  },

  onPeriodChange(e) {
    const period = e.currentTarget.dataset.period;
    if (period === this.data.period) {
      return;
    }
    this.setData({ period, legacyTip: '' });
    this.loadBoard();
  },

  async loadBoard() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    const { type, period } = this.data;
    try {
      const list = await homeApi.leaderboard({ type, period, limit: 50 });
      this.setData({ ranks: decorate(list), loading: false, legacyTip: '' });
    } catch (err) {
      console.error('[leaderboard] 新榜加载失败，回退旧志愿者榜:', err);
      // 兼容路径：旧 /feeding/volunteer/leaderboard 保持原样可用，保证页面不空白
      try {
        const legacy = (await feedingApi.volunteerLeaderboard(50)) || [];
        this.setData({
          ranks: decorateLegacy(legacy),
          loading: false,
          legacyTip: '新榜暂不可用，以下为喂养总榜'
        });
      } catch (e) {
        this.setData({ ranks: [], loading: false });
      }
    }
    this.loadMine();
  },

  // 我的名次跟随当前榜类型 / 榜期；未登录或数值为 0 时不展示
  async loadMine() {
    const app = getApp();
    if (!app.globalData.token) {
      this.setData({ mine: null });
      return;
    }
    try {
      const my = await homeApi.myRank({ type: this.data.type, period: this.data.period });
      this.setData({ mine: my && my.rank ? my : null });
    } catch (err) {
      // 拉不到就只看榜单，不展示我的卡片
      this.setData({ mine: null });
    }
  },

  async prepareShareSid() {
    try {
      const link = await attribution.createLink({
        channel: 'share_card',
        campaign: 'leaderboard',
        bizType: 'feeding',
        landingPage: 'pages/volunteer/leaderboard'
      });
      if (link && link.sid) {
        this.setData({ shareSid: link.sid });
      }
    } catch (e) {
      // 失败退回不带 sid 的旧分享
    }
  },

  onShareAppMessage() {
    const query = `type=${this.data.type}&period=${this.data.period}`;
    const path = this.data.shareSid
      ? `/pages/volunteer/leaderboard?sid=${this.data.shareSid}&${query}`
      : `/pages/volunteer/leaderboard?${query}`;
    return {
      title: '城市喂猫人荣誉榜，看看这周谁在守护小猫',
      path,
      imageUrl: '/images/share-cover.png'
    };
  },

  goFeeding() {
    wx.switchTab({ url: '/pages/feeding/list' });
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/index' });
  }
});
