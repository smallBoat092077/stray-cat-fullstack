"""
流浪猫花纹识别核心算法（花纹识别三期治本）

L2 特征重构 + L3 局部特征重排的共用逻辑：
- 删除一期 `_extract_orb()` 的 mean/std 聚合（64 维恒在 ~127.5 的常数维是
  「任何有角点的图余弦相似度都 0.92+」的直接根因）。
- 改为「分段归一化 + 2x2 空间分块」：每个特征族各自 L2 归一化 × 权重后
  concat，再整体归一化，避免某一族量级压过其他族，并保留空间布局信息。
- 闸门失败时 extract_feature() 返回 None（不再返回零向量）。
- extract_descriptors() 返回 ORB 原始描述子（uint8 N×32），与 extract_feature()
  共用同一次 _preprocess()，避免重复计算。
- local_match_score() / blend_similarity() 为 main.py 与 eval 共用的 L3 重排口径，
  保证线上匹配与离线评测完全一致。

三期改动（真实实拍治本）：
- L3 常量支持环境变量覆盖（LOWE_RATIO / RANSAC_REPROJ_THRESHOLD /
  GLOBAL_WEIGHT / LOCAL_WEIGHT），网格搜索与部署调参无需改代码；
  默认值沿用二期实测基线。
- 若 ORB 局部重排在真实实拍上仍无分离度，允许切换 SIFT/AKAZE
  （DESCRIPTOR 环境变量，纯 OpenCV、无新依赖）；默认仍为 ORB。

特征维度（三处对齐：recognizer.FEATURE_DIM / database / stats）：
    color_hist_spatial : 2x2 分块 x (8x8 HSV) = 4 x 64 = 256   w=1.0
    texture_spatial    : 2x2 分块 x (Sobel/HOG 16) = 4 x 16 = 64   w=0.8
    global_color       : 全图 8x8 HSV = 64                        w=0.5
    -----------------------------------------------------------------------
    FEATURE_DIM        : 256 + 64 + 64 = 384
"""
import os
import logging
from typing import Optional, Tuple

import cv2
import numpy as np

from cat_gate import CatGate

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# 统一常量（二期新增；feature_dim 不再散落魔法数字）
# ---------------------------------------------------------------------------
FEATURE_DIM = 384

# 分段归一化权重
W_COLOR_SPATIAL = 1.0
W_TEXTURE_SPATIAL = 0.8
W_GLOBAL_COLOR = 0.5


def _env_float(key: str, default: float) -> float:
    try:
        v = os.getenv(key)
        return default if v is None or not v.strip() else float(v)
    except (TypeError, ValueError):
        return default


def _env_str(key: str, default: str) -> str:
    v = os.getenv(key)
    return default if v is None or not v.strip() else v.strip().upper()


# ---------------------------------------------------------------------------
# L3 局部特征重排常量（main.py 与 eval/run_eval.py 共用；三期支持环境变量覆盖）
# ---------------------------------------------------------------------------
LOWE_RATIO = _env_float("LOWE_RATIO", 0.75)
RANSAC_REPROJ_THRESHOLD = _env_float("RANSAC_REPROJ_THRESHOLD", 5.0)
GLOBAL_WEIGHT = _env_float("GLOBAL_WEIGHT", 0.3)
LOCAL_WEIGHT = _env_float("LOCAL_WEIGHT", 0.7)
# 三期新增：局部特征算子（ORB / SIFT / AKAZE）；ORB 为默认，SIFT/AKAZE 兜底
DESCRIPTOR = _env_str("DESCRIPTOR", "ORB")


def _l2(vec: np.ndarray) -> np.ndarray:
    """L2 归一化（对全零向量安全）。"""
    v = np.asarray(vec, dtype=np.float32).ravel()
    return v / (np.linalg.norm(v) + 1e-8)


