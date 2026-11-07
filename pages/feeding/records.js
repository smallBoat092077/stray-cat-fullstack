const { feedingApi, resolveImageUrl } = require('../../utils/api');
const { RECORD_STATUS_MAP } = require('../../utils/constants');
const { prettyTime } = require('../../utils/format');

Page({
  data: {
    records: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loading: false
  },

  onLoad() {
    if (!getApp().ensureLogin()) {
      return;
    }
    this.loadRecords();
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, hasMore: true });
    this.loadRecords().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadRecords();
    }
  },

  async loadRecords() {
    this.setData({ loading: true });
    try {
      const result = await feedingApi.listMyRecords({ pageNum: this.data.pageNum, pageSize: this.data.pageSize });
      const rows = (result.records || []).map((item) => {
        const statusInfo = RECORD_STATUS_MAP[item.status];
        return {
          ...item,
          timeText: prettyTime(item.checkInTime),
          statusText: statusInfo ? statusInfo.text : '未知',
          statusClass: statusInfo ? statusInfo.class : 'info',
          images: (item.images || []).map((path) => resolveImageUrl(path))
        };
      });
      this.setData({
        records: this.data.pageNum === 1 ? rows : [...this.data.records, ...rows],
        total: result.total || 0,
        hasMore: result.current < result.pages,
        pageNum: this.data.pageNum + 1
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  goToTask(e) {
    const taskId = e.currentTarget.dataset.id;
    if (taskId) {
      wx.navigateTo({ url: `/pages/feeding/detail?id=${taskId}` });
    }
  },

  previewImage(e) {
    const { index, img } = e.currentTarget.dataset;
    const urls = this.data.records[index] ? this.data.records[index].images : [];
    if (urls && urls.length) {
      wx.previewImage({ current: img, urls });
    }
  },

  goFeeding() {
    wx.switchTab({ url: '/pages/feeding/list' });
  }
});
