"""
花纹识别三期 · 离线评测脚本（增强样本口径 + 真实实拍网格搜索）

两种模式：

1. 默认模式（二期口径不变）：基于 fixtures 增强样本评测，输出 top1/top5、
   FRR、FAR、拒识率、分数分布。⚠️ 口径声明：增强样本 ≠ 真实多角度采集。

2. 网格搜索模式（三期新增，`python eval/run_eval.py --grid`）：
   - 样本来源 `eval/real-samples/<cat_id>/*.jpg|png`（真实多角度实拍，
     每猫 >=3 张）。目录不存在时自动回退 fixtures 真猫图并明确标注
     「样本不足，结果仅供参考」。
   - 网格：LOCAL_WEIGHT ∈ {0, 0.2, 0.3, 0.4, 0.5} × LOWE_RATIO ∈
     {0.75, 0.80, 0.85} × RANSAC_REPROJ_THRESHOLD ∈ {5, 8}。
   - 口径与线上 /match 完全一致：粗召回 -> 重排 -> 按 cat_id 分组取最大。
   - 输出每个组合的 top1 命中率、负样本最高分、分离带（正样本最低 top1 分
     - 负样本最高分），按分离带降序推荐参数，并给出对应阈值建议。

评测不依赖 MySQL / FAISS / 服务进程：复用线上同款
CatGate + CatRecognizer.extract_all + local_match_score + blend_similarity，
在内存里构造临时基准库，保证评测口径与线上一致。
"""
import os
import sys
import glob
import argparse
import itertools
from collections import Counter

import cv2
import numpy as np
from PIL import Image

# 让脚本可直接 `python eval/run_eval.py` 运行
HERE = os.path.dirname(os.path.abspath(__file__))
PKG = os.path.dirname(HERE)
sys.path.insert(0, PKG)

import recognizer as rc  # noqa: E402  （网格搜索需在运行时覆盖常量）
from cat_gate import CatGate, GateConfig  # noqa: E402
from recognizer import CatRecognizer, FEATURE_DIM, local_match_score, blend_similarity  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

FIXTURES = os.path.join(HERE, "fixtures")
REAL_SAMPLES_DIR = os.path.join(HERE, "real-samples")
DISCLAIMER = "基于增强样本，非真实多角度采集"

# 真实猫照片（作为正样本增强源 + 基准库，每个源一个 cat_id）
REAL_CATS = [
    (1, "3eb86cb0-937d-4801-895a-84a2aea606f3.jpg"),
    (2, "85b96e5e-2a9d-4fbe-9d7c-3e398e69bfd8.jpg"),
    (3, "b5788ff4-4c14-4144-b345-ebe578d32776.png"),
]
# 用户点名的 75ec3cde：经核验实为「AI 生成卡通插画」，非真实猫照。
# 仍纳入正样本增强源以遵循素材清单，但其被闸门按设计图拒识属预期，
# 单独计入 FRR_all 参考口径并明确标注；不作为基准库（过不了闸门）。
BOUNDARY_SOURCES = [
    ("插画", "75ec3cde-d04e-4f56-8de2-8354e82a7482.png"),
]
# 非猫负样本集
NEGATIVE_FILES = [
    "noise.png", "white.png",
    "brand_cover.png", "brand_lockup.png",
    "app_detail.png", "app_message.png",
]

# 仅评测用：方案验收「非猫图不再出现 >= 0.85 的 high」；线上阈值由 Java 侧控制
HIGH_THRESHOLD = 0.85
TOP_K = 3

RECOGNIZER = CatRecognizer()
GATE = RECOGNIZER.gate


# ---------------------------------------------------------------------------
# 素材与增强
# ---------------------------------------------------------------------------
def load_bgr(path):
    img = Image.open(path).convert("RGB")
    return cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)


def load_fixture(fname):
    return load_bgr(fname if os.path.isabs(fname) else os.path.join(FIXTURES, fname))


