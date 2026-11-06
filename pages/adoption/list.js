const { adoptionApi } = require('../../utils/api');
const { ADOPTION_STATUS_MAP } = require('../../utils/constants');

Page({
  data: {
    adoptionList: [],
    currentStatus: '',
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loading: false
  },

  onLoad() {
    this.loadAdoptions();
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, hasMore: true, adoptionList: [] });
    this.loadAdoptions().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadAdoptions();
    }
  },

  async loadAdoptions() {
    this.setData({ loading: true });
    try {
      const app = getApp();
      const applicantId = app.globalData.userInfo?.id;

      const params = {
        pageNum: this.data.pageNum,
        pageSize: this.data.pageSize
      };

      if (applicantId) {
        params.applicantId = applicantId;
      }
      if (this.data.currentStatus !== '') {
        params.status = this.data.currentStatus;
      }

      const result = await adoptionApi.getApplicationList(params);
      const list = (result.records || []).map(item => ({
        ...item,
        statusText: ADOPTION_STATUS_MAP[item.status]?.text || '未知',
        statusClass: ADOPTION_STATUS_MAP[item.status]?.class || 'info'
      }));

      this.setData({
        adoptionList: this.data.pageNum === 1 ? list : [...this.data.adoptionList, ...list],
        hasMore: result.current < result.pages,
        pageNum: this.data.pageNum + 1
      });
    } catch (err) {
      console.error('加载领养列表失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  onFilter(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ currentStatus: status, pageNum: 1, hasMore: true, adoptionList: [] });
    this.loadAdoptions();
  },

  onReapply(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/adoption/apply?id=${id}` });
  },

  goToCatList() {
    wx.switchTab({ url: '/pages/cat/list' });
  }
});
