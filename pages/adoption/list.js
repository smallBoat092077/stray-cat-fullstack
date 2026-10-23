const ADOPTION_STATUS_MAP = {
  0: { text: '待审核', class: 'warning' },
  1: { text: '审核中', class: 'info' },
  2: { text: '已通过', class: 'success' },
  3: { text: '已拒绝', class: 'danger' },
  4: { text: '已签约', class: 'success' },
  5: { text: '已完成', class: 'success' }
};

Page({
  data: {
    adoptionList: [],
    currentStatus: '',
    loading: false
  },

  onLoad() {
    this.loadAdoptions();
  },

  async loadAdoptions() {
    this.setData({ loading: true });
    // TODO: 对接后端领养列表接口
    // 目前使用模拟数据
    const mockList = [
      {
        id: 1, catName: '大黄', status: 2,
        statusText: '已通过', statusClass: 'success',
        createdAt: '2026-10-20', reviewComment: '条件符合，欢迎领养'
      }
    ];
    this.setData({ adoptionList: mockList, loading: false });
  },

  onFilter(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ currentStatus: status });
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
