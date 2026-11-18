const { transferApi, resolveImageUrl } = require('../../utils/api.js');
const { TRANSFER_STATUS_MAP, TRANSFER_APPLY_STATUS_MAP } = require('../../utils/constants.js');

const SEGMENTS = [
  { key: 1, label: '在架' },
  { key: 0, label: '待审' },
  { key: 2, label: '已转让' },
  { key: 3, label: '已下架' }
];

function decoratePost(post) {
  const photos = Array.isArray(post.photos)
    ? post.photos
    : String(post.photos || '').split(',').filter(Boolean);
  const st = TRANSFER_STATUS_MAP[post.status] || TRANSFER_STATUS_MAP[0];
  return {
    ...post,
    cover: resolveImageUrl(photos[0] || ''),
    statusText: st.text,
    statusClass: st.cls
  };
}

function decorateApply(apply) {
  const st = TRANSFER_APPLY_STATUS_MAP[apply.status] || TRANSFER_APPLY_STATUS_MAP[0];
  return {
    ...apply,
    statusText: st.text,
    statusClass: st.cls,
    initial: (apply.contactName || apply.applicantName || '猫').charAt(0)
  };
}

Page({
  data: {
    segments: SEGMENTS,
    activeStatus: 1,
    counts: { 1: 0, 0: 0, 2: 0, 3: 0 },
    list: [],
    filtered: [],
    loading: true,
    error: false,
    expandedId: null,
    appliesMap: {}
  },

  onShow() {
    const app = getApp();
    if (!app.globalData.token || !app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      setTimeout(() => wx.navigateTo({ url: '/pages/login/index' }), 600);
      return;
    }
    this.loadMine();
  },

  onPullDownRefresh() {
    this.loadMine().then(() => wx.stopPullDownRefresh());
  },

  async loadMine() {
    this.setData({ loading: true, error: false });
    try {
      const res = await transferApi.mine();
      const rows = Array.isArray(res) ? res : [];
      const list = rows.map(decoratePost);
      const counts = { 1: 0, 0: 0, 2: 0, 3: 0 };
      list.forEach((p) => {
        if (p.status === 0) counts[0]++;
        else if (p.status === 1) counts[1]++;
        else if (p.status === 2) counts[2]++;
        else if (p.status === 3 || p.status === 4) counts[3]++;
      });
      this.setData({ list, counts, loading: false, error: false });
      this.applyFilter();
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  applyFilter() {
    const status = this.data.activeStatus;
    const filtered = this.data.list.filter((p) => {
      if (status === 3) return p.status === 3 || p.status === 4;
      return p.status === status;
    });
    this.setData({ filtered });
  },

  onSegment(e) {
    const key = Number(e.currentTarget.dataset.key);
    if (key === this.data.activeStatus) return;
    this.setData({ activeStatus: key, expandedId: null });
    this.applyFilter();
  },

  onRetry() {
    this.loadMine();
  },

  onToggleExpand(e) {
    const id = e.currentTarget.dataset.id;
    if (this.data.expandedId === id) {
      this.setData({ expandedId: null });
      return;
    }
    const map = Object.assign({}, this.data.appliesMap);
    map[id] = { loading: true, error: false, list: map[id] ? map[id].list : [] };
    this.setData({ expandedId: id, appliesMap: map });
    this.loadApplies(id);
  },

  onRetryApplies(e) {
    this.loadApplies(e.currentTarget.dataset.id);
  },

  async loadApplies(postId) {
    const map = Object.assign({}, this.data.appliesMap);
    map[postId] = { loading: true, error: false, list: map[postId] ? map[postId].list : [] };
    this.setData({ appliesMap: map });
    try {
      const res = await transferApi.applies(postId);
      const rows = Array.isArray(res) ? res : [];
      map[postId] = { loading: false, error: false, list: rows.map(decorateApply) };
      this.setData({ appliesMap: map });
    } catch (err) {
      map[postId] = { loading: false, error: true, list: [] };
      this.setData({ appliesMap: map });
    }
  },

  async onApplyStatus(e) {
    const applyId = e.currentTarget.dataset.id;
    const postId = e.currentTarget.dataset.post;
    const status = Number(e.currentTarget.dataset.status);
    try {
      await transferApi.applyStatus(applyId, status);
      wx.showToast({ title: '已更新', icon: 'success' });
      this.loadApplies(postId);
    } catch (err) {
      wx.showToast({ title: '操作失败，请重试', icon: 'none' });
    }
  },

  async onOffline(e) {
    const postId = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认下架',
      content: '下架后将不再展示在列表中，确定继续吗？',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await transferApi.offline(postId);
          wx.showToast({ title: '已下架', icon: 'success' });
          this.loadMine();
        } catch (err) {
          wx.showToast({ title: '操作失败，请重试', icon: 'none' });
        }
      }
    });
  },

  async onComplete(e) {
    const postId = e.currentTarget.dataset.id;
    wx.showModal({
      title: '标记已转让',
      content: '标记后该条会自动下架，并通知所有申请人。确定继续吗？',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await transferApi.complete(postId);
          wx.showToast({ title: '已标记转让', icon: 'success' });
          this.loadMine();
        } catch (err) {
          wx.showToast({ title: '操作失败，请重试', icon: 'none' });
        }
      }
    });
  },

  goPublish() {
    wx.navigateTo({ url: '/pages/transfer/publish' });
  }
});
