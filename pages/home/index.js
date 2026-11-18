const { homeApi, messageApi, resolveImageUrl } = require('../../utils/api.js');
const { navigateByLink, navigateToPage } = require('../../utils/navigate.js');

const BOARD_TYPES = [
  { key: 'feeding', label: '喂养次数榜' },
  { key: 'food', label: '猫粮份数榜' },
  { key: 'active', label: '用户活跃榜' }
];

const PERIODS = [
  { key: 'week', label: '周榜' },
  { key: 'month', label: '月榜' },
  { key: 'all', label: '总榜' }
];

// 榜单统一保留真实数值 + 单位（不再展示折算小时数），最多取 5 条
function decorateRanks(list) {
  return (list || []).slice(0, 5).map((item, i) => ({
    ...item,
    rank: item.rank || (i + 1),
    value: item.value != null ? item.value : 0,
    unit: item.unit || ''
  }));
}

Page({
  data: {
    loading: true, // 首屏骨架
    // 顶部导航：铃铛定位与红点
    statusBarHeight: 20,
    navBarHeight: 44,
    unreadCount: 0,
    // 模块开关由 bootstrap 下发，失败时按默认全开渲染骨架，接口回来后再隐藏
    modules: {
      hotspot: true, notice: true, leaderboard: true, lostCat: true, weather: true,
      catTransfer: true, catWiki: true, catToolbox: true
    },
    // ① 热点
    hotspots: [],
    currentSwiperIndex: 0,
    // ① 通告
    notice: null,
    // ③ 志愿者三榜
    boardTypes: BOARD_TYPES,
    periods: PERIODS,
    boardType: 'feeding',
    boardPeriod: 'month',
    ranks: [],
    boardLoading: false,
    boardError: false,
    myRank: null
  },

  onLoad() {
    this._initNavMetrics();
    this.loadBootstrap();
  },

  onShow() {
    this.loadUnread();
  },

  onPullDownRefresh() {
    this.loadBootstrap().then(() => wx.stopPullDownRefresh());
  },

  onShareAppMessage() {
    return {
      title: '猫遇猫舍 · 遇一只猫，安一个家',
      path: '/pages/home/index',
      imageUrl: '/images/share-cover.png'
    };
  },

  // 与 brand-nav 保持同一套测量口径，铃铛才能落在导航条里且避开右上角胶囊
  _initNavMetrics() {
    let statusBarHeight = 20;
    let navBarHeight = 44;
    try {
      const win = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      statusBarHeight = win.statusBarHeight || 20;
      const menu = wx.getMenuButtonBoundingClientRect ? wx.getMenuButtonBoundingClientRect() : null;
      if (menu && menu.height) {
        navBarHeight = (menu.top - statusBarHeight) * 2 + menu.height;
      }
    } catch (e) {
      // 量不到就用默认值，不影响首屏
    }
    this.setData({ statusBarHeight, navBarHeight });
  },

  async loadUnread() {
    try {
      const res = await messageApi.unreadCount();
      this.setData({ unreadCount: (res && res.count) || 0 });
    } catch (e) {
      // 红点独立降级，不阻塞首页
    }
  },

  /**
   * 首屏：bootstrap（公共内容 + 三榜默认月榜）与 myRank（登录态）并发，
   * 两次响应合并为一次 setData；myRank 未登录/失败独立吞掉，不拖累首屏。
   */
  async loadBootstrap() {
    this.setData({ loading: true, boardError: false });
    try {
      const [bs, mine] = await Promise.all([
        homeApi.bootstrap(),
        homeApi.myRank().catch(() => null)
      ]);
      const data = bs || {};
      const modules = Object.assign(
        {
          hotspot: true, notice: true, leaderboard: true, lostCat: true, weather: true,
          catTransfer: true, catWiki: true, catToolbox: true
        },
        data.modules || {}
      );
      const hotspots = (data.hotspots || []).slice(0, 3).map((h) => ({
        ...h,
        coverUrl: resolveImageUrl(h.coverUrl)
      }));
      const boards = data.boards || {};
      // 合并为一次 setData，减少 JS-Native 通信
      this.setData({
        loading: false,
        modules,
        hotspots,
        notice: data.notice || null,
        ranks: decorateRanks(boards[this.data.boardType]),
        boardError: false,
        myRank: mine || null
      });
    } catch (err) {
      console.error('[home] bootstrap 失败:', err);
      // 整块失败独立降级：热点/通告自动隐藏，榜单显示「点击重试」，不白屏
      this.setData({
        loading: false,
        hotspots: [],
        notice: null,
        ranks: [],
        boardError: true
      });
    }
  },

  /** 切换榜单类型 / 榜期：单独请求三榜 Top5，并同步刷新我的名次 */
  async fetchBoard(type, period) {
    this.setData({ boardType: type, boardPeriod: period, boardLoading: true, boardError: false });
    try {
      const [list, mine] = await Promise.all([
        homeApi.leaderboard({ type, period, limit: 5 }),
        homeApi.myRank({ type, period }).catch(() => null)
      ]);
      this.setData({ ranks: decorateRanks(list), myRank: mine || null, boardLoading: false });
    } catch (err) {
      this.setData({ ranks: [], boardLoading: false, boardError: true });
    }
  },

  onBoardTypeChange(e) {
    const type = e.currentTarget.dataset.type;
    if (type === this.data.boardType) {
      return;
    }
    this.fetchBoard(type, this.data.boardPeriod);
  },

  onPeriodChange(e) {
    const period = e.currentTarget.dataset.period;
    if (period === this.data.boardPeriod) {
      return;
    }
    this.fetchBoard(this.data.boardType, period);
  },

  onBoardRetry() {
    this.fetchBoard(this.data.boardType, this.data.boardPeriod);
  },

  goFullBoard() {
    wx.navigateTo({
      url: `/pages/volunteer/leaderboard?type=${this.data.boardType}&period=${this.data.boardPeriod}`
    });
  },

  // ---- ① 热点 / 通告 ----
  onSwiperChange(e) {
    this.setData({ currentSwiperIndex: e.detail.current });
  },

  onHotspotTap(e) {
    const h = this.data.hotspots[e.currentTarget.dataset.index];
    if (!h || h.linkType === 'none') {
      return;
    }
    // 点击记数失败不阻塞跳转
    if (h.id != null) {
      homeApi.contentClick(h.id).catch(() => {});
    }
    navigateByLink(h.linkUrl);
  },

  goNoticeList() {
    navigateToPage('/pages/home/notice-list');
  },

  // ---- ② 功能区 ----
  onPublishCat() {
    wx.navigateTo({ url: '/pages/cat/report' });
  },

  onRecognize() {
    wx.showModal({
      title: '识别流浪猫',
      content: '花纹识别功能正在内测，开放时间请留意公告',
      showCancel: false,
      confirmText: '我知道了'
    });
  },

  onFindCat() {
    wx.navigateTo({ url: '/pages/lost-cat/list' });
  },

  onCatTransfer() {
    wx.navigateTo({ url: '/pages/transfer/list' });
  },

  onCatWiki() {
    wx.navigateTo({ url: '/pages/wiki/index' });
  },

  onCatToolbox() {
    wx.navigateTo({ url: '/pages/toolbox/index' });
  },

  // ---- 顶部消息铃铛 ----
  goMessage() {
    wx.navigateTo({ url: '/pages/message/index' });
  },

  onMyRankTap() {
    if (this.data.myRank && this.data.myRank.rank) {
      this.goFullBoard();
    } else if (!getApp().globalData.token) {
      wx.navigateTo({ url: '/pages/login/index' });
    } else {
      wx.switchTab({ url: '/pages/feeding/list' });
    }
  }
});
