const { catApi } = require('../../utils/api');

const LIFE_STATUS_MAP = {
  0: { text: '流浪中', class: 'warning' },
  1: { text: '投喂中', class: 'info' },
  2: { text: '待领养', class: 'success' },
  3: { text: '已领养', class: 'success' },
  4: { text: '回访中', class: 'info' }
};

const GENDER_MAP = { 0: '母', 1: '公', 2: '未知' };

Page({
  data: {
    catList: [],
    currentStatus: '',
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loading: false
  },

  onLoad() {
    this.loadCatList();
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, hasMore: true, catList: [] });
    this.loadCatList().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadCatList();
    }
  },

  async loadCatList() {
    this.setData({ loading: true });
    try {
      const params = {
        pageNum: this.data.pageNum,
        pageSize: this.data.pageSize
      };
      if (this.data.currentStatus !== '') {
        params.lifeStatus = this.data.currentStatus;
      }
      const result = await catApi.getList(params);
      const cats = (result.records || []).map(cat => ({
        ...cat,
        lifeStatusText: LIFE_STATUS_MAP[cat.lifeStatus]?.text || '未知',
        lifeStatusClass: LIFE_STATUS_MAP[cat.lifeStatus]?.class || 'info',
        genderText: GENDER_MAP[cat.gender] || '未知'
      }));
      this.setData({
        catList: this.data.pageNum === 1 ? cats : [...this.data.catList, ...cats],
        hasMore: result.current < result.pages,
        pageNum: this.data.pageNum + 1
      });
    } catch (err) {
      console.error('加载猫咪列表失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  onStatusFilter(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({
      currentStatus: status,
      pageNum: 1,
      hasMore: true,
      catList: []
    });
    this.loadCatList();
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/cat/detail?id=${id}` });
  }
});
