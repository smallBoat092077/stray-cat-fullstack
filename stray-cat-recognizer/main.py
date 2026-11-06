"""
流浪猫花纹识别服务 - FastAPI
功能：特征提取、匹配、注册
"""
from fastapi import FastAPI, UploadFile, File, HTTPException, Form
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import Optional, List
import numpy as np
import cv2
from PIL import Image
import io
import os
import logging
from datetime import datetime

from recognizer import CatRecognizer
from database import Database

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="流浪猫花纹识别服务", version="1.0.0")

# 初始化识别器和数据库
recognizer = CatRecognizer()
db = Database()

# 识别阈值
MATCH_THRESHOLD = 0.85


class MatchResult(BaseModel):
    cat_id: int
    similarity: float
    image_url: str


class RecognizeResponse(BaseModel):
    success: bool
    message: str
    matches: Optional[List[MatchResult]] = None
    feature_id: Optional[int] = None


@app.get("/health")
async def health_check():
    """健康检查"""
    return {"status": "ok", "timestamp": datetime.now().isoformat()}


@app.post("/api/recognize/extract", response_model=RecognizeResponse)
async def extract_feature(file: UploadFile = File(...)):
    """
    提取照片特征
    返回特征向量 ID（用于后续匹配）
    """
    try:
        # 读取图片
        contents = await file.read()
        image = Image.open(io.BytesIO(contents))
        image_np = np.array(image)
        
        # 转换为 BGR（OpenCV 格式）
        if len(image_np.shape) == 3:
            image_bgr = cv2.cvtColor(image_np, cv2.COLOR_RGB2BGR)
        else:
            raise HTTPException(status_code=400, detail="图片格式错误")
        
        # 提取特征
        feature_vector = recognizer.extract_feature(image_bgr)
        
        if feature_vector is None:
            return RecognizeResponse(
                success=False,
                message="未检测到猫或图片质量不佳"
            )
        
        # 保存特征到数据库
        feature_id = db.save_feature(feature_vector)
        
        return RecognizeResponse(
            success=True,
            message="特征提取成功",
            feature_id=feature_id
        )
        
    except Exception as e:
        logger.error(f"特征提取失败：{e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/recognize/match", response_model=RecognizeResponse)
async def match_cat(
    file: UploadFile = File(...),
    top_k: int = Form(3)
):
    """
    匹配数据库中的猫
    返回 Top-K 匹配结果
    """
    try:
        # 读取图片
        contents = await file.read()
        image = Image.open(io.BytesIO(contents))
        image_np = np.array(image)
        
        # 转换为 BGR
        if len(image_np.shape) == 3:
            image_bgr = cv2.cvtColor(image_np, cv2.COLOR_RGB2BGR)
        else:
            raise HTTPException(status_code=400, detail="图片格式错误")
        
        # 提取特征
        feature_vector = recognizer.extract_feature(image_bgr)
        
        if feature_vector is None:
            return RecognizeResponse(
                success=False,
                message="未检测到猫或图片质量不佳"
            )
        
        # 搜索匹配
        matches = db.search_similar(feature_vector, top_k=top_k)
        
        # 过滤阈值
        valid_matches = [
            MatchResult(
                cat_id=m["cat_id"],
                similarity=m["similarity"],
                image_url=m["image_url"]
            )
            for m in matches
            if m["similarity"] >= MATCH_THRESHOLD
        ]
        
        if valid_matches:
            best = valid_matches[0]
            return RecognizeResponse(
                success=True,
                message=f"匹配成功：猫咪 ID {best.cat_id}（相似度 {best.similarity:.2%}）",
                matches=valid_matches
            )
        else:
            return RecognizeResponse(
                success=False,
                message="未找到匹配的猫咪（相似度均低于 85%）",
                matches=[MatchResult(**m) for m in matches]  # 返回所有结果供参考
            )
        
    except Exception as e:
        logger.error(f"匹配失败：{e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/recognize/register", response_model=RecognizeResponse)
async def register_cat(
    file: UploadFile = File(...),
    cat_id: int = Form(...)
):
    """
    注册新猫（首次打卡）
    将照片特征与猫咪 ID 关联
    """
    try:
        # 读取图片
        contents = await file.read()
        image = Image.open(io.BytesIO(contents))
        image_np = np.array(image)
        
        # 转换为 BGR
        if len(image_np.shape) == 3:
            image_bgr = cv2.cvtColor(image_np, cv2.COLOR_RGB2BGR)
        else:
            raise HTTPException(status_code=400, detail="图片格式错误")
        
        # 提取特征
        feature_vector = recognizer.extract_feature(image_bgr)
        
        if feature_vector is None:
            return RecognizeResponse(
                success=False,
                message="未检测到猫或图片质量不佳"
            )
        
        # 保存到数据库（关联 cat_id）
        feature_id = db.save_feature_with_cat_id(feature_vector, cat_id)
        
        return RecognizeResponse(
            success=True,
            message=f"猫咪 {cat_id} 注册成功",
            feature_id=feature_id
        )
        
    except Exception as e:
        logger.error(f"注册失败：{e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/recognize/stats")
async def get_stats():
    """获取数据库统计信息"""
    try:
        stats = db.get_stats()
        return stats
    except Exception as e:
        logger.error(f"获取统计失败：{e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
