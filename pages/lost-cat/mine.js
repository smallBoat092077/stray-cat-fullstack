const { lostCatApi, resolveImageUrl } = require('../../utils/api.js');

const STATUS_MAP = { 0: '寻找中', 1: '已找到', 2: '已关闭' };
const AUDIT_MAP = {
  0: { text: '待审核', cls: 'warning' },
  1: { text: '已通过', cls: 'success' },
  2: { text: '已驳回', cls: 'danger' }
};

function formatTime(v) {
  if (!v) return '';
  return String(v).replace('T', ' ').slice(0, 16);
}

function normImages(images) {
  const arr = Array.isArray(images)
    ? images
    : String(images || '').split(',').filter(Boolean);
  return arr.map((p) => resolveImageUrl(p)).filter(Boolean);
}

function decorate(p) {
  const imagesList = normImages(p.images);
  const audit = AUDIT_MAP[p.auditStatus] || AUDIT_MAP[0];
  return {
    ...p,
    imagesList,
    cover: imagesList[0] || '',
    statusText: STATUS_MAP[p.status] || '寻找中',
    auditText: audit.text,
    auditClass: audit.cls,
    lostAtText: formatTime(p.lostAt),
    canFound: p.status === 0
  };
}

function decorateClue(c) {
  const imagesList = normImages(c.images);
  const audit = AUDIT_MAP[c.auditStatus] || AUDIT_MAP[0];
  return {
    ...c,
    imagesList,
    cover: imagesList[0] || '',
    auditText: audit.text,
    auditClass: audit.cls,
    foundAtText: formatTime(c.foundAt)
  };
}

Page({
  data: {
    activeTab: 'post', // 'post' 我的发布 | 'clue' 我的线索
    list: [],
    clueList: [],
    loading: false,
    clueLoading: false
  },

  onLoad(options) {
    // 线索提交成功后 redirectTo 带 tab=clue，直接落到「我的线索」
    if (options && options.tab === 'clue') {
      this.setData({ activeTab: 'clue' });
    }
  },

  onShow() {
    this.loadMine();
    this.loadClueMine();
  },

  onPullDownRefresh() {
    Promise.all([this.loadMine(), this.loadClueMine()])
      .then(() => wx.stopPullDownRefresh());
  },

  onSwitchTab(e) {
    this.setData({ activeTab: e.currentTarget.dataset.tab });
  },

  async loadMine() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    try {
      const res = await lostCatApi.mine();
      const rows = Array.isArray(res) ? res : ((res && res.records) || []);
      this.setData({ list: rows.map(decorate), loading: false });
    } catch (err) {
      this.setData({ loading: false });
      wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    }
  },

  async loadClueMine() {
    if (this.data.clueLoading) {
      return;
    }
    this.setData({ clueLoading: true });
    try {
      const res = await lostCatApi.clueMine();
      const rows = Array.isArray(res) ? res : ((res && res.records) || []);
      this.setData({ clueList: rows.map(decorateClue), clueLoading: false });
    } catch (err) {
      this.setData({ clueLoading: false });
      wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    }
  },

  // 仅本人在「我的发布」里可标记已找到
  onFound(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '标记已找到',
      content: '确认猫咪已经找到了吗？标记后启事会更新为「已找到」。',
      success: async (res) => {
        if (!res.confirm) {
          return;
        }
        try {
          await lostCatApi.found(id);
          wx.showToast({ title: '已更新', icon: 'success' });
          this.loadMine();
        } catch (err) {
          const msg = (err && err.message) || '操作失败';
          wx.showToast({ title: msg, icon: 'none' });
        }
      }
    });
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/lost-cat/detail?id=${id}` });
  },

  goClueDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/lost-cat/clue-detail?id=${id}` });
  },

  goCreate() {
    wx.navigateTo({ url: '/pages/lost-cat/create' });
  }
});