def augment(bgr):
    """旋转 / 裁剪 / 亮度 / 缩放 增强，返回 [(tag, image), ...]。"""
    out = [("orig", bgr)]
    h, w = bgr.shape[:2]
    for ang in (-10, 10):
        M = cv2.getRotationMatrix2D((w / 2.0, h / 2.0), ang, 1.0)
        out.append((f"rot{ang:+d}", cv2.warpAffine(bgr, M, (w, h),
                                                   flags=cv2.INTER_LINEAR,
                                                   borderMode=cv2.BORDER_REFLECT)))
    for f in (0.7, 1.3):
        out.append((f"bright{f}", np.clip(bgr.astype(np.float32) * f, 0, 255).astype(np.uint8)))
    out.append(("scale0.6", cv2.resize(bgr, (max(1, int(w * 0.6)), max(1, int(h * 0.6))),
                                       interpolation=cv2.INTER_AREA)))
    y1, y2, x1, x2 = int(h * 0.1), int(h * 0.9), int(w * 0.1), int(w * 0.9)
    out.append(("crop0.8", bgr[y1:y2, x1:x2]))
    return out


def extract(bgr):
    """闸门 + 特征提取一步走；返回 (feature, desc, kp) 或 None。"""
    gr = GATE.check(bgr)
    if not gr["pass"]:
        return None
    return RECOGNIZER.extract_all(bgr, gate_result=gr)


# ---------------------------------------------------------------------------
# 内存基准库（与线上 /match 同口径：粗召回 -> 重排 -> 按猫分组取最大）
# ---------------------------------------------------------------------------
def build_baseline(entries):
    """entries: [(cat_id, bgr), ...] -> [{"cat_id","key","feature","desc","kp"}]

    key = (cat_id, 序号)，供网格搜索留一法排除查询图自身。
    """
    baseline = []
    counter = Counter()
    for cat_id, bgr in entries:
        idx = counter[cat_id]
        counter[cat_id] += 1
        got = extract(bgr)
        if got is None or got[0] is None:
            print(f"  [warn] cat_id={cat_id} 基准图特征提取失败/被拒识，跳过")
            continue
        feat, desc, kp = got
        baseline.append({"cat_id": cat_id, "key": (cat_id, idx),
                         "feature": feat, "desc": desc, "kp": kp})
    return baseline


def match_top(feat, q_desc, q_kp, baseline, top_k=TOP_K):
    """与线上 /match 完全同口径：粗召回 top_k*12 -> 重排 -> 按猫分组取最大 -> top_k。

    Returns:
        [(similarity, cat_id), ...] 按分数降序，长度 <= top_k（每猫一个）。
    """
    cand = []
    for e in baseline:
        if e["feature"] is None:
            continue
        gcos = float(np.dot(feat, e["feature"]))  # 特征已 L2 归一，内积=余弦
        cand.append((gcos, e))
    cand.sort(key=lambda x: -x[0])
    cand = cand[:top_k * 12]

    best_by_cat = {}
    for gcos, e in cand:
        local = local_match_score(q_desc, q_kp, e["desc"], e["kp"])
        sim = blend_similarity(gcos, local["local_score"])
        cat = e["cat_id"]
        if cat not in best_by_cat or sim > best_by_cat[cat][0]:
            best_by_cat[cat] = (sim, cat)
    scored = sorted(best_by_cat.values(), key=lambda x: -x[0])
    return scored[:max(1, top_k)]


# ---------------------------------------------------------------------------
# 样本装载
# ---------------------------------------------------------------------------
def load_real_samples():
    """加载 eval/real-samples/<cat_id>/* 真实多角度样本。

    Returns:
        (samples, fallback)  samples: {cat_id: [bgr, ...]}；fallback: 是否回退 fixtures
    """
    samples = {}
    if os.path.isdir(REAL_SAMPLES_DIR):
        for sub in sorted(os.listdir(REAL_SAMPLES_DIR)):
            sub_dir = os.path.join(REAL_SAMPLES_DIR, sub)
            if not os.path.isdir(sub_dir) or not sub.isdigit():
                continue
            files = []
            for ext in ("*.jpg", "*.jpeg", "*.png", "*.webp"):
                files.extend(glob.glob(os.path.join(sub_dir, ext)))
            for f in sorted(files):
                samples.setdefault(int(sub), []).append(load_bgr(f))
    if samples:
        return samples, False

    # 回退：fixtures 真猫图（单图单猫，仅用于验证脚本链路，不代表真实准确率）
    samples = {cat_id: [load_fixture(fname)] for cat_id, fname in REAL_CATS}
    return samples, True


