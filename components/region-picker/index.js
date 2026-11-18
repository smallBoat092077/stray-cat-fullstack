/**
 * 区域筛选通用组件（区域划分需求 R3/R4，功能设计 §3.1）
 *
 * 三页复用（猫咪 / 投喂 / 寻猫广场）。交互口径：
 * - 默认过滤优先级：手选记忆（storage regionFilter）> 定位解析（区级，D3）> 全部；
 * - 定位解析静默进行：失败/未登录/未授权不弹窗不阻断，落到「全部」口径；
 *   主动定位走「📍」按钮，才触发完整授权链路（location.js 约束：冷启动不弹隐私窗）；
 * - 级联面板：省 -> 市 -> 区；直辖市省级下无市级子节点，选省后直接出区级（市码=省码）；
 * - 「全市」选项支持只按市过滤；「全部」清过滤并清记忆；
 * - 空态引导（D6）：列表为空且有区级过滤时显示引导条，
 *   「切换到市」临时重查不覆盖手选记忆，「查看全部」清过滤。
 *
 * 事件：bind:change，detail = { provinceCode, cityCode, districtCode, label }
 *      （全空 = 不过滤，列表按全部口径查询）
 */
const { regionApi, geoApi } = require('../../utils/api');
const { getCurrentLocation, notifyError } = require('../../utils/location');

const STORAGE_KEY = 'regionFilter';

