"""
流浪猫花纹识别服务 - FastAPI（花纹识别二期治本）

接口契约（对 Java 侧保持不变）：
- multipart 字段名 `file`；Form 字段 `top_k`、`cat_id`；
- 路径 /health、/api/recognize/match|register|stats|extract 全部不变；
  三期新增 /api/recognize/delete（Form feature_id，删行 + 重建索引）。
- 任何异常均返回结构化 `success=false` 的 HTTP 200，绝不让 Java 侧拿到 500。

/match 链路（三期口径）：
    L1 CatGate 拒识闸门 -> L2 重构特征(384维) + 局部描述子/关键点
    -> L3 FAISS 粗召回 top_k*12 -> BFMatcher/Lowe/RANSAC 重排
    -> 按 cat_id 分组取猫内最高分 -> 跨猫降序取 top_k 返回。
    Python 不再做阈值过滤（阈值统一由 Java 侧控制）。
    L3 常量（LOWE_RATIO / RANSAC_REPROJ_THRESHOLD / GLOBAL_WEIGHT / LOCAL_WEIGHT /
    DESCRIPTOR）支持环境变量覆盖，与离线评测网格搜索共用同一套代码路径。
"""
import io
import os
import logging
from datetime import datetime
from typing import Optional, List, Dict

import cv2
import numpy as np
from PIL import Image
from fastapi import FastAPI, UploadFile, File, Form, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from pydantic import BaseModel

from recognizer import CatRecognizer, local_match_score, blend_similarity, DESCRIPTOR
from database import Database

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="流浪猫花纹识别服务", version="2.0.0")

# 初始化识别器和数据库
recognizer = CatRecognizer()
db = Database()

# 复用识别器内置的闸门实例（避免重复加载级联分类器）；main 先过闸门，
# 再把结果传给 extract_all 跳过重复闸门计算。
gate = recognizer.gate


@app.on_event("startup")
async def _startup_warmup():
    """容器启动时同步完成冷加载（ORB / FAISS / MySQL），让「启动完成」与「服务可用」对齐。

    UAT 2 核 CPU 实测：二期加载 Haar 级联 + 384 维特征 + FAISS 后，首个真实请求
    可超过 15s，导致 Java 侧预热第一次超时、期间用户请求被判降级。把冷加载前移
    到 startup，避免 Uvicorn 已监听却仍要等冷启动。
    """
    try:
        result = await warmup()
        logger.info(f"启动预热完成: {result}")
    except Exception as e:
        logger.warning(f"启动预热失败（不阻断启动）: {e}")


class MatchResult(BaseModel):
    cat_id: int
    similarity: float
    image_url: str
    local_score: Optional[float] = None
    # 三期新增诊断字段（Java 侧仅记日志，不参与业务判定；多余字段 Jackson 忽略）
    global_cos: Optional[float] = None
    good_matches: Optional[int] = None
    inliers: Optional[int] = None
    descriptor: Optional[str] = None


class RecognizeResponse(BaseModel):
    success: bool
    message: str
    matches: Optional[List[MatchResult]] = None
    feature_id: Optional[int] = None
    # 二期新增（向后兼容，Java 侧按字段存在与否适配）
    no_cat: Optional[bool] = False
    reject_reason: Optional[str] = None


# ---------------------------------------------------------------------------
# 统一响应助手
# ---------------------------------------------------------------------------
def _reject_response(gate_result: dict) -> RecognizeResponse:
    """闸门拒识：success=false, no_cat=true, matches=[]（HTTP 200）。"""
    return RecognizeResponse(
        success=False,
        no_cat=True,
        reject_reason=gate_result.get("reason"),
        message=gate_result.get("message") or "照片中未检测到猫咪，请拍摄猫咪正面或侧面全身照",
        matches=[],
    )


def _structured_error(message: str) -> JSONResponse:
    """所有异常的兜底：结构化 success=false 的 HTTP 200。"""
    return JSONResponse(status_code=200, content={
        "success": False,
        "message": message,
        "no_cat": False,
        "reject_reason": None,
        "matches": [],
    })


def _decode(contents: bytes) -> np.ndarray:
    """bytes -> BGR ndarray（统一 3 通道）。"""
    image = Image.open(io.BytesIO(contents)).convert("RGB")
    return cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)


