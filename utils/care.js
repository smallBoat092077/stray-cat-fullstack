/**
 * 猫咪工具箱本地能力（一期纯本地，无后端）
 * - 记录 key：careRecords:{catId}，结构 [{id,type,time,amount,medName,photo,remark,createdAt}]
 * - 健康指数按方案 3.2 权重在前端计算，仅反映「记录规律度」，不构成健康诊断
 */

const TEMP_CAT = { id: 'temp', name: '临时小猫', breed: '', isTemp: true };
const CAT_CACHE_KEYS = ['catList', 'cat_profile', 'cats', 'catProfiles'];
const CURRENT_CAT_KEY = 'toolboxCatId';
const MY_CATS_KEY = 'toolboxMyCats';

const TYPES = {
  feed: { label: '进食', short: '食', unit: 'g' },
  water: { label: '喝水', short: '水', unit: 'ml' },
  litter: { label: '排泄', short: '泄', unit: '次' },
  medicine: { label: '吃药', short: '药', unit: '次' }
};

const TYPE_LIST = [
  { key: '', label: '全部' },
  { key: 'feed', label: '进食' },
  { key: 'water', label: '喝水' },
  { key: 'litter', label: '排泄' },
  { key: 'medicine', label: '吃药' }
];

function normalizeCat(c) {
  return {
    id: c.id != null ? c.id : (c.catId != null ? c.catId : ''),
    name: c.catName || c.name || '猫咪',
    breed: c.breed || c.breedName || ''
  };
}

// ---------- 我的猫咪（本地管理，可关联平台档案或本地新建） ----------

function getMyCats() {
  try {
    const list = wx.getStorageSync(MY_CATS_KEY);
    return Array.isArray(list) ? list : [];
  } catch (e) {
    return [];
  }
}

function saveMyCats(list) {
  try {
    wx.setStorageSync(MY_CATS_KEY, list);
  } catch (e) {}
}

/** 添加本地猫咪：{name, breed} → 返回新猫，并设为当前 */
function addLocalCat(name, breed) {
  const cat = {
    id: `local_${Date.now()}`,
    name: (name || '').trim() || '我的猫咪',
    breed: (breed || '').trim(),
    source: 'local'
  };
  const list = getMyCats();
  list.unshift(cat);
  saveMyCats(list);
  setCurrentCat(cat.id);
  return cat;
}

/** 关联平台档案（cat_profile）：已存在则只设当前，不存在则加入 */
function addPlatformCat(c) {
  const cat = {
    id: c.id != null ? String(c.id) : '',
    name: c.catName || c.name || '猫咪',
    breed: c.breed || c.breedName || '',
    source: 'platform'
  };
  if (!cat.id) return null;
  const list = getMyCats();
  const exists = list.find((x) => String(x.id) === cat.id);
  if (!exists) {
    list.unshift(cat);
    saveMyCats(list);
  }
  setCurrentCat(cat.id);
  return exists || cat;
}

/** 删除我的猫咪（不删平台档案；若是当前猫则清空当前选择） */
function removeCat(id) {
  const list = getMyCats().filter((c) => String(c.id) !== String(id));
  saveMyCats(list);
  try {
    if (String(wx.getStorageSync(CURRENT_CAT_KEY) || '') === String(id)) {
      wx.removeStorageSync(CURRENT_CAT_KEY);
    }
  } catch (e) {}
  return list;
}

function getCats() {
  const mine = getMyCats();
  if (mine.length) return mine;
  for (let i = 0; i < CAT_CACHE_KEYS.length; i++) {
    try {
      const v = wx.getStorageSync(CAT_CACHE_KEYS[i]);
      if (Array.isArray(v) && v.length) {
        return v.map(normalizeCat).filter((c) => c.id !== '');
      }
    } catch (e) {}
  }
  return [];
}

function getCurrentCat() {
  const cats = getCats();
  let savedId = '';
  try {
    savedId = wx.getStorageSync(CURRENT_CAT_KEY) || '';
  } catch (e) {}
  const found = cats.find((c) => String(c.id) === String(savedId));
  if (found) return found;
  if (cats.length) return cats[0];
  return TEMP_CAT;
}

function setCurrentCat(id) {
  try {
    wx.setStorageSync(CURRENT_CAT_KEY, id);
  } catch (e) {}
}

function recordKey(catId) {
  return `careRecords:${catId}`;
}

function getRecords(catId) {
  try {
    const list = wx.getStorageSync(recordKey(catId));
    return Array.isArray(list) ? list : [];
  } catch (e) {
    return [];
  }
}

function setRecords(catId, list) {
  try {
    wx.setStorageSync(recordKey(catId), list);
  } catch (e) {}
}

