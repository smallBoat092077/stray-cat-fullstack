const care = require('../../utils/care.js');

const RANGES = [
  { days: 7, label: '近 7 天' },
  { days: 30, label: '近 30 天' }
];

Page({
  data: {
    cat: null,
    ranges: RANGES,
    days: 7,
    score: 0,
    bars: [],
    tip: '',
    ringStyle: '',
    recordCount: 0
  },

  onLoad(options) {
    const cat = care.getCurrentCat();
    this.setData({ cat, days: options.days ? Number(options.days) : 7 });
    this.calc();
  },

  onShow() {
    // 从记录页返回后分数即时刷新
    if (this.data.cat) this.calc();
  },

  onRange(e) {
    const days = Number(e.currentTarget.dataset.days);
    if (days === this.data.days) return;
    this.setData({ days });
    this.calc();
  },

  calc() {
    const records = care.getRecords(this.data.cat.id);
    const res = care.calcHealth(records, this.data.days);
    this.setData({
      score: res.score,
      bars: res.bars,
      tip: res.tip,
      recordCount: res.recordCount,
      ringStyle: `background: conic-gradient(#C3A15F 0 ${res.score}%, #EFEBDD ${res.score}% 100%)`
    });
  },

  goRecords() {
    wx.navigateTo({ url: `/pages/toolbox/records?id=${this.data.cat.id}` });
  }
});
