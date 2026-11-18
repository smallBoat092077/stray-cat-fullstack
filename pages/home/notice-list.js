const { homeApi } = require('../../utils/api.js');

function formatTime(v) {
  if (!v) return '';
  return String(v).replace('T', ' ').slice(0, 16);
}

Page({
  data: {
    list: [],
    loading: false,
    pageNum: 1,
    pageSize: 20,
    hasMore: true
  },

  onLoad() {
    this.loadList(true);
  },

  onPullDownRefresh() {
    this.loadList(true).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadList(false);
    }
  },

  async loadList(reset) {
    if (this.data.loading) {
      return;
    }
    const pageNum = reset ? 1 : this.data.pageNum;
    this.setData({ loading: true });
    try {
      const res = await homeApi.noticeList({ pageNum, pageSize: this.data.pageSize });
      const rows = Array.isArray(res) ? res : ((res && res.records) || []);
      const items = rows.map((n) => ({
        ...n,
        timeText: formatTime(n.createdAt || n.publishTime),
        expanded: false,
        detailLoaded: false,
        content: ''
      }));
      let hasMore;
      if (res && typeof res.current === 'number' && typeof res.pages === 'number') {
        hasMore = res.current < res.pages;
      } else {
        hasMore = rows.length >= this.data.pageSize;
      }
      this.setData({
        list: reset ? items : [...this.data.list, ...items],
        pageNum: pageNum + 1,
        hasMore,
        loading: false
      });
    } catch (err) {
      this.setData({ loading: false });
      wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    }
  },

  _updateItem(id, patch) {
    const list = this.data.list.map((item) =>
      String(item.id) === String(id) ? { ...item, ...patch } : item
    );
    this.setData({ list });
  },

  // 点击展开全文并调详情接口（pv 自增）；已加载过的不重复请求
  async onToggle(e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.list.find((n) => String(n.id) === String(id));
    if (!item) {
      return;
    }
    if (item.expanded) {
      this._updateItem(id, { expanded: false });
      return;
    }
    if (item.detailLoaded) {
      this._updateItem(id, { expanded: true });
      return;
    }
    try {
      const d = await homeApi.noticeDetail(id);
      const content = (d && (d.content || d.summary || d.title)) || item.summary || '';
      this._updateItem(id, { expanded: true, detailLoaded: true, content });
    } catch (err) {
      wx.showToast({ title: '加载失败，请重试', icon: 'none' });
    }
  }
});