def local_match_score(q_desc, q_kp, b_desc, b_kp) -> dict:
    """L3 局部特征重排：BFMatcher + Lowe ratio + RANSAC 内点。

    q_* 为查询图，b_* 为候选基准（描述子 N×W、关键点坐标 N×2 float32）。
    三期：按描述子 dtype 自动选匹配度量 —— 二进制描述子（ORB/AKAZE，uint8）
    用 NORM_HAMMING；浮点描述子（SIFT，float32）用 NORM_L2。Lowe ratio 与
    RANSAC 逻辑不变。
    说明：findHomography 需要匹配点的空间坐标，故基准除描述子外还需随库保存
    关键点坐标（见 database._pack/_unpack）；坐标缺失时退化为仅好匹配占比。

    Returns:
        {"local_score": float, "good_matches": int, "inliers": int}
    """
    empty = {"local_score": 0.0, "good_matches": 0, "inliers": 0}
    if q_desc is None or b_desc is None:
        return empty
    q_desc = np.asarray(q_desc)
    b_desc = np.asarray(b_desc)
    # 二进制描述子（uint8）用 HAMMING；浮点描述子（SIFT float32）用 L2
    norm = cv2.NORM_HAMMING if q_desc.dtype == np.uint8 else cv2.NORM_L2
    if len(q_desc) < 2 or len(b_desc) < 2:
        return empty

    try:
        matcher = cv2.BFMatcher(norm)
        knn = matcher.knnMatch(q_desc, b_desc, k=2)
        good = []
        for pair in knn:
            if len(pair) < 2:
                continue
            m, n = pair[0], pair[1]
            if m.distance < LOWE_RATIO * n.distance:
                good.append(m)
    except cv2.error as e:
        logger.debug(f"local_match_score: BFMatcher 失败：{e}")
        return empty

    denom = max(min(len(q_desc), len(b_desc)), 1)
    good_ratio = min(len(good) / denom, 1.0)

    inliers = 0
    if len(good) >= 4 and q_kp is not None and b_kp is not None:
        try:
            q_kp = np.asarray(q_kp, dtype=np.float32)
            b_kp = np.asarray(b_kp, dtype=np.float32)
            src = np.float32([q_kp[m.queryIdx] for m in good]).reshape(-1, 1, 2)
            dst = np.float32([b_kp[m.trainIdx] for m in good]).reshape(-1, 1, 2)
            _, mask = cv2.findHomography(src, dst, cv2.RANSAC, RANSAC_REPROJ_THRESHOLD)
            if mask is not None:
                inliers = int(mask.sum())
        except cv2.error as e:
            logger.debug(f"local_match_score: findHomography 失败：{e}")

    inlier_ratio = min(inliers / denom, 1.0)
    local_score = float(np.clip(0.5 * good_ratio + 0.5 * inlier_ratio, 0.0, 1.0))
    return {"local_score": local_score, "good_matches": len(good), "inliers": inliers}


def blend_similarity(global_cos: float, local_score: float) -> float:
    """最终分：similarity = GLOBAL_WEIGHT * global_cos + LOCAL_WEIGHT * local_score
    （默认 0.3 / 0.7，支持环境变量覆盖，三期用评测集重标定）。"""
    g = float(np.clip(global_cos, 0.0, 1.0))
    l = float(np.clip(local_score, 0.0, 1.0))
    return float(np.clip(GLOBAL_WEIGHT * g + LOCAL_WEIGHT * l, 0.0, 1.0))


