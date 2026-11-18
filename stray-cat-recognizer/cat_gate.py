"""
L1 拒识闸门（花纹识别二期治本）
纯 cv2 / numpy，零新增依赖，单图目标 < 150ms。

设计原则（对齐方案 §3.3）：
- 硬闸门只挡「明显不是自然照片」的图：解码/几何、纯色低信息、模糊、
  自然图像统计（挡随机噪声）、UI 截图判别（挡小程序截图 / logo / 文档）。
- 猫脸级联分类器仅作「软闸门」：只写 has_cat_face 标记，绝不因检测不到
  猫脸而拒识（侧身 / 全身猫召回低）。
- 所有阈值集中在 GateConfig，支持环境变量覆盖；GATE_ENABLED 可整体开关，
  便于 gate on/off 对比评测。
- check() 返回结构化 dict，且把全部原始指标写入 metrics，便于评测标定与排障。
"""
import os
import logging
import cv2
import numpy as np

logger = logging.getLogger(__name__)


def _env_bool(name: str, default: bool) -> bool:
    v = os.getenv(name)
    if v is None:
        return default
    return v.strip().lower() in ("1", "true", "yes", "on")


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


class GateConfig:
    """闸门阈值集中配置；全部可用环境变量覆盖（类名前缀 GATE_）。

    默认值经 eval/fixtures 评测集标定：负样本（噪声/纯白/品牌图/小程序截图）
    硬闸门 100% 拒识，真实猫照片全部放行。
    """

    # 总开关：置 false 完全放行，用于 gate on/off 对比评测
    ENABLED = _env_bool("GATE_ENABLED", True)

    # --- 硬闸门 1：解码与几何 ---
    MIN_SHORT_EDGE = _env_int("GATE_MIN_SHORT_EDGE", 128)
    MIN_ASPECT = _env_float("GATE_MIN_ASPECT", 0.3)
    MAX_ASPECT = _env_float("GATE_MAX_ASPECT", 3.0)

    # --- 硬闸门 2：纯色 / 低信息 ---
    MIN_GRAY_STD = _env_float("GATE_MIN_GRAY_STD", 10.0)
    MIN_UNIQUE_COLORS = _env_int("GATE_MIN_UNIQUE_COLORS", 12)

    # --- 硬闸门 3：模糊（Laplacian 方差） ---
    MIN_LAPLACIAN_VAR = _env_float("GATE_MIN_LAPLACIAN_VAR", 30.0)

    # --- 硬闸门 4：自然图像统计（挡随机噪声） ---
    # 实测：真猫 hfr<=0.031 / adjdiff<=10.3，噪声图 hfr=0.651 / adjdiff=30.4；双指标均触发
    MAX_HIGH_FREQ_RATIO = _env_float("GATE_MAX_HIGH_FREQ_RATIO", 0.50)
    MAX_ADJ_DIFF = _env_float("GATE_MAX_ADJ_DIFF", 28.0)

    # --- 硬闸门 5：UI / 截图判别（挡小程序截图 / logo / 文档） ---
    MAX_HOUGH_LONG_LINE = _env_int("GATE_MAX_HOUGH_LONG_LINE", 6)
    MAX_UNIFORM_RATIO = _env_float("GATE_MAX_UNIFORM_RATIO", 0.62)
    MAX_TOP_COLOR_RATIO = _env_float("GATE_MAX_TOP_COLOR_RATIO", 0.45)
    FLAT_BLOCK_STD = _env_float("GATE_FLAT_BLOCK_STD", 12.0)   # 块内灰度标准差低于此视为平坦块
    HOUGH_MIN_LINE_RATIO = _env_float("GATE_HOUGH_MIN_LINE_RATIO", 0.35)  # 长直线 = 短边 * 该比例

    # --- 计算用工作图尺寸（统一缩放，保证 < 150ms） ---
    WORK_LONG_EDGE = _env_int("GATE_WORK_LONG_EDGE", 256)

    # --- 软闸门：Haar 猫脸 ---
    CATFACE_SCALE = _env_float("GATE_CATFACE_SCALE", 1.1)
    CATFACE_MIN_NEIGHBORS = _env_int("GATE_CATFACE_MIN_NEIGHBORS", 2)


# 拒识 reason -> 友好文案（不暴露内部判别细节，统一引导重新拍摄）
NO_CAT_MESSAGE = "照片中未检测到猫咪，请拍摄猫咪正面或侧面全身照"
REJECT_MESSAGES = {
    "decode_error": "图片无法读取，请重新上传清晰的猫咪照片",
    "geometry": "照片尺寸异常，请拍摄猫咪正面或侧面全身照",
    "low_information": NO_CAT_MESSAGE,
    "blur": "照片有些模糊，请对准猫咪重新拍摄",
    "noise": NO_CAT_MESSAGE,
    "ui_screenshot": NO_CAT_MESSAGE,
}


