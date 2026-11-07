const { rechargeApi } = require('../../utils/api');

const STATUS_MAP = {
  0: '待支付',
  1: '已支付待到账',
  2: '已到账',
  3: '已退款',
  4: '已关闭'
};

const prettyTime = (iso) => (iso ? String(iso).slice(0, 16).replace('T', ' ') : '');

Page({
  data: {
    orders: [],
    loading: false
  },

  onShow() {
    this.loadOrders();
  },

  onPullDownRefresh() {
    this.loadOrders().then(() => wx.stopPullDownRefresh());
  },

  async loadOrders() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    try {
      const rows = await rechargeApi.getOrders(1, 50);
      const orders = (rows || []).map((item) => ({
        ...item,
        priceYuan: ((item.priceFen || 0) / 100).toFixed(2),
        statusText: STATUS_MAP[item.status] || '未知',
        timeText: prettyTime(item.paidAt || item.createdAt)
      }));
      this.setData({ orders, loading: false });
    } catch (err) {
      this.setData({ loading: false });
    }
  }
});
