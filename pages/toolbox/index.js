const care = require('../../utils/care.js');

Page({
  data: {
    cat: null,
    cats: [],
    todayRecords: [],
    missingTypes: [],
    hasRecords: false
  },

  onShow() {
    this.refresh();
  },

  refresh() {
    const cat = care.getCurrentCat();
    const cats = care.getCats();
    const all = care.getRecords(cat.id);
    const today = care.dayKey(new Date().toISOString());
    const todayRecords = all
      .filter((r) => care.dayKey(r.time) === today)
      .map((r) => {
        const meta = care.TYPES[r.type] || { label: '记录', short: '记' };
        return {
          ...r,
          label: meta.label,
          short: meta.short,
          unit: meta.unit,
          timeText: care.formatTime(r.time),
          desc: r.medName ? `${meta.label} · ${r.medName}` : `${meta.label}${r.amount ? ` · ${r.amount}${meta.unit || ''}` : ''}`
        };
      });
    const recorded = {};
    todayRecords.forEach((r) => { recorded[r.type] = true; });
    const missingTypes = ['feed', 'water', 'litter', 'medicine']
      .filter((t) => !recorded[t])
      .map((t) => care.TYPES[t].label);
    this.setData({
      cat,
      cats,
      todayRecords,
      missingTypes,
      hasRecords: all.length > 0
    });
  },

  onManageCats() {
    wx.navigateTo({ url: '/pages/toolbox/cat-manage' });
  },

  onSwitchCat() {
    const cats = care.getCats();
    if (!cats.length) {
      wx.showToast({ title: '暂无猫咪档案，先用临时小猫体验', icon: 'none' });
      return;
    }
    const names = cats.map((c) => `${c.name}${c.breed ? ` · ${c.breed}` : ''}`);
    wx.showActionSheet({
      itemList: names,
      success: (res) => {
        care.setCurrentCat(cats[res.tapIndex].id);
        this.refresh();
      },
      fail: () => {}
    });
  },

  goHealth() {
    wx.navigateTo({ url: `/pages/toolbox/health?id=${this.data.cat.id}` });
  },

  goRecords() {
    wx.navigateTo({ url: `/pages/toolbox/records?id=${this.data.cat.id}` });
  },

  goCompress() {
    wx.navigateTo({ url: '/pages/toolbox/compress' });
  },

  goGame() {
    wx.navigateTo({ url: '/pages/toolbox/game/memory' });
  }
});
