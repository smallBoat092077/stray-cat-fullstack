import { adminApi } from '../api';
import { catApi } from '../api';

/**
 * 后台的数据量级很小，一次拉满 100 条做 id→名字 的映射，
 * 比每行单独查一次接口（N+1）划算，也免得列表加载卡顿。
 */
let catPromise = null;
let userPromise = null;

export function catNameMap() {
  if (!catPromise) {
    catPromise = catApi
      .list({ pageNum: 1, pageSize: 100 })
      .then((page) => {
        const map = {};
        (page.records || []).forEach((cat) => {
          map[cat.id] = cat;
        });
        return map;
      })
      .catch(() => ({}));
  }
  return catPromise;
}

export function userNameMap() {
  if (!userPromise) {
    userPromise = adminApi
      .users({ pageNum: 1, pageSize: 100 })
      .then((page) => {
        const map = {};
        (page.records || []).forEach((user) => {
          map[user.id] = user;
        });
        return map;
      })
      .catch(() => ({}));
  }
  return userPromise;
}

/**
 * 审核动作会让缓存过期，改完数据调用一次
 */
export function invalidateLookups() {
  catPromise = null;
  userPromise = null;
}