# ---------------------------------------------------------------------------
# 全局异常兜底：框架层 422 / 未捕获 500 一律转 200 结构化
# ---------------------------------------------------------------------------
@app.exception_handler(RequestValidationError)
async def _validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.warning(f"请求参数校验失败：{exc}")
    return _structured_error("请求参数有误，请检查 file / top_k / cat_id")


@app.exception_handler(Exception)
async def _unhandled_exception_handler(request: Request, exc: Exception):
    logger.error(f"未处理异常：{exc}", exc_info=True)
    return _structured_error("识别服务内部异常，请稍后重试")


# ---------------------------------------------------------------------------
# 接口
# ---------------------------------------------------------------------------
@app.get("/health")
async def health_check():
    """健康检查"""
    return {"status": "ok", "timestamp": datetime.now().isoformat()}


@app.post("/api/recognize/warmup")
async def warmup():
    """启动/恢复预热专用端点（仅供 Java 内网调用，8002 只绑 127.0.0.1）。

    二期新增 CatGate 后，一期的随机噪点预热图会被闸门直接拒识，/match 虽返回 200
    却没有真正触发 ORB / FAISS 冷加载。此端点跳过闸门，真实走一次特征提取 +
    FAISS 粗召回；不写库、不产生候选、不对外暴露识别能力。
    """
    try:
        img = np.zeros((224, 224, 3), dtype=np.uint8)
        for x in range(224):
            img[:, x] = (40 + int(110 * x / 224), 80 + int(90 * x / 224), 120 + int(60 * x / 224))
        cv2.circle(img, (112, 112), 58, (205, 165, 95), -1)
        cv2.circle(img, (86, 96), 11, (35, 35, 35), -1)
        cv2.circle(img, (138, 96), 11, (35, 35, 35), -1)
        cv2.line(img, (96, 140), (128, 140), (60, 60, 60), 3)
        # 加少量纹理，保证 ORB 能提到关键点
        img = cv2.add(img, np.random.default_rng(20261115)
                      .integers(0, 16, img.shape, dtype=np.uint8))
        feature, desc, _ = recognizer.extract_all(img, gate_result={"pass": True})
        candidates = db.search_candidates(feature, top_k=1) if feature is not None else []
        return {
            "success": feature is not None,
            "warmed": feature is not None,
            "descriptors": 0 if desc is None else int(len(desc)),
            "candidates": len(candidates),
            "timestamp": datetime.now().isoformat(),
        }
    except Exception as e:
        logger.error(f"预热失败：{e}", exc_info=True)
        return {"success": False, "message": str(e)}


@app.post("/api/recognize/extract", response_model=RecognizeResponse)
async def extract_feature(file: UploadFile = File(...)):
    """提取照片特征，返回特征向量 ID（临时行 cat_id=0）。"""
    try:
        image_bgr = _decode(await file.read())
        gr = gate.check(image_bgr)               # L1 闸门
        if not gr["pass"]:
            return _reject_response(gr)

        feature = recognizer.extract_feature(image_bgr, gate_result=gr)
        if feature is None:
            return RecognizeResponse(
                success=False, no_cat=True, reject_reason="feature_error",
                message="照片特征提取失败，请重新拍摄", matches=[])

        feature_id = db.save_feature(feature)
        return RecognizeResponse(success=True, message="特征提取成功", feature_id=feature_id)
    except Exception as e:
        logger.error(f"特征提取失败：{e}", exc_info=True)
        return RecognizeResponse(success=False, message="特征提取失败，请稍后重试", matches=[])


