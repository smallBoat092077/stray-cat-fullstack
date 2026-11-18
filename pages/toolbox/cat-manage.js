const care = require('../../utils/care.js');
const { catApi } = require('../../utils/api');

Page({
  data: {
    mode: 'list',            // list 我的猫咪 / pick 选平台档案 / form 手动添加
    pickTab: 'mine',         // mine 我上报的 / search 搜索
    cats: [],                // 我的猫咪
    currentId: '',
    mineCats: [],            // 平台：我上报的
    mineLoaded: false,
    searchResults: [],
    kw: '',
    searching: false,
    formName: '',
    formBreed: '',
    saving: false,
    loggedIn: true
  },

  onShow() {
    this.refreshList();
    if (this.data.mode === 'pick' && this.data.pickTab === 'mine') this.loadMine();
  },

  refreshList() {
    const cats = care.getCats();
    const current = care.getCurrentCat();
    this.setData({
      cats: cats.map((c) => ({
        ...c,
        sourceLabel: c.source === 'platform' ? '平台档案' : '本地',
        isCurrent: String(c.id) === String(current.id)
      })),
      currentId: current.id
    });
  },

  backToList() {
    this.setData({ mode: 'list' });
    this.refreshList();
  },

  // ---------- 选择方式 ----------
  onAddTap() {
    wx.showActionSheet({
      itemList: ['从平台档案选', '手动添加'],
      success: (res) => {
        if (res.tapIndex === 0) {
          this.setData({ mode: 'pick', pickTab: 'mine', mineLoaded: false, searchResults: [], kw: '' });
          this.loadMine();
        } else {
          this.setData({ mode: 'form', formName: '', formBreed: '' });
        }
      },
      fail: () => {}
    });
  },

  onPickTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ pickTab: tab });
    if (tab === 'mine') this.loadMine();
  },

  // ---------- 平台档案：我上报的 ----------
  loadMine() {
    const app = getApp();
    const token = app && app.globalData ? app.globalData.token : '';
    if (!token) {
      this.setData({ loggedIn: false, mineLoaded: true, mineCats: [] });
      return;
    }
    this.setData({ loggedIn: true });
    catApi.getMine().then((list) => {
      const mine = (list || []).map((c) => ({
        id: c.id,
        name: c.catName || c.name || '猫咪',
        breed: c.breed || '',
        added: this.isAdded(c.id)
      }));
      this.setData({ mineCats: mine, mineLoaded: true });
    }).catch(() => {
      this.setData({ mineLoaded: true });
    });
  },

  isAdded(id) {
    return care.getMyCats().some((c) => String(c.id) === String(id));
  },

  // ---------- 平台档案：搜索 ----------
  onKwInput(e) {
    this.setData({ kw: e.detail.value });
  },

  onSearch() {
    const kw = this.data.kw.trim();
    if (!kw) {
      wx.showToast({ title: '输入名字/品种/花色搜索', icon: 'none' });
      return;
    }
    this.setData({ searching: true });
    // 后端 /cat/list 的搜索参数名是 keyword（匹配名字/品种/花色）
    catApi.getList({ keyword: kw, pageNum: 1, pageSize: 20 }).then((page) => {
      const rows = (page && page.records ? page.records : []).map((c) => ({
        id: c.id,
        name: c.catName || c.name || '猫咪',
        breed: c.breed || '',
        added: this.isAdded(c.id)
      }));
      this.setData({ searchResults: rows, searching: false });
    }).catch(() => {
      this.setData({ searching: false });
    });
  },

  onPickCat(e) {
    const id = e.currentTarget.dataset.id;
    const source = this.data.pickTab === 'mine' ? this.data.mineCats : this.data.searchResults;
    const cat = source.find((c) => String(c.id) === String(id));
    if (!cat) return;
    if (cat.added) {
      care.setCurrentCat(cat.id);
      wx.showToast({ title: `已切换到 ${cat.name}`, icon: 'none' });
      this.backToList();
      return;
    }
    care.addPlatformCat(cat);
    wx.showToast({ title: `已添加 ${cat.name}`, icon: 'none' });
    if (this.data.pickTab === 'mine') this.loadMine();
    else this.setData({ searchResults: this.data.searchResults.map((c) => ({ ...c, added: this.isAdded(c.id) })) });
  },

  // ---------- 手动添加 ----------
  onFormName(e) { this.setData({ formName: e.detail.value }); },
  onFormBreed(e) { this.setData({ formBreed: e.detail.value }); },

  onSaveForm() {
    const name = this.data.formName.trim();
    if (!name) {
      wx.showToast({ title: '请填写猫咪名字', icon: 'none' });
      return;
    }
    if (this.data.saving) return;
    this.setData({ saving: true });
    care.addLocalCat(name, this.data.formBreed);
    wx.showToast({ title: '已添加', icon: 'success' });
    this.setData({ saving: false, mode: 'list' });
    this.refreshList();
  },

  // ---------- 列表操作 ----------
  onSelectCat(e) {
    const id = e.currentTarget.dataset.id;
    care.setCurrentCat(id);
    this.refreshList();
    wx.showToast({ title: '已切换', icon: 'none' });
  },

  onRemoveCat(e) {
    const id = e.currentTarget.dataset.id;
    const cat = this.data.cats.find((c) => String(c.id) === String(id));
    if (!cat) return;
    wx.showModal({
      title: '移除猫咪',
      content: `把「${cat.name}」从我的猫咪移除？${cat.source === 'platform' ? '平台档案不受影响。' : '其本地日常记录将无法再查看。'}`,
      confirmText: '移除',
      confirmColor: '#B4552D',
      success: (res) => {
        if (res.confirm) {
          care.removeCat(id);
          this.refreshList();
        }
      }
    });
  },

  goReport() {
    wx.navigateTo({ url: '/pages/cat/report' });
  }
});