function saveRecord(catId, record) {
  const list = getRecords(catId);
  const item = Object.assign({}, record, {
    id: record.id || `r_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
    createdAt: record.createdAt || new Date().toISOString()
  });
  const exists = list.findIndex((r) => String(r.id) === String(item.id));
  if (exists >= 0) list[exists] = item;
  else list.unshift(item);
  setRecords(catId, list);
  return item;
}

function getRecord(catId, id) {
  return getRecords(catId).find((r) => String(r.id) === String(id)) || null;
}

function deleteRecord(catId, id) {
  const list = getRecords(catId).filter((r) => String(r.id) !== String(id));
  setRecords(catId, list);
  return list;
}

function dayKey(time) {
  if (!time) return '';
  const d = new Date(time);
  if (isNaN(d.getTime())) return String(time).slice(0, 10);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function inWindow(record, days) {
  if (!record.time) return false;
  const t = new Date(record.time).getTime();
  if (isNaN(t)) return false;
  return t >= Date.now() - days * 24 * 60 * 60 * 1000;
}

/**
 * 健康指数：近 N 天记录规律度评分（总分 100）
 * 进食 25 / 饮水 20 / 排泄 20 / 用药 15 / 记录完整 20
 */
function calcHealth(records, days) {
  const recent = (records || []).filter((r) => inWindow(r, days));
  const byType = { feed: [], water: [], litter: [], medicine: [] };
  recent.forEach((r) => {
    if (byType[r.type]) byType[r.type].push(r);
  });

  const daySet = (arr) => {
    const s = {};
    arr.forEach((r) => { s[dayKey(r.time)] = true; });
    return Object.keys(s).length;
  };

  const feedDays = daySet(byType.feed);
  const waterDays = daySet(byType.water);
  const litterDays = daySet(byType.litter);
  const medDays = daySet(byType.medicine);

  // 进食稳定性：每日次数越一致系数越接近 1
  const perDay = {};
  byType.feed.forEach((r) => { const k = dayKey(r.time); perDay[k] = (perDay[k] || 0) + 1; });
  const counts = Object.keys(perDay).map((k) => perDay[k]);
  let stability = 1;
  if (counts.length > 1) {
    const mean = counts.reduce((a, b) => a + b, 0) / counts.length;
    const variance = counts.reduce((a, b) => a + (b - mean) * (b - mean), 0) / counts.length;
    stability = Math.max(0.7, 1 - (Math.sqrt(variance) / (mean || 1)) * 0.3);
  }

  // 排泄连续性：最长连续天数占比
  const litterDayList = Object.keys(byType.litter.reduce((acc, r) => {
    acc[dayKey(r.time)] = true;
    return acc;
  }, {})).sort();
  let streak = 0;
  let maxStreak = 0;
  let prev = null;
  litterDayList.forEach((k) => {
    const cur = new Date(`${k}T00:00:00`).getTime();
    if (prev != null && cur - prev === 86400000) streak++;
    else streak = 1;
    prev = cur;
    if (streak > maxStreak) maxStreak = streak;
  });

  const ratio = (n) => Math.min(1, n / days);
  const feedScore = Math.round(25 * ratio(feedDays) * stability);
  const waterScore = Math.round(20 * ratio(waterDays));
  const litterScore = Math.round(20 * (0.7 * ratio(litterDays) + 0.3 * ratio(maxStreak)));
  const medScore = Math.round(15 * ratio(medDays));
  const covered = [feedDays, waterDays, litterDays, medDays].filter((n) => n > 0).length;
  const completeScore = Math.round(20 * (covered / 4));

  const bars = [
    { key: 'feed', label: '进食规律', value: feedScore, max: 25 },
    { key: 'water', label: '饮水规律', value: waterScore, max: 20 },
    { key: 'litter', label: '排泄规律', value: litterScore, max: 20 },
    { key: 'medicine', label: '用药执行', value: medScore, max: 15 },
    { key: 'complete', label: '记录完整', value: completeScore, max: 20 }
  ].map((b) => Object.assign({}, b, {
    barStyle: `width: ${Math.round((b.value / b.max) * 100)}%`
  }));

  const score = feedScore + waterScore + litterScore + medScore + completeScore;

  const lowest = bars.slice().sort((a, b) => (a.value / a.max) - (b.value / b.max))[0];
  const tipMap = {
    feed: '这周进食记录偏少，可以留意它每天的吃饭次数与食量。',
    water: '这周饮水记录偏少，可以留意它每天喝了多少水。',
    litter: '排泄记录还可以更连续一些，方便日常观察。',
    medicine: '用药记录较少，如有按备注用药可以补记一下。',
    complete: '记录类型还不全，慢慢补齐就好，不用着急。'
  };
  let tip = tipMap[lowest.key] || tipMap.complete;
  if (score >= 85) tip = '这周记录很稳定，继续保持，谢谢你的用心照顾。';

  return { score, bars, tip, days, recordCount: recent.length };
}

function formatTime(time) {
  if (!time) return '';
  const d = new Date(time);
  if (isNaN(d.getTime())) return String(time).slice(0, 16);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}

module.exports = {
  TYPES,
  TYPE_LIST,
  TEMP_CAT,
  getMyCats,
  addLocalCat,
  addPlatformCat,
  removeCat,
  getCats,
  getCurrentCat,
  setCurrentCat,
  recordKey,
  getRecords,
  saveRecord,
  getRecord,
  deleteRecord,
  dayKey,
  calcHealth,
  formatTime
};
