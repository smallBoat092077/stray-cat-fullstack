// 图案池：24 个不重复图案（第三关 24 对需要全部用上）
const PATTERN_POOL = [
  '猫', '鱼', '爪', '铃', '尾', '碗',
  '喵', '肉', '球', '奶', '窝', '罐',
  '线', '毯', '蝶', '雪', '月', '星',
  '茶', '灯', '伞', '花', '叶', '云'
];

// 三关配置：pairs 对数（张数=对数×2），cols 网格列数，cardH 卡片高度(rpx)，
// font 字号(rpx)，gap 间距(rpx)，closeDelay 翻回背面延时(ms，越高越容易记)
const LEVELS = [
  { name: '第一关 · 热身',   pairs: 6,  cols: 4, cardH: 165, font: 52, gap: 14, closeDelay: 700 },
  { name: '第二关 · 进阶',   pairs: 12, cols: 6, cardH: 105, font: 38, gap: 10, closeDelay: 600 },
  { name: '第三关 · 魔鬼',   pairs: 24, cols: 8, cardH: 80,  font: 28, gap: 6,  closeDelay: 500 }
];

function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    const t = a[i];
    a[i] = a[j];
    a[j] = t;
  }
  return a;
}

// 本局图案：从池中随机抽 pairs 个（位置与图案双重随机）
function pickPatterns(pairs) {
  return shuffle(PATTERN_POOL).slice(0, pairs);
}

function pad(n) { return String(n).padStart(2, '0'); }

Page({
  data: {
    levelIdx: 0,
    levelName: LEVELS[0].name,
    levels: LEVELS.map(function (l) { return l.name; }),
    cols: LEVELS[0].cols,
    cardH: LEVELS[0].cardH,
    cardFont: LEVELS[0].font,
    gap: LEVELS[0].gap,
    totalPairs: LEVELS[0].pairs,
    cards: [],
    first: null,
    lock: false,
    steps: 0,
    matched: 0,
    timeText: '00:00',
    msg: ''
  },

  onLoad() {
    this.started = false;
    this.timer = null;
    this.seconds = 0;
    this.reset();
  },

  onUnload() {
    this.clearTimer();
  },

  onHide() {
    this.clearTimer();
  },

  onShow() {
    if (this.started && !this.timer) this.startTimer();
  },

  clearTimer() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  },

  startTimer() {
    this.clearTimer();
    this.timer = setInterval(() => {
      this.seconds += 1;
      this.setData({ timeText: `${pad(Math.floor(this.seconds / 60))}:${pad(this.seconds % 60)}` });
    }, 1000);
  },

  switchLevel(e) {
    const idx = Number(e.currentTarget.dataset.level);
    if (idx === this.data.levelIdx || isNaN(idx)) return;
    this.setData({ levelIdx: idx, levelName: LEVELS[idx].name });
    this.reset();
  },

  levelMsg(level) {
    return `${level.name}：${level.pairs} 对图案，翻错翻回更慢记。翻开两张相同的图案即可配对`;
  },

  reset() {
    this.clearTimer();
    this.started = false;
    this.seconds = 0;
    this.first = null;
    const level = LEVELS[this.data.levelIdx];
    const values = pickPatterns(level.pairs);
    const deck = shuffle(values.concat(values)).map((value, index) => ({
      id: `c${index}`,
      value,
      faceUp: false,
      matched: false
    }));
    this.setData({
      cards: deck,
      cols: level.cols,
      cardH: level.cardH,
      cardFont: level.font,
      gap: level.gap,
      totalPairs: level.pairs,
      first: null,
      lock: false,
      steps: 0,
      matched: 0,
      timeText: '00:00',
      msg: this.levelMsg(level)
    });
  },

  onFlip(e) {
    const index = Number(e.currentTarget.dataset.index);
    const cards = this.data.cards;
    const card = cards[index];
    if (!card || this.data.lock || card.faceUp || card.matched) return;

    if (!this.started) {
      this.started = true;
      this.startTimer();
    }

    const next = cards.slice();
    next[index] = Object.assign({}, card, { faceUp: true });
    this.setData({ cards: next });

    if (this.first === null) {
      this.first = index;
      return;
    }

    const steps = this.data.steps + 1;
    const firstIndex = this.first;
    const firstCard = cards[firstIndex];
    this.setData({ steps });

    if (firstCard.value === card.value) {
      next[firstIndex] = Object.assign({}, firstCard, { faceUp: true, matched: true });
      next[index] = Object.assign({}, card, { faceUp: true, matched: true });
      const matched = this.data.matched + 1;
      this.first = null;
      this.setData({ cards: next, matched });
      if (matched === this.data.totalPairs) {
        this.onLevelClear();
      }
    } else {
      const delay = LEVELS[this.data.levelIdx].closeDelay;
      this.setData({ lock: true });
      const self = this;
      setTimeout(() => {
        const cur = self.data.cards.slice();
        cur[firstIndex] = Object.assign({}, cur[firstIndex], { faceUp: false });
        cur[index] = Object.assign({}, cur[index], { faceUp: false });
        self.first = null;
        self.setData({ cards: cur, lock: false });
      }, delay);
    }
  },

  onLevelClear() {
    this.clearTimer();
    const levelIdx = this.data.levelIdx;
    const level = LEVELS[levelIdx];
    const summary = `用时 ${this.data.timeText}，共 ${this.data.steps} 步。`;

    if (levelIdx < LEVELS.length - 1) {
      const nextLevel = LEVELS[levelIdx + 1];
      this.setData({ msg: `${level.name}全部配对成功！${summary}和小满一样聪明，敢挑战${nextLevel.name}吗？` });
      wx.showModal({
        title: `${level.name}通关`,
        content: `${summary}下一关「${nextLevel.name}」共 ${nextLevel.pairs * 2} 张图案，要继续吗？`,
        confirmText: '下一关',
        cancelText: '留在本关',
        success: (res) => {
          if (res.confirm) {
            this.setData({ levelIdx: levelIdx + 1, levelName: nextLevel.name });
          }
          this.reset();
        }
      });
    } else {
      this.setData({ msg: `${level.name}全部配对成功！${summary}你比小满还厉害！` });
      wx.showModal({
        title: '全部通关',
        content: `三关全部完成！${summary}要再挑战一次魔鬼关吗？`,
        confirmText: '再玩一次',
        cancelText: '先不了',
        success: (res) => { if (res.confirm) this.reset(); }
      });
    }
  },

  onRestart() {
    this.reset();
  }
});