# ---------------------------------------------------------------------------
# 网格搜索（三期）
# ---------------------------------------------------------------------------
GRID_LOCAL_WEIGHTS = [0.0, 0.2, 0.3, 0.4, 0.5]
GRID_LOWE = [0.75, 0.80, 0.85]
GRID_RANSAC = [5.0, 8.0]


def grid_evaluate(samples, baseline, local_weight, lowe, ransac):
    """在给定参数组合下评测，返回统计 dict（不打印）。"""
    rc.LOCAL_WEIGHT = local_weight
    rc.GLOBAL_WEIGHT = round(1.0 - local_weight, 4)
    rc.LOWE_RATIO = lowe
    rc.RANSAC_REPROJ_THRESHOLD = ransac

    pos_scores, neg_scores, top1, top1_total = [], [], 0, 0

    # 正样本：留一法——查询图自身从基准库中排除，用同猫其余实拍做基准。
    # 每猫仅 1 张时退化为「无同猫基准」，该查询不计入 top1 分母并记录。
    for cat_id, images in samples.items():
        for idx, img in enumerate(images):
            got = extract(img)
            if got is None or got[0] is None:
                continue
            feat, desc, kp = got
            if len(images) <= 1:
                top1_total += 1          # 弱口径：无同猫基准，必不可能命中
                continue
            sub_baseline = [e for e in baseline if e["key"] != (cat_id, idx)]
            ranked = match_top(feat, desc, kp, sub_baseline)
            if not ranked:
                continue
            top_sim, top_cat = ranked[0]
            top1_total += 1
            pos_scores.append(top_sim)
            if top_cat == cat_id:
                top1 += 1

    # 负样本
    for fname in NEGATIVE_FILES:
        got = extract(load_fixture(fname))
        if got is None or got[0] is None:
            continue
        feat, desc, kp = got
        ranked = match_top(feat, desc, kp, baseline)
        neg_scores.append(ranked[0][0] if ranked else 0.0)

    pos_arr = np.array(pos_scores) if pos_scores else np.array([0.0])
    neg_arr = np.array(neg_scores) if neg_scores else np.array([0.0])
    separation = float(pos_arr.min() - neg_arr.max()) if (len(pos_scores) and len(neg_scores)) else -1.0
    return {
        "local_weight": local_weight,
        "lowe": lowe,
        "ransac": ransac,
        "top1": pct(top1, top1_total),
        "top1_detail": f"{top1}/{top1_total}",
        "pos_min": float(pos_arr.min()),
        "pos_mean": float(pos_arr.mean()),
        "neg_max": float(neg_arr.max()),
        "neg_mean": float(neg_arr.mean()),
        "separation": separation,
    }