class CatGate:
    """L1 拒识闸门。"""

    def __init__(self, config=GateConfig):
        self.config = config
        self._face_cascade = None
        self._face_cascade_ready = False

    # ------------------------------------------------------------------
    # 对外主入口
    # ------------------------------------------------------------------
    def check(self, image_bgr) -> dict:
        """对 BGR 图像执行拒识判定。

        Returns:
            {
              "pass": bool,            # 是否通过闸门（可进入后续特征提取/匹配）
              "reason": str | None,    # 硬闸门失败原因码；通过为 None
              "message": str,          # 面向用户的友好文案
              "has_cat_face": bool,    # 软闸门：是否检测到猫脸（绝不据此拒识）
              "metrics": dict          # 全部原始指标（可 JSON 序列化）
            }
        """
        cfg = self.config
        metrics = {"enabled": bool(cfg.ENABLED)}

        # 总开关：关闭时完全放行，便于 gate on/off 对比评测
        if not cfg.ENABLED:
            return {
                "pass": True,
                "reason": None,
                "message": "闸门已禁用（GATE_ENABLED=false）",
                "has_cat_face": False,
                "metrics": metrics,
            }

        # --- 硬闸门 1：解码与几何 ---
        if image_bgr is None or not isinstance(image_bgr, np.ndarray):
            return self._reject("decode_error", metrics)
        if image_bgr.ndim != 3 or image_bgr.shape[2] != 3:
            return self._reject("decode_error", metrics)

        h, w = image_bgr.shape[:2]
        short_edge = min(h, w)
        aspect = w / (h + 1e-8)
        metrics.update({
            "height": int(h), "width": int(w),
            "short_edge": int(short_edge), "aspect": round(float(aspect), 4),
        })
        if short_edge < cfg.MIN_SHORT_EDGE or not (cfg.MIN_ASPECT <= aspect <= cfg.MAX_ASPECT):
            return self._reject("geometry", metrics)

        # --- 计算工作图与全部指标 ---
        try:
            work_gray = self._compute_metrics(image_bgr, metrics)
        except Exception as e:  # 指标计算异常时保守拒识，绝不外抛导致 500
            logger.warning(f"CatGate: 指标计算异常，保守拒识：{e}")
            return self._reject("decode_error", metrics)

        # --- 硬闸门 2：纯色 / 低信息 ---
        if (metrics["gray_std"] < cfg.MIN_GRAY_STD
                or metrics["unique_colors"] < cfg.MIN_UNIQUE_COLORS):
            return self._reject("low_information", metrics)

        # --- 硬闸门 3：模糊 ---
        if metrics["laplacian_var"] < cfg.MIN_LAPLACIAN_VAR:
            return self._reject("blur", metrics)

        # --- 硬闸门 4：自然图像统计（挡随机噪声） ---
        if (metrics["high_freq_ratio"] > cfg.MAX_HIGH_FREQ_RATIO
                or metrics["adj_diff"] > cfg.MAX_ADJ_DIFF):
            return self._reject("noise", metrics)

        # --- 硬闸门 5：UI / 截图判别 ---
        if self._is_ui_screenshot(metrics, cfg):
            return self._reject("ui_screenshot", metrics)

        # --- 软闸门：Haar 猫脸（仅标记，绝不拒识） ---
        has_cat_face = self._detect_cat_face(work_gray, cfg)
        metrics["has_cat_face"] = bool(has_cat_face)

        return {
            "pass": True,
            "reason": None,
            "message": "闸门通过",
            "has_cat_face": bool(has_cat_face),
            "metrics": metrics,
        }

    # ------------------------------------------------------------------
    # 指标计算（全部在统一缩放的工作图上进行，保证耗时可控）
    # ------------------------------------------------------------------
    def _compute_metrics(self, image_bgr, metrics) -> np.ndarray:
        """计算全部硬闸门指标写入 metrics，返回工作灰度图（供软闸门复用）。"""
        cfg = self.config
        h, w = image_bgr.shape[:2]
        scale = cfg.WORK_LONG_EDGE / float(max(h, w))
        if scale < 1.0:
            work = cv2.resize(image_bgr,
                              (max(1, int(round(w * scale))), max(1, int(round(h * scale)))),
                              interpolation=cv2.INTER_AREA)
        else:
            work = image_bgr.copy()

        gray = cv2.cvtColor(work, cv2.COLOR_BGR2GRAY)
        gf = gray.astype(np.float32)
        wh, ww = gray.shape[:2]

        # (2) 纯色/低信息：灰度标准差 + 量化唯一色数
        metrics["gray_std"] = round(float(gf.std()), 3)
        q = (work // 32).astype(np.int32)                 # 每通道量化到 0..7
        codes = q[..., 0] * 64 + q[..., 1] * 8 + q[..., 2]   # 0..511
        metrics["unique_colors"] = int(np.unique(codes).size)
        hist = np.bincount(codes.ravel(), minlength=512).astype(np.float32)
        # 大面积同色块信号之一：最常见量化色占比
        metrics["top_color_ratio"] = round(float(hist.max() / (hist.sum() + 1e-8)), 4)

        # (3) 模糊：Laplacian 方差
        metrics["laplacian_var"] = round(float(cv2.Laplacian(gray, cv2.CV_64F).var()), 3)

        # (4) 自然图像统计：相邻像素差分能量 + 高频能量占比
        dx = float(np.abs(np.diff(gf, axis=1)).mean())
        dy = float(np.abs(np.diff(gf, axis=0)).mean())
        metrics["adj_diff"] = round((dx + dy) / 2.0, 3)
        low = cv2.GaussianBlur(gf, (0, 0), 1.0)
        high = gf - low
        denom = float(np.sum((gf - gf.mean()) ** 2)) + 1e-8
        metrics["high_freq_ratio"] = round(float(np.sum(high ** 2) / denom), 4)

        # (5) UI/截图判别：大面积同色块占比（8x8 平坦块）
        gh = gw = 8
        ch, cw = max(1, wh // gh), max(1, ww // gw)
        flat = total = 0
        for i in range(gh):
            for j in range(gw):
                block = gf[i * ch:(i + 1) * ch, j * cw:(j + 1) * cw]
                if block.size == 0:
                    continue
                total += 1
                if float(block.std()) < cfg.FLAT_BLOCK_STD:
                    flat += 1
        metrics["uniform_ratio"] = round(flat / (total + 1e-8), 4)

        # (5) UI/截图判别：Hough 长直线条数（卡片边框 / 分隔线 / 状态栏）
        edges = cv2.Canny(gray, 60, 160)
        min_len = max(10, int(min(wh, ww) * cfg.HOUGH_MIN_LINE_RATIO))
        lines = cv2.HoughLinesP(edges, 1, np.pi / 180, threshold=20,
                                minLineLength=min_len, maxLineGap=6)
        long_line = 0
        if lines is not None:
            for x1, y1, x2, y2 in lines.reshape(-1, 4):
                if np.hypot(x2 - x1, y2 - y1) >= min_len:
                    long_line += 1
        metrics["hough_long_line"] = int(long_line)

        return gray

    # ------------------------------------------------------------------
    # 组合判别与软闸门
    # ------------------------------------------------------------------
    def _is_ui_screenshot(self, m, cfg) -> bool:
        """UI 截图 / logo / 文档：需多个信号同时成立，避免误拒含门框/窗框的真猫照。"""
        long_line_hit = m["hough_long_line"] >= cfg.MAX_HOUGH_LONG_LINE
        uniform_hit = m["uniform_ratio"] >= cfg.MAX_UNIFORM_RATIO
        top_color_hit = m["top_color_ratio"] >= cfg.MAX_TOP_COLOR_RATIO
        # 长直线 + 色块信号，或 色块两信号同时极强（纯色背景设计图）
        return (long_line_hit and (uniform_hit or top_color_hit)) or (uniform_hit and top_color_hit)

    def _load_face_cascade(self):
        if self._face_cascade_ready:
            return
        self._face_cascade_ready = True
        # 某些精简构建（如 opencv-python-headless 5.x）不含 CascadeClassifier；
        # 软闸门缺失时静默降级，绝不影响硬闸门判定。
        cls = getattr(cv2, "CascadeClassifier", None)
        if cls is None:
            logger.warning("CatGate: 当前 cv2 构建不含 CascadeClassifier，has_cat_face 恒为 False")
            return
        try:
            xml = cv2.data.haarcascades + "haarcascade_frontalcatface_extended.xml"
            cascade = cls(xml)
            if cascade.empty():
                logger.warning("CatGate: 猫脸级联分类器为空，has_cat_face 恒为 False")
            else:
                self._face_cascade = cascade
        except Exception as e:
            logger.warning(f"CatGate: 猫脸级联分类器不可用：{e}")

    def _detect_cat_face(self, work_gray, cfg) -> bool:
        """软闸门：多尺度 Haar 猫脸检测，仅标记，绝不参与拒识。"""
        self._load_face_cascade()
        if self._face_cascade is None:
            return False
        try:
            faces = self._face_cascade.detectMultiScale(
                work_gray,
                scaleFactor=cfg.CATFACE_SCALE,
                minNeighbors=cfg.CATFACE_MIN_NEIGHBORS,
                minSize=(20, 20),
            )
            return len(faces) > 0
        except Exception as e:
            logger.warning(f"CatGate: 猫脸检测异常：{e}")
            return False

    # ------------------------------------------------------------------
    def _reject(self, reason: str, metrics: dict) -> dict:
        return {
            "pass": False,
            "reason": reason,
            "message": REJECT_MESSAGES.get(reason, NO_CAT_MESSAGE),
            "has_cat_face": False,
            "metrics": metrics,
        }
