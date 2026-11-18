const { resolveImageUrl } = require('../../utils/api.js');

const DIMENSIONS = [
  { category: 'feeding', name: '喂养' },
  { category: 'health', name: '健康观察' },
  { category: 'behavior', name: '行为沟通' },
  { category: 'hygiene', name: '清洁护理' },
  { category: 'firstaid', name: '应急处理' }
];

function formatViews(n) {
  if (n == null) return '0';
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
  return String(n);
}

Page({
  data: {
    result: null,
    score: 0,
    correctCount: 0,
    portrait: '',
    ringStyle: '',
    weakNames: [],
    bars: [],
    recommended: [],
    loading: true
  },

  onLoad() {
    let result = null;
    try {
      result = wx.getStorageSync('wikiQuizResult');
    } catch (e) {}
    if (!result || result.score == null) {
      wx.showToast({ title: '暂无测评结果', icon: 'none' });
      this.setData({ loading: false });
      return;
    }
    this.build(result);
  },

  build(result) {
    const score = Number(result.score) || 0;
    const weak = Array.isArray(result.weakCategories) ? result.weakCategories : [];
    const scores = result.dimensionScores || result.categoryScores || {};
    const bars = DIMENSIONS.map((d) => {
      const isWeak = weak.indexOf(d.category) >= 0;
      let value = scores[d.category];
      if (value == null) value = isWeak ? 40 : Math.max(60, score);
      value = Math.max(0, Math.min(100, Number(value) || 0));
      return {
        ...d,
        value,
        isWeak,
        barStyle: `width: ${value}%`
      };
    });
    const weakNames = weak.map((c) => {
      const d = DIMENSIONS.find((x) => x.category === c);
      return d ? d.name : c;
    });
    const recommended = (Array.isArray(result.recommended) ? result.recommended : []).map((a) => ({
      ...a,
      cover: resolveImageUrl(a.coverUrl || a.cover || ''),
      viewsText: formatViews(a.viewCount),
      readText: a.readMinutes ? `约 ${a.readMinutes} 分钟` : ''
    }));
    this.setData({
      result,
      score,
      correctCount: result.correctCount != null ? result.correctCount : 0,
      portrait: result.portrait || '稳扎稳打型铲屎官',
      ringStyle: `background: conic-gradient(#C3A15F 0 ${score}%, #EFEBDD ${score}% 100%)`,
      weakNames,
      bars,
      recommended,
      loading: false
    });
  },

  goArticle(e) {
    wx.navigateTo({ url: `/pages/wiki/article?id=${e.currentTarget.dataset.id}` });
  },

  goQuiz() {
    wx.navigateTo({ url: '/pages/wiki/quiz' });
  },

  goHome() {
    wx.navigateBack({ delta: 1 });
  },

  onShareAppMessage() {
    return {
      title: `我的养猫知识指数 ${this.data.score} 分 · 猫遇猫舍`,
      path: '/pages/wiki/index',
      imageUrl: '/images/share-cover.png'
    };
  }
});