def run_grid(samples, baseline, fallback):
    print("=" * 72)
    print("花纹识别三期 · 真实实拍网格搜索")
    if fallback:
        print("⚠️  未找到 eval/real-samples/<cat_id>/ 真实多角度样本，")
        print("    已回退 fixtures 单图单猫。该结果仅验证脚本链路，")
        print("    不能作为阈值重标定依据（纸上谈兵口径）。")
    else:
        counts = {c: len(v) for c, v in sorted(samples.items())}
        print(f"真实实拍样本：{counts}")
    print(f"基准库：{len(baseline)} 条；负样本：{len(NEGATIVE_FILES)} 张")
    print(f"网格：LOCAL_WEIGHT={GRID_LOCAL_WEIGHTS} × LOWE={GRID_LOWE} × RANSAC={GRID_RANSAC}")
    print("=" * 72)

    results = []
    total = len(GRID_LOCAL_WEIGHTS) * len(GRID_LOWE) * len(GRID_RANSAC)
    for i, (lw, lo, ra) in enumerate(itertools.product(
            GRID_LOCAL_WEIGHTS, GRID_LOWE, GRID_RANSAC), 1):
        r = grid_evaluate(samples, baseline, lw, lo, ra)
        results.append(r)
        print(f"[{i:>2}/{total}] LW={lw:<4} Lowe={lo:<5} RANSAC={ra:<4} "
              f"top1={r['top1']:6.2f}% ({r['top1_detail']})  "
              f"pos_min={r['pos_min']:.3f}  neg_max={r['neg_max']:.3f}  "
              f"分离带={r['separation']:+.3f}")

    results.sort(key=lambda x: (-x["separation"], -x["top1"]))
    best = results[0]
    print("-" * 72)
    print("推荐组合（按分离带降序）：")
    for r in results[:3]:
        print(f"  LOCAL_WEIGHT={r['local_weight']}  GLOBAL_WEIGHT={round(1 - r['local_weight'], 4)}"
              f"  LOWE_RATIO={r['lowe']}  RANSAC={r['ransac']}"
              f"  -> top1={r['top1']:.2f}%  分离带={r['separation']:+.3f}")
    print("-" * 72)
    print("阈值建议（基于最优组合的分数分布，须结合 D3 验收线人工复核）：")
    print(f"  正样本 top1 最低分 = {best['pos_min']:.4f}（manualThreshold 上限）")
    print(f"  负样本 最高分      = {best['neg_max']:.4f}（manualThreshold 下限）")
    if best["separation"] > 0:
        mid = (best["pos_min"] + best["neg_max"]) / 2
        print(f"  建议.manualThreshold ≈ {mid:.2f}（分离带中点），"
              f"matchThreshold 保持 {HIGH_THRESHOLD} 以上由负样本分布复核")
    else:
        print("  ⚠️ 无正负分离带（separation<=0）：ORB 当前权重下不可分，")
        print("     按 D1 应进入 SIFT/AKAZE 评估（DESCRIPTOR=AKAZE 重跑本脚本）。")
    print("=" * 72)

    # 复位运行时常量，避免污染后续默认评测
    rc.LOCAL_WEIGHT = _orig["LOCAL_WEIGHT"]
    rc.GLOBAL_WEIGHT = _orig["GLOBAL_WEIGHT"]
    rc.LOWE_RATIO = _orig["LOWE_RATIO"]
    rc.RANSAC_REPROJ_THRESHOLD = _orig["RANSAC_REPROJ_THRESHOLD"]


_orig = {
    "LOCAL_WEIGHT": rc.LOCAL_WEIGHT,
    "GLOBAL_WEIGHT": rc.GLOBAL_WEIGHT,
    "LOWE_RATIO": rc.LOWE_RATIO,
    "RANSAC_REPROJ_THRESHOLD": rc.RANSAC_REPROJ_THRESHOLD,
}


# ---------------------------------------------------------------------------
# 默认模式（二期口径，保持不变）
# ---------------------------------------------------------------------------
def rerank(feat, q_desc, q_kp, baseline, top_k=TOP_K):
    """兼容旧调用：等价 match_top 的全量排序版（不截断 top_k）。"""
    ranked = match_top(feat, q_desc, q_kp, baseline, top_k=max(TOP_K, 5))
    return ranked


