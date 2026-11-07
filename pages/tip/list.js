const { coinApi, tipApi } = require('../../utils/api');
const { COIN_BIZ_MAP } = require('../../utils/constants');

function prettyTime(iso) {
  if (!iso) return '';
  return iso.slice(0, 16).replace('T', ' ');
}

Page({
  data: {
    tab: 'tip',
    balance: 0,
    tipped: 0,
    tips: [],
    flows: [],
    tipPage: 1,
    flowPage: 1,
    tipHasMore: true,
    flowHasMore: true,
    loading: false
  },

  onLoad(options) {
    if (options.tab === 'flow') {
      this.setData({ tab: 'flow' });
    }
  },

  onShow() {
    this.refreshCurrent();
  },

  onPullDownRefresh() {
    this.refreshCurrent().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    this.loadMore();
  },

  onSwitchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.tab) {
      return;
    }
    this.setData({ tab });
    this.refreshCurrent();
  },

  refreshCurrent() {
    return this.data.tab === 'tip' ? this.loadTips(true) : this.loadFlows(true);
  },

  loadMore() {
    if (this.data.tab === 'tip') {
      if (this.data.tipHasMore) {
        this.loadTips(false);
      }
    } else if (this.data.flowHasMore) {
      this.loadFlows(false);
    }
  },

  async loadTips(reset) {
    if (this.data.loading) {
      return;
    }
    const page = reset ? 1 : this.data.tipPage + 1;
    this.setData({ loading: true });
    try {
      const res = await tipApi.listMine({ pageNum: page, pageSize: 20 });
      const rows = res.records.map((item) => ({
        ...item,
        timeText: prettyTime(item.createdAt)
      }));
      this.setData({
        tips: reset ? rows : this.data.tips.concat(rows),
        tipPage: page,
        tipHasMore: page < res.pages,
        loading: false
      });
      this.loadSummary();
    } catch (err) {
      this.setData({ loading: false });
    }
  },

  async loadFlows(reset) {
    if (this.data.loading) {
      return;
    }
    const page = reset ? 1 : this.data.flowPage + 1;
    this.setData({ loading: true });
    try {
      const res = await coinApi.getTransactions({ pageNum: page, pageSize: 20 });
      const rows = res.records.map((item) => ({
        ...item,
        bizText: COIN_BIZ_MAP[item.bizType] || item.bizType,
        timeText: prettyTime(item.createdAt),
        gain: item.amount > 0
      }));
      this.setData({
        flows: reset ? rows : this.data.flows.concat(rows),
        flowPage: page,
        flowHasMore: page < res.pages,
        loading: false
      });
      this.loadSummary();
    } catch (err) {
      this.setData({ loading: false });
    }
  },

  async loadSummary() {
    try {
      const summary = await coinApi.getSummary();
      this.setData({ balance: summary.balance, tipped: summary.tipped });
    } catch (err) {
      // 未登录时已由 request 统一处理
    }
  },

  onGoCreate() {
    wx.switchTab({ url: '/pages/cat/list' });
  }
});
