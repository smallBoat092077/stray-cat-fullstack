const care = require('../../utils/care.js');

const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六'];

function groupRecords(records) {
  const groups = {};
  records.forEach((r) => {
    const key = care.dayKey(r.time);
    if (!groups[key]) groups[key] = [];
    groups[key].push(r);
  });
  const todayKey = care.dayKey(new Date().toISOString());
  const yesterday = new Date(Date.now() - 86400000);
  const yesterdayKey = care.dayKey(yesterday.toISOString());
  return Object.keys(groups).sort().reverse().map((key) => {
    const list = groups[key].map((r) => {
      const meta = care.TYPES[r.type] || { label: '记录', short: '记', unit: '' };
      return {
        ...r,
        label: meta.label,
        short: meta.short,
        unit: meta.unit,
        timeText: care.formatTime(r.time),
        desc: r.medName
          ? `${meta.label} · ${r.medName}${r.remark ? ' · ' + r.remark : ''}`
          : `${meta.label}${r.amount ? ` · ${r.amount}${meta.unit || ''}` : ''}${r.remark ? ' · ' + r.remark : ''}`
      };
    }).sort((a, b) => String(b.time).localeCompare(String(a.time)));
    let dateText = key;
    if (key === todayKey) dateText = '今天';
    else if (key === yesterdayKey) dateText = '昨天';
    else {
      const parts = key.split('-');
      dateText = `${Number(parts[1])} 月 ${Number(parts[2])} 日`;
    }
    return { key, dateText, count: list.length, list };
  });
}

Page({
  data: {
    cat: null,
    types: care.TYPE_LIST,
    activeType: '',
    week: [],
    groups: [],
    allCount: 0,
    loading: true
  },

  onLoad(options) {
    const cat = care.getCurrentCat();
    this.setData({ cat });
    if (options && options.type) this.setData({ activeType: options.type });
    this.refresh();
  },

  onShow() {
    if (this.data.cat) this.refresh();
  },

  refresh() {
    const records = care.getRecords(this.data.cat.id);
    const filtered = this.data.activeType
      ? records.filter((r) => r.type === this.data.activeType)
      : records;

    // 周历小热力：最近 7 天（含今天）
    const hasByDay = {};
    records.forEach((r) => { hasByDay[care.dayKey(r.time)] = true; });
    const week = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date(Date.now() - i * 86400000);
      const key = care.dayKey(d.toISOString());
      week.push({
        key,
        label: WEEK_LABELS[d.getDay()],
        day: d.getDate(),
        has: !!hasByDay[key]
      });
    }

    this.setData({
      week,
      groups: groupRecords(filtered),
      allCount: records.length,
      loading: false
    });
  },

  onManageCats() {
    wx.navigateTo({ url: '/pages/toolbox/cat-manage' });
  },

  onFilter(e) {
    const type = e.currentTarget.dataset.type || '';
    if (type === this.data.activeType) return;
    this.setData({ activeType: type });
    this.refresh();
  },

  onAdd() {
    wx.navigateTo({ url: `/pages/toolbox/record-edit?id=${this.data.cat.id}` });
  },

  onDelete(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '删除记录',
      content: '确定删除这条记录吗？删除后健康指数会同步变化。',
      success: (res) => {
        if (!res.confirm) return;
        care.deleteRecord(this.data.cat.id, id);
        wx.showToast({ title: '已删除', icon: 'success' });
        this.refresh();
      }
    });
  }
});
