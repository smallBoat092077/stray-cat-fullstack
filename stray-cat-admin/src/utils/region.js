/**
 * 区域字典工具（区域划分需求 R5）：加载一次 /region/tree，模块级缓存，
 * 提供 code -> 名称映射与 el-cascader 选项，猫咪/寻猫两个页面共用。
 */
import { regionApi } from '../api';

let treePromise = null;
const nameMap = new Map(); // code -> name

function indexTree(nodes) {
  for (const n of nodes || []) {
    nameMap.set(n.code, n.name);
    indexTree(n.children);
  }
}

export function loadRegionTree(force = false) {
  if (!treePromise || force) {
    treePromise = regionApi.tree().then((tree) => {
      nameMap.clear();
      indexTree(tree);
      return tree;
    });
  }
  return treePromise;
}

export function regionNames(codes = {}) {
  const p = codes.provinceCode ? nameMap.get(codes.provinceCode) : '';
  const c = codes.cityCode ? nameMap.get(codes.cityCode) : '';
  const d = codes.districtCode ? nameMap.get(codes.districtCode) : '';
  return [p, c, d].filter(Boolean).join(' / ');
}

export function regionNameMap() {
  return nameMap;
}