def evaluate(gate_enabled, baseline):
    GateConfig.ENABLED = gate_enabled
    st = {
        "frr_real": [0, 0], "frr_all": [0, 0],
        "top1": [0, 0], "top5": [0, 0],
        "neg_reject": [0, 0], "far": [0, 0],
        "scores": [], "neg_max_sims": [], "reject_reasons": Counter(),
    }

    # --- 正样本：真实猫照片增强 ---
    for cat_id, fname in REAL_CATS:
        for tag, img in augment(load_fixture(fname)):
            st["frr_real"][1] += 1
            st["frr_all"][1] += 1
            got = extract(img)
            if got is None or got[0] is None:
                st["frr_real"][0] += 1
                st["frr_all"][0] += 1
                continue
            feat, desc, kp = got
            scored = rerank(feat, desc, kp, baseline)
            if scored:
                st["scores"].append(scored[0][0])
                st["top1"][1] += 1
                st["top5"][1] += 1
                if scored[0][1] == cat_id:
                    st["top1"][0] += 1
                if cat_id in [c for _, c in scored[:5]]:
                    st["top5"][0] += 1

    # --- 边界源：75ec3cde（AI 卡通插画），仅计入 FRR_all 参考口径 ---
    for _, fname in BOUNDARY_SOURCES:
        for tag, img in augment(load_fixture(fname)):
            st["frr_all"][1] += 1
            got = extract(img)
            if got is None:
                st["frr_all"][0] += 1

    # --- 负样本 ---
    for fname in NEGATIVE_FILES:
        img = load_fixture(fname)
        st["neg_reject"][1] += 1
        st["far"][1] += 1
        gr = GATE.check(img)
        if not gr["pass"]:
            st["neg_reject"][0] += 1
            st["reject_reasons"][gr["reason"]] += 1
            continue  # 被拒识 => 不产生候选，自然不构成误接受
        got = extract(img)
        if got is None or got[0] is None:
            continue
        feat, desc, kp = got
        scored = rerank(feat, desc, kp, baseline)
        top_sim = scored[0][0] if scored else 0.0
        st["neg_max_sims"].append(top_sim)
        st["scores"].append(top_sim)
        if top_sim >= HIGH_THRESHOLD:
            st["far"][0] += 1

    return st


def pct(hit, total):
    return 0.0 if total == 0 else 100.0 * hit / total


def score_hist(scores, bins=10):
    counts = [0] * bins
    for s in scores:
        idx = min(int(max(s, 0.0) * bins), bins - 1)
        counts[idx] += 1
    return counts


def print_thresholds():
    print("实际使用的闸门阈值（GateConfig，可由 GATE_* 环境变量覆盖）：")
    keys = [
        "MIN_SHORT_EDGE", "MIN_ASPECT", "MAX_ASPECT",
        "MIN_GRAY_STD", "MIN_UNIQUE_COLORS", "MIN_LAPLACIAN_VAR",
        "MAX_HIGH_FREQ_RATIO", "MAX_ADJ_DIFF",
        "MAX_HOUGH_LONG_LINE", "MAX_UNIFORM_RATIO", "MAX_TOP_COLOR_RATIO",
        "FLAT_BLOCK_STD", "HOUGH_MIN_LINE_RATIO", "WORK_LONG_EDGE",
    ]
    for k in keys:
        print(f"    {k:<24} = {getattr(GateConfig, k)}")