@app.post("/api/recognize/match", response_model=RecognizeResponse)
async def match_cat(
    file: UploadFile = File(...),
    top_k: int = Form(3)
):
    """匹配数据库中的猫，仅返回按分数降序的候选（不做阈值过滤）。"""
    try:
        image_bgr = _decode(await file.read())

        # ---------- L1：拒识闸门 ----------
        gr = gate.check(image_bgr)
        if not gr["pass"]:
            return _reject_response(gr)

        # ---------- L2：全局特征 + ORB 描述子/关键点（一次预处理） ----------
        feature, q_desc, q_kp = recognizer.extract_all(image_bgr, gate_result=gr)
        if feature is None:
            return RecognizeResponse(
                success=False, no_cat=True, reject_reason="feature_error",
                message="照片特征提取失败，请重新拍摄", matches=[])

        # ---------- L3：FAISS 粗召回 top_k*12（三期：为按猫分组留足余量） ----------
        candidates = db.search_candidates(feature, top_k=top_k * 12)

        scored: List[MatchResult] = []
        for c in candidates:
            # BFMatcher + Lowe + RANSAC 内点 -> local_score
            local = local_match_score(q_desc, q_kp, c["descriptors"], c["keypoints"])
            # similarity = GLOBAL_WEIGHT * global_cos + LOCAL_WEIGHT * local_score
            sim = blend_similarity(c["global_cos"], local["local_score"])
            scored.append(MatchResult(
                cat_id=int(c["cat_id"]),
                similarity=round(sim, 4),
                image_url=c["image_url"] or "",
                local_score=round(local["local_score"], 4),
                global_cos=round(float(c["global_cos"]), 4),
                good_matches=int(local["good_matches"]),
                inliers=int(local["inliers"]),
                descriptor=DESCRIPTOR,
            ))

        # 三期口径：同一只猫的多张基准图先在猫内取最高分，再跨猫排序取 top_k。
        # 避免同猫多图占满 top3、导致 Java marginThreshold 的「top1 vs top2」判定失真。
        best_by_cat: Dict[int, MatchResult] = {}
        for m in scored:
            cur = best_by_cat.get(m.cat_id)
            if cur is None or m.similarity > cur.similarity:
                best_by_cat[m.cat_id] = m
        grouped = sorted(best_by_cat.values(), key=lambda x: x.similarity, reverse=True)
        scored = grouped[:max(1, top_k)]

        return RecognizeResponse(
            success=True,
            message=f"匹配完成，返回 {len(scored)} 个候选",
            matches=scored,
        )
    except Exception as e:
        logger.error(f"匹配失败：{e}", exc_info=True)
        return RecognizeResponse(success=False, message="匹配服务暂不可用，请稍后重试", matches=[])


@app.post("/api/recognize/register", response_model=RecognizeResponse)
async def register_cat(
    file: UploadFile = File(...),
    cat_id: int = Form(...),
    image_url: str = Form("")            # 可选，向后兼容（Java 不传则空串）
):
    """注册新猫基准图：同步保存 feature、descriptors、image_url。"""
    try:
        image_bgr = _decode(await file.read())
        gr = gate.check(image_bgr)
        if not gr["pass"]:
            return _reject_response(gr)

        feature, desc, kp = recognizer.extract_all(image_bgr, gate_result=gr)
        if feature is None:
            return RecognizeResponse(
                success=False, no_cat=True, reject_reason="feature_error",
                message="照片特征提取失败，请重新拍摄", matches=[])

        feature_id = db.save_feature_with_cat_id(
            feature, cat_id, image_url=image_url, descriptors=desc, keypoints=kp)
        return RecognizeResponse(
            success=True, message=f"猫咪 {cat_id} 注册成功", feature_id=feature_id)
    except Exception as e:
        logger.error(f"注册失败：{e}", exc_info=True)
        return RecognizeResponse(success=False, message="注册失败，请稍后重试", matches=[])


@app.post("/api/recognize/delete", response_model=RecognizeResponse)
async def delete_feature(feature_id: int = Form(...)):
    """删除一条基准特征（三期）：删行 + 重建内存索引。误登记/重传场景用。"""
    try:
        deleted = db.delete_feature(feature_id)
        if not deleted:
            return RecognizeResponse(
                success=False, message=f"特征 {feature_id} 不存在或不可删除", matches=[])
        return RecognizeResponse(success=True, message=f"特征 {feature_id} 已删除")
    except Exception as e:
        logger.error(f"删除特征失败：{e}", exc_info=True)
        return RecognizeResponse(success=False, message="删除失败，请稍后重试", matches=[])


@app.get("/api/recognize/stats")
async def get_stats():
    """获取数据库统计信息（透传 feature_dim / gate_enabled / usable_features）。"""
    try:
        return db.get_stats()
    except Exception as e:
        logger.error(f"获取统计失败：{e}", exc_info=True)
        return {
            "success": False,
            "error": str(e),
            "feature_dim": recognizer.feature_dim,
            "gate_enabled": gate.config.ENABLED,
            "usable_features": 0,
        }
