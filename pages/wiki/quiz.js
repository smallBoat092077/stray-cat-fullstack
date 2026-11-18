const { wikiApi } = require('../../utils/api.js');

const DRAFT_KEY = 'wikiQuizDraft';
const CATEGORY_NAME = {
  feeding: '喂养习惯',
  health: '健康观察',
  behavior: '行为沟通',
  hygiene: '清洁护理',
  firstaid: '应急处理'
};

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];

Page({
  data: {
    questions: [],
    current: 0,
    answers: {}, // questionId -> optionIndex
    currentCategory: '',
    progressWidth: '0%',
    loading: true,
    error: false,
    submitting: false
  },

  onLoad() {
    this.loadQuestions();
  },

  async loadQuestions() {
    this.setData({ loading: true, error: false });
    try {
      const res = await wikiApi.questions(10);
      let questions = Array.isArray(res) ? res : ((res && res.questions) || []);
      questions = questions.map((q, i) => ({
        ...q,
        options: (q.options || []).map((text, oi) => ({ letter: LETTERS[oi] || String(oi + 1), text }))
      }));
      if (questions.length === 0) {
        this.setData({ questions: [], loading: false, error: false });
        return;
      }
      // 本地暂存答案：按 questionId 还原，题目顺序变化也不影响
      let draft = {};
      try {
        draft = wx.getStorageSync(DRAFT_KEY) || {};
      } catch (e) {
        draft = {};
      }
      this.setData({
        questions,
        answers: draft,
        loading: false,
        error: false
      });
      this.syncCurrent();
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  onRetry() {
    this.loadQuestions();
  },

  syncCurrent() {
    const q = this.data.questions[this.data.current];
    const total = this.data.questions.length || 1;
    this.setData({
      currentCategory: CATEGORY_NAME[q && q.category] || '知识测评',
      progressWidth: `${Math.round(((this.data.current + 1) / total) * 100)}%`
    });
  },

  onSelect(e) {
    const index = Number(e.currentTarget.dataset.index);
    const q = this.data.questions[this.data.current];
    if (!q) return;
    const answers = Object.assign({}, this.data.answers);
    answers[q.id] = index;
    this.setData({ answers });
    try {
      wx.setStorageSync(DRAFT_KEY, answers);
    } catch (e) {}
  },

  onPrev() {
    if (this.data.current > 0) {
      this.setData({ current: this.data.current - 1 }, () => this.syncCurrent());
    }
  },

  onNext() {
    if (this.data.current < this.data.questions.length - 1) {
      this.setData({ current: this.data.current + 1 }, () => this.syncCurrent());
    } else {
      this.submit();
    }
  },

  currentAnswered() {
    const q = this.data.questions[this.data.current];
    return !!(q && this.data.answers[q.id] !== undefined);
  },

  async submit() {
    if (this.data.submitting) return;
    const questions = this.data.questions;
    const answersMap = this.data.answers;
    const missingIndex = questions.findIndex((q) => answersMap[q.id] === undefined);
    if (missingIndex >= 0) {
      wx.showToast({ title: '还有题目未作答', icon: 'none' });
      this.setData({ current: missingIndex }, () => this.syncCurrent());
      return;
    }
    this.setData({ submitting: true });
    try {
      const answers = questions.map((q) => ({ questionId: q.id, optionIndex: answersMap[q.id] }));
      const result = await wikiApi.submitQuiz(answers);
      wx.setStorageSync('wikiQuizResult', result);
      try {
        wx.removeStorageSync(DRAFT_KEY);
      } catch (e) {}
      wx.redirectTo({ url: '/pages/wiki/result' });
    } catch (e) {
      wx.showToast({ title: (e && e.message) || '提交失败，请重试', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  }
});
