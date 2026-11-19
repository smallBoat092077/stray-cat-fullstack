// 图案池：24 个不重复图案（第三关 24 对需要全部用上）
const ads = require('../../../utils/ads');

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
  { name: '第三关 · 魔鬼',   pairs: 24, cols: 8, cardH: 80,  font: 28, gap: 6,  closeDelay: 500 },
  // 隐藏大师关：通关魔鬼关后看广告解锁（未解锁时不出现在关卡条）
  { name: '隐藏 · 大师',     pairs: 30, cols: 10, cardH: 64, font: 22, gap: 5, closeDelay: 450, hidden: true }
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

// 关卡条上可见的关卡名（大师关解锁后追加）
function visibleLevelNames(masterUnlocked) {
  return LEVELS
    .filter((l, i) => i < 3 || masterUnlocked)
    .map((l) => l.name);
}

Page({
  data: {
    levelIdx: 0,
    levelName: LEVELS[0].name,
    levels: visibleLevelNames(false),
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
    msg: '',
    masterUnlocked: false,
    levels: visibleLevelNames(false)
  },

  onLoad() {
    this.started = false;
    this.timer = null;
    this.seconds = 0;
    // 广告增益开关：未配置广告位 ID 时为 false，全部广告入口静默隐藏
    this.adEnabled = ads.rewardedAvailable();
    this.previewBonus = false;
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
    if (isNaN(idx) || idx < 0 || idx >= LEVELS.length) return;
    if (LEVELS[idx].hidden && !this.data.masterUnlocked) return;
    if (idx === this.data.levelIdx) return;
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
    this.resetSeq = (this.resetSeq || 0) + 1;
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

    // 广告加成：进入关卡时全部图案先亮 3 秒帮助记忆（看完激励视频才有）
    if (this.previewBonus) {
      this.previewBonus = false;
      const self = this;
      const seq = this.resetSeq; // 预览期间用户切关/重开会推进代次，旧定时器作废
      const revealed = deck.map((c) => Object.assign({}, c, { faceUp: true }));
      this.setData({ cards: revealed, lock: true });
      setTimeout(() => {
        if (self.resetSeq !== seq) return;
        const hidden = self.data.cards.map((c) => Object.assign({}, c, { faceUp: false }));
        self.setData({ cards: hidden, lock: false });
      }, 3000);
    }
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
    const lastPlayable = this.data.masterUnlocked ? LEVELS.length - 1 : 2;

    if (levelIdx < lastPlayable) {
      // 中间关通关：可看广告获得下一关 3 秒预览加成
      const nextLevel = LEVELS[levelIdx + 1];
      this.setData({ msg: `${level.name}全部配对成功！${summary}和小满一样聪明，敢挑战${nextLevel.name}吗？` });
      wx.showModal({
        title: `${level.name}通关`,
        content: `${summary}下一关「${nextLevel.name}」共 ${nextLevel.pairs * 2} 张图案，要继续吗？`,
        confirmText: '下一关',
        cancelText: '留在本关',
        success: (res) => {
          if (!res.confirm) { this.reset(); return; }
          if (this.adEnabled) {
            wx.showModal({
              title: '通关奖励',
              content: '看一段短视频，进入下一关时图案会先亮 3 秒帮你记忆，要吗？',
              confirmText: '看视频加成',
              cancelText: '直接开始',
              success: (r) => {
                if (!r.confirm) { this.enterLevel(levelIdx + 1, nextLevel); return; }
                ads.showRewardedVideo().then((done) => {
                  if (done) {
                    this.previewBonus = true;
                  } else {
                    wx.showToast({ title: '视频未看完，直接开始', icon: 'none' });
                  }
                  this.enterLevel(levelIdx + 1, nextLevel);
                });
              }
            });
          } else {
            this.enterLevel(levelIdx + 1, nextLevel);
          }
        }
      });
    } else if (levelIdx === 2 && this.adEnabled && !this.data.masterUnlocked) {
      // 魔鬼关通关：可看广告解锁隐藏大师关
      this.setData({ msg: `${level.name}全部配对成功！${summary}你比小满还厉害！还有一个隐藏大师关等你挑战。` });
      wx.showModal({
        title: '全部通关',
        content: `三关全部完成！${summary}看一段短视频可解锁隐藏大师关（60 张图案），要挑战吗？`,
        confirmText: '看视频解锁',
        cancelText: '先不了',
        success: (r) => {
          if (!r.confirm) { this.offerReplay(summary); return; }
          ads.showRewardedVideo().then((done) => {
            if (done) {
              this.unlockMaster(summary);
            } else {
              wx.showToast({ title: '视频未看完，下次再解锁吧', icon: 'none' });
              this.offerReplay(summary);
            }
          });
        }
      });
    } else {
      this.setData({ msg: `${level.name}全部配对成功！${summary}你比小满还厉害！` });
      wx.showModal({
        title: '全部通关',
        content: `${level.name}也完成了！${summary}要再挑战一次吗？`,
        confirmText: '再玩一次',
        cancelText: '先不了',
        success: (res) => { if (res.confirm) this.reset(); }
      });
    }
  },

  enterLevel(idx, level) {
    this.setData({ levelIdx: idx, levelName: level.name });
    this.reset();
  },

  unlockMaster(summary) {
    this.setData({
      masterUnlocked: true,
      levels: visibleLevelNames(true),
      msg: `${summary}隐藏大师关已解锁，关卡条最右侧可直接挑战！`
    });
    wx.showModal({
      title: '隐藏大师关已解锁',
      content: '大师关共 60 张图案（30 对 × 10 列），是全游戏最高难度。要现在就挑战吗？',
      confirmText: '马上挑战',
      cancelText: '稍后',
      success: (res) => {
        if (res.confirm) this.enterLevel(3, LEVELS[3]);
      }
    });
  },

  offerReplay(summary) {
    wx.showModal({
      title: '再玩一次？',
      content: `要再挑战一次魔鬼关吗？（${summary}）`,
      confirmText: '再玩一次',
      cancelText: '先不了',
      success: (res) => { if (res.confirm) this.reset(); }
    });
  },

  onRestart() {
    this.reset();
  }
});