Component({
  data: {
    label: '全部',
    hasFilter: false,
    currentFilter: null,
    // 面板
    panelVisible: false,
    provinces: [],
    cities: [],
    districts: [],
    selProvince: null,
    selCity: null,
    // 直辖市：省级下无市级子节点，市码=省码
    municipality: false,
    locating: false
  },

  lifetimes: {
    attached() {
      this.initDefault();
    }
  },

  methods: {
    // ---------- 默认过滤 ----------
    async initDefault() {
      // 1) 手选记忆优先
      const saved = wx.getStorageSync(STORAGE_KEY);
      if (saved && saved.label) {
        this.applyFilter(saved, false);
        return;
      }
      // 2) 静默定位解析（失败/未登录/未授权全部静默落到全部口径）
      try {
        const loc = await getCurrentLocation({ autoPrompt: false });
        const geo = await geoApi.reverse(loc.latitude, loc.longitude);
        if (geo && geo.success && geo.districtCode) {
          this.applyFilter({
            provinceCode: geo.provinceCode,
            cityCode: geo.cityCode,
            districtCode: geo.districtCode,
            label: this.buildLabel(geo.districtName, geo.cityName || geo.provinceName, geo.provinceName)
          }, false);
        }
        // 区级解析不到（如字典未同步）保持全部口径，不打扰用户
      } catch (err) {
        // 静默：定位失败不过滤
      }
    },

    buildLabel(districtName, cityName, provinceName) {
      if (districtName && cityName) {
        return districtName === cityName ? districtName : `${districtName} · ${cityName}`;
      }
      if (cityName) {
        return cityName;
      }
      return provinceName || '全部';
    },

    applyFilter(filter, save) {
      const f = {
        provinceCode: filter.provinceCode || '',
        cityCode: filter.cityCode || '',
        districtCode: filter.districtCode || '',
        label: filter.label || '全部'
      };
      const hasFilter = !!(f.provinceCode || f.cityCode || f.districtCode);
      this.setData({ label: f.label, hasFilter, currentFilter: f });
      if (save) {
        wx.setStorageSync(STORAGE_KEY, f);
      }
      this.triggerEvent('change', {
        provinceCode: f.provinceCode,
        cityCode: f.cityCode,
        districtCode: f.districtCode,
        label: f.label
      });
    },

    // ---------- 主动定位 ----------
    async onLocate() {
      if (this.data.locating) return;
      this.setData({ locating: true });
      try {
        const loc = await getCurrentLocation();
        const geo = await geoApi.reverse(loc.latitude, loc.longitude);
        if (geo && geo.success && geo.provinceCode) {
          this.applyFilter({
            provinceCode: geo.provinceCode,
            cityCode: geo.cityCode || '',
            districtCode: geo.districtCode || '',
            label: this.buildLabel(geo.districtName, geo.cityName || geo.provinceName, geo.provinceName)
          }, true);
          wx.showToast({ title: `已定位：${geo.districtName || geo.cityName || geo.provinceName}`, icon: 'none' });
        } else {
          wx.showToast({ title: geo && geo.geoConfigured ? '未能解析所在区域' : '区域服务未配置，暂按全部展示', icon: 'none' });
        }
      } catch (err) {
        notifyError(err);
      } finally {
        this.setData({ locating: false });
      }
    },

    // ---------- 级联面板 ----------
    async openPanel() {
      this.setData({ panelVisible: true });
      if (!this.data.provinces.length) {
        try {
          const provinces = await regionApi.children('');
          this.setData({ provinces: provinces || [] });
        } catch (err) {
          wx.showToast({ title: '区域数据加载失败', icon: 'none' });
        }
      }
    },

    closePanel() {
      this.setData({ panelVisible: false });
    },

    noop() {},

    async tapProvince(e) {
      const p = e.currentTarget.dataset.item;
      this.setData({ selProvince: p, selCity: null, cities: [], districts: [], municipality: false });
      try {
        const children = await regionApi.children(p.code);
        const list = children || [];
        if (list.length && list[0].level === 3) {
          // 直辖市：省级下直接是区级，市码=省码
          this.setData({ municipality: true, districts: list });
        } else {
          this.setData({ cities: list });
        }
      } catch (err) {
        wx.showToast({ title: '区域数据加载失败', icon: 'none' });
      }
    },

    async tapCity(e) {
      const c = e.currentTarget.dataset.item;
      this.setData({ selCity: c, districts: [] });
      try {
        const districts = await regionApi.children(c.code);
        this.setData({ districts: districts || [] });
      } catch (err) {
        wx.showToast({ title: '区域数据加载失败', icon: 'none' });
      }
    },

    tapDistrict(e) {
      const d = e.currentTarget.dataset.item;
      const selProvince = this.data.selProvince;
      const selCity = this.data.selCity;
      this.applyFilter({
        provinceCode: selProvince.code,
        cityCode: selCity ? selCity.code : selProvince.code,
        districtCode: d.code,
        label: this.buildLabel(d.name, selCity ? selCity.name : selProvince.name, selProvince.name)
      }, true);
      this.setData({ panelVisible: false });
    },

    // 「全市」：只按市过滤
    tapWholeCity() {
      const selProvince = this.data.selProvince;
      const selCity = this.data.selCity;
      if (selCity) {
        this.applyFilter({
          provinceCode: selProvince.code,
          cityCode: selCity.code,
          districtCode: '',
          label: selCity.name
        }, true);
      } else if (this.data.municipality) {
        this.applyFilter({
          provinceCode: selProvince.code,
          cityCode: selProvince.code,
          districtCode: '',
          label: selProvince.name
        }, true);
      }
      this.setData({ panelVisible: false });
    },

    tapWholeProvince() {
      const selProvince = this.data.selProvince;
      if (selProvince) {
        this.applyFilter({
          provinceCode: selProvince.code,
          cityCode: '',
          districtCode: '',
          label: selProvince.name
        }, true);
      }
      this.setData({ panelVisible: false });
    },

    // ---------- 全部 ----------
    clearFilter() {
      wx.removeStorageSync(STORAGE_KEY);
      this.applyFilter({ label: '全部' }, false);
    },

    // ---------- 供页面空状态调用：提升到市级重查（不覆盖手选记忆） ----------
    switchToCity() {
      const cur = this.data.currentFilter || {};
      const cityCode = cur.cityCode || cur.provinceCode || '';
      if (!cityCode) {
        return;
      }
      // 市级名称：优先取 label 中「区 · 市」的市段，取不到就维持原 label
      const parts = String(cur.label || '').split(' · ');
      const cityLabel = parts.length > 1 ? parts[1] : cur.label;
      this.applyFilter({
        provinceCode: cur.provinceCode || '',
        cityCode,
        districtCode: '',
        label: cityLabel || '全市'
      }, false);
    },

    viewAll() {
      this.clearFilter();
    }
  }
});
