const { catApi, resolveImageUrl } = require('../../utils/api');
const { LIFE_STATUS_MAP, GENDER_MAP } = require('../../utils/constants');

Page({
  data: {
    catList: [],
    displayList: [],
    focusedCat: null,
    currentStatus: '',
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loading: false
  },

  onLoad() {
    this.loadCatList();
  },

  onShow() {
    const app = getApp();
    const focusId = app.globalData.focusCatId;
    if (focusId) {
      app.globalData.focusCatId = null;
      this.focusCat(focusId);
    }
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, hasMore: true, catList: [], focusedCat: null });
    this.loadCatList().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadCatList();
    }
  },

  decorate(cat) {
    return {
      ...cat,
      coverImage: resolveImageUrl(cat.coverImage),
      lifeStatusText: LIFE_STATUS_MAP[cat.lifeStatus] ? LIFE_STATUS_MAP[cat.lifeStatus].text : '未知',
      lifeStatusClass: LIFE_STATUS_MAP[cat.lifeStatus] ? LIFE_STATUS_MAP[cat.lifeStatus].class : 'info',
      genderText: GENDER_MAP[cat.gender] || '未知'
    };
  },

  // 置顶项与分页列表分开存，避免 onShow 的置顶被下一页 setData 冲掉
  refreshDisplay() {
    const focus = this.data.focusedCat;
    const rest = focus
      ? this.data.catList.filter(cat => cat.id !== focus.id)
      : this.data.catList;
    this.setData({ displayList: focus ? [focus, ...rest] : rest });
  },

  async focusCat(id) {
    try {
      const cat = await catApi.getDetail(id);
      this.setData({ focusedCat: this.decorate(cat) });
      this.refreshDisplay();
    } catch (err) {
      console.error('加载选中的猫咪失败:', err);
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
      const cats = (result.records || []).map(cat => this.decorate(cat));
      this.setData({
        catList: this.data.pageNum === 1 ? cats : [...this.data.catList, ...cats],
        hasMore: result.current < result.pages,
        pageNum: this.data.pageNum + 1
      });
      this.refreshDisplay();
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
      catList: [],
      focusedCat: null
    });
    this.loadCatList();
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/cat/detail?id=${id}` });
  }
});