class CatRecognizer:
    """猫花纹识别器"""

    def __init__(self):
        # 局部特征检测器（三期：DESCRIPTOR 环境变量可切 ORB/AKAZE/SIFT，纯 OpenCV）
        # ORB/AKAZE 输出二进制描述子（HAMMING）；SIFT 输出 float32（L2）。
        if DESCRIPTOR == "SIFT":
            self.local_detector = cv2.SIFT_create(nfeatures=500)
        elif DESCRIPTOR == "AKAZE":
            self.local_detector = cv2.AKAZE_create()
        else:
            self.local_detector = cv2.ORB_create(nfeatures=500)
        # L1 拒识闸门（软/硬闸门；GATE_ENABLED 关闭时整体放行）
        self.gate = CatGate()
        # 统一特征维度：384（修正一期注释维度与实现不一致的问题）
        self.feature_dim = FEATURE_DIM
        logger.info(f"CatRecognizer 初始化完成，feature_dim={self.feature_dim}, "
                    f"descriptor={DESCRIPTOR}")

    # ------------------------------------------------------------------
    # 对外接口
    # ------------------------------------------------------------------
    def extract_all(self, image: np.ndarray, gate_result: Optional[dict] = None
                    ) -> Tuple[Optional[np.ndarray], Optional[np.ndarray], Optional[np.ndarray]]:
        """一次预处理同时产出 (feature, descriptors, keypoints)。

        与 extract_feature() / extract_descriptors() 共用同一次 _preprocess()，
        供 /match 与 /register 一次拿到全局特征 + 局部描述子，避免重复计算。

        Args:
            image: BGR 图像 (H, W, 3)
            gate_result: 已完成的闸门结果（main.py 先过闸门再传入，避免闸门重复计算）；
                         为 None 时本方法自行调用闸门。闸门未通过则三项均为 None。

        Returns:
            feature:     float32 (FEATURE_DIM,) 或 None
            descriptors: uint8  (N, 32) 或 None
            keypoints:   float32 (N, 2) ORB 关键点坐标，或 None
        """
        if gate_result is None:
            gate_result = self.gate.check(image)
        if not gate_result.get("pass"):
            return None, None, None

        prep = self._preprocess(image)
        if prep is None:
            return None, None, None
        enhanced, gray = prep

        try:
            feature = self._build_feature(enhanced)
            keypoints, descriptors = self.local_detector.detectAndCompute(gray, None)
            if descriptors is None or len(descriptors) == 0:
                # 无特征点：全局特征仍可用，但局部重排无法进行
                return feature, None, None
            kp_xy = np.array([kp.pt for kp in keypoints], dtype=np.float32)
            # 保留原始 dtype：ORB/AKAZE uint8（HAMMING）、SIFT float32（L2）
            return feature, np.ascontiguousarray(descriptors), kp_xy
        except Exception as e:
            logger.error(f"特征提取失败：{e}", exc_info=True)
            return None, None, None

    def extract_feature(self, image: np.ndarray,
                        gate_result: Optional[dict] = None) -> Optional[np.ndarray]:
        """提取全局特征向量；闸门失败或无特征时返回 None（不再返回零向量）。"""
        return self.extract_all(image, gate_result)[0]

    def extract_descriptors(self, image: np.ndarray,
                            gate_result: Optional[dict] = None) -> Optional[np.ndarray]:
        """返回 ORB 原始描述子（uint8 N×32），供 L3 局部特征重排。"""
        return self.extract_all(image, gate_result)[1]

    def extract_keypoints(self, image: np.ndarray,
                          gate_result: Optional[dict] = None) -> Optional[np.ndarray]:
        """返回 ORB 关键点坐标（float32 N×2），供 findHomography 几何一致性校验。"""
        return self.extract_all(image, gate_result)[2]

    def compute_similarity(self, feature1: np.ndarray, feature2: np.ndarray) -> float:
        """计算两个特征向量的余弦相似度（兼容一期签名）。"""
        dot_product = np.dot(feature1, feature2)
        norm1 = np.linalg.norm(feature1)
        norm2 = np.linalg.norm(feature2)
        similarity = dot_product / (norm1 * norm2 + 1e-8)
        return float(np.clip(similarity, 0, 1))

    # ------------------------------------------------------------------
    # 预处理（一次，供特征族与 ORB 共用）
    # ------------------------------------------------------------------
    def _preprocess(self, image: np.ndarray):
        """统一尺寸 + CLAHE 对比度增强；返回 (增强BGR, 原始灰度)。"""
        try:
            resized = cv2.resize(image, (224, 224))
            lab = cv2.cvtColor(resized, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
            enhanced = cv2.cvtColor(cv2.merge([clahe.apply(l), a, b]), cv2.COLOR_LAB2BGR)
            # ORB 在未增强的原始灰度图上计算，避免 CLAHE 改变局部纹理稳定性
            gray = cv2.cvtColor(resized, cv2.COLOR_BGR2GRAY)
            return enhanced, gray
        except Exception as e:
            logger.error(f"预处理失败：{e}", exc_info=True)
            return None

    # ------------------------------------------------------------------
    # L2 特征族：分段归一化 + 2x2 空间分块
    # ------------------------------------------------------------------
    def _build_feature(self, enhanced: np.ndarray) -> np.ndarray:
        color_spatial = self._color_hist_spatial(enhanced)     # 256
        texture_spatial = self._texture_spatial(enhanced)       # 64
        global_color = self._global_color_hist(enhanced)        # 64

        # 每个特征族各自 L2 归一化 × 权重后 concat，再整体归一化
        combined = np.concatenate([
            _l2(color_spatial) * W_COLOR_SPATIAL,
            _l2(texture_spatial) * W_TEXTURE_SPATIAL,
            _l2(global_color) * W_GLOBAL_COLOR,
        ]).astype(np.float32)
        return _l2(combined).astype(np.float32)

    @staticmethod
    def _block_slices(h: int, w: int):
        """2x2 空间分块，返回 (行切片, 列切片) 列表。"""
        return [
            (slice(i * h // 2, (i + 1) * h // 2), slice(j * w // 2, (j + 1) * w // 2))
            for i in range(2) for j in range(2)
        ]

    def _color_hist_spatial(self, image: np.ndarray) -> np.ndarray:
        """2x2 分块的 8x8 HSV 直方图：4 x 64 = 256 维。"""
        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        h, w = hsv.shape[:2]
        parts = []
        for sy, sx in self._block_slices(h, w):
            block = hsv[sy, sx]
            hist = cv2.calcHist([block], [0, 1], None, [8, 8], [0, 180, 0, 256]).flatten()
            parts.append(_l2(hist))     # 每块各自归一化，保证各块等权
        return np.concatenate(parts).astype(np.float32)

    def _texture_spatial(self, image: np.ndarray) -> np.ndarray:
        """2x2 分块的 Sobel/HOG + 统计纹理：4 x 16 = 64 维。"""
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        gf = gray.astype(np.float32)
        gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
        gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
        mag = np.sqrt(gx ** 2 + gy ** 2)
        ang = np.arctan2(gy, gx)
        bins = np.clip(np.floor((ang + np.pi) / (2 * np.pi) * 8).astype(int), 0, 7)
        h, w = gray.shape[:2]

        parts = []
        for sy, sx in self._block_slices(h, w):
            m, b = mag[sy, sx], bins[sy, sx]
            g, gx_b, gy_b = gf[sy, sx], gx[sy, sx], gy[sy, sx]
            hog = np.zeros(8, dtype=np.float32)
            for k in range(8):
                mask = (b == k)
                if np.any(mask):
                    hog[k] = float(np.mean(m[mask]))
            stats = np.array([
                np.mean(m), np.std(m),
                np.mean(g), np.std(g),
                np.mean(gx_b), np.std(gx_b),
                np.mean(gy_b), np.std(gy_b),
            ], dtype=np.float32)
            parts.append(_l2(np.concatenate([hog, stats])))   # 16 维/块
        return np.concatenate(parts).astype(np.float32)

    def _global_color_hist(self, image: np.ndarray) -> np.ndarray:
        """全图 8x8 HSV 直方图：64 维。"""
        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        hist = cv2.calcHist([hsv], [0, 1], None, [8, 8], [0, 180, 0, 256]).flatten()
        return _l2(hist).astype(np.float32)