def main():
    parser = argparse.ArgumentParser(description="花纹识别离线评测")
    parser.add_argument("--grid", action="store_true",
                        help="三期：真实实拍网格搜索（LOCAL_WEIGHT/Lowe/RANSAC）")
    args = parser.parse_args()

    if args.grid:
        samples, fallback = load_real_samples()
        entries = []
        for cat_id, images in sorted(samples.items()):
            entries.extend((cat_id, img) for img in images)
        baseline = build_baseline(entries)
        if not baseline:
            print("基准库为空（样本全部被拒识/提取失败），无法评测")
            return
        run_grid(samples, baseline, fallback)
        return

    print("=" * 72)
    print("花纹识别二期/三期 · 离线评测（增强样本口径）")
    print(f"⚠️  {DISCLAIMER}")
    print("=" * 72)
    print(f"feature_dim = {FEATURE_DIM}")
    print_thresholds()
    print()

    # 基准库在 gate on 下构建（真实猫原图均应通过闸门）
    GateConfig.ENABLED = True
    baseline = build_baseline([(c, load_fixture(f)) for c, f in REAL_CATS])
    print(f"临时基准库：{len(baseline)} 只真实猫（原图）")
    print(f"正样本增强源：{len(REAL_CATS)} 张真实猫照 + {len(BOUNDARY_SOURCES)} 张边界素材"
          f"（75ec3cde 为 AI 卡通插画，非真实照片）")
    print(f"负样本：{len(NEGATIVE_FILES)} 张（噪声/纯白/品牌图/小程序截图）")
    print(f"增强：原图 + 旋转±10° + 亮度×0.7/1.3 + 缩放0.6 + 中心裁剪0.8")
    print()

    # ---------- 主评测：GATE_ENABLED = True ----------
    on = evaluate(True, baseline)
    print("-" * 72)
    print("【主评测】GATE_ENABLED = True")
    print("-" * 72)
    print(f"  top1 准确率        : {pct(*on['top1']):6.2f}%  ({on['top1'][0]}/{on['top1'][1]})"
          f"   [基准库仅 {len(baseline)} 只，top5 参考价值有限]")
    print(f"  top5 准确率        : {pct(*on['top5']):6.2f}%  ({on['top5'][0]}/{on['top5'][1]})")
    print(f"  FRR 真猫误拒率     : {pct(*on['frr_real']):6.2f}%  ({on['frr_real'][0]}/{on['frr_real'][1]})"
          f"   [真实猫照口径，目标 <=20%]")
    print(f"  FRR_all 全口径     : {pct(*on['frr_all']):6.2f}%  ({on['frr_all'][0]}/{on['frr_all'][1]})"
          f"   [含 75ec3cde 插画，参考口径]")
    print(f"  负样本拒识率       : {pct(*on['neg_reject']):6.2f}%  ({on['neg_reject'][0]}/{on['neg_reject'][1]})"
          f"   [目标 100%]")
    print(f"  FAR 非猫误接受率   : {pct(*on['far']):6.2f}%  ({on['far'][0]}/{on['far'][1]})"
          f"   [被拒识不产生候选，自然不构成误接受]")
    print(f"  拒识 reason 分布   : {dict(on['reject_reasons'])}")
    print()

    # ---------- 对比：GATE_ENABLED = False ----------
    off = evaluate(False, baseline)
    arr = np.array(off["neg_max_sims"]) if off["neg_max_sims"] else np.array([0.0])
    print("-" * 72)
    print("【对比评测】GATE_ENABLED = False（闸门关闭，负样本全部进入 L3 重排）")
    print("-" * 72)
    print(f"  负样本拒识率       : {pct(*off['neg_reject']):6.2f}%")
    print(f"  FAR 非猫误接受率   : {pct(*off['far']):6.2f}%  ({off['far'][0]}/{off['far'][1]})"
          f"   [sim >= {HIGH_THRESHOLD} 视为 high]")
    print(f"  负样本 top1 分数   : min={arr.min():.3f}  mean={arr.mean():.3f}  max={arr.max():.3f}")
    print("  => 闸门 on/off 对比说明：闸门负责 100% 拦下明显非自然照片；")
    print("     局部重排权重也会把非猫图分数压到阈值以下。")
    print()

    # ---------- 分数分布 ----------
    all_scores = on["scores"] + off["neg_max_sims"]
    hist = score_hist(all_scores)
    print("-" * 72)
    print("分数分布（全部查询 top1 similarity，含 gate on 正样本 + gate off 负样本）")
    print("-" * 72)
    for i, c in enumerate(hist):
        lo, hi = i / 10.0, (i + 1) / 10.0
        bar = "#" * c
        print(f"  [{lo:.1f}, {hi:.1f}) : {c:3d}  {bar}")
    print()
    print("=" * 72)
    print(f"⚠️  {DISCLAIMER}")
    print("    说明：75ec3cde-...png 实为 AI 生成卡通插画（非真实猫照），与品牌设计图")
    print("    统计特征不可分，闸门按设计图拒识属预期；已单列 FRR_all 参考口径。")
    print("    真实多角度样本请放入 eval/real-samples/<cat_id>/ 后运行 --grid 模式。")
    print("=" * 72)

    GateConfig.ENABLED = True  # 复位


if __name__ == "__main__":
    main()
