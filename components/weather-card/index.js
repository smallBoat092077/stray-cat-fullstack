/**
 * 首页天气卡片（天气功能 R1/R2，功能设计 §4）。
 *
 * 口径：
 * - 区域跟随用户手选记忆（storage regionFilter），不主动触发定位、不弹隐私窗；
 *   优先级 区码 > 市码 > 省码 > 默认武汉(420100)；
 * - 数据来自后端 GET /weather（后端缓存30min + 静默降级）；
 * - 加载失败 / 无实况数据 → 整卡不渲染，绝不影响首页其他模块；
 * - 缓存 key 与后端一致按 adcode，页面内也做一次内存记忆避免重复请求。
 */
const { weatherApi } = require('../../utils/api');

const STORAGE_KEY = 'regionFilter';
const DEFAULT_ADCODE = '420100';

// 天气现象 -> 轻量符号（不引外部图标，保持平和）
const WEATHER_ICON = {
  '晴': '☀', '多云': '⛅', '阴': '☁', '小雨': '🌦', '中雨': '🌧',
  '大雨': '🌧', '暴雨': '⛈', '雷阵雨': '⛈', '雪': '❄', '雾': '🌫',
  '霾': '🌫', '雨夹雪': '🌨'
};

function iconOf(weather) {
  if (!weather) return '🌤';
  for (const key of Object.keys(WEATHER_ICON)) {
    if (weather.indexOf(key) >= 0) return WEATHER_ICON[key];
  }
  return '🌤';
}

function shortDate(date) {
  if (!date) return '';
  const s = String(date);
  return s.length > 5 ? s.slice(-5) : s;
}

Component({
  data: {
    visible: false,
    cityName: '',
    icon: '🌤',
    weather: '',
    temperature: null,
    humidity: null,
    windText: '',
    future: []
  },

  lifetimes: {
    attached() {
      this.load();
    }
  },

  methods: {
    resolveAdcode() {
      let saved = null;
      try {
        saved = wx.getStorageSync(STORAGE_KEY) || {};
      } catch (e) {
        saved = {};
      }
      return saved.districtCode || saved.cityCode || saved.provinceCode || DEFAULT_ADCODE;
    },

    async load() {
      const adcode = this.resolveAdcode();
      try {
        const data = await weatherApi.get(adcode);
        const now = data && data.now;
        // 没有实况（腾讯失败/限流/未配置）→ 不渲染卡片
        if (!now || (now.weather == null && now.temperature == null)) {
          this.setData({ visible: false });
          return;
        }
        const future = (data.future || []).map((d) => ({
          ...d,
          dateText: shortDate(d.date),
          icon: iconOf(d.weather)
        }));
        let windText = '';
        if (now.windDirection || now.windPower) {
          windText = [now.windDirection, now.windPower].filter(Boolean).join(' ');
        }
        this.setData({
          visible: true,
          cityName: data.cityName || '',
          icon: iconOf(now.weather),
          weather: now.weather || '',
          temperature: now.temperature,
          humidity: now.humidity,
          windText,
          future
        });
      } catch (err) {
        // 静默降级：接口异常不渲染卡片
        this.setData({ visible: false });
      }
    }
  }
});
