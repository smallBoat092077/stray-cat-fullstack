"""
流浪猫花纹识别核心算法
使用 ORB 特征点 + 颜色直方图 + 纹理特征
"""
import cv2
import numpy as np
from typing import Optional, Tuple
import logging

logger = logging.getLogger(__name__)


class CatRecognizer:
    """猫花纹识别器"""
    
    def __init__(self):
        # ORB 特征检测器（CPU 友好）
        self.orb = cv2.ORB_create(nfeatures=500)
        
        # 特征向量维度：ORB聚合(64) + 颜色直方图(64) + 纹理(16) = 144
        self.feature_dim = 144
        
        logger.info("CatRecognizer 初始化完成")
    
    def extract_feature(self, image: np.ndarray) -> Optional[np.ndarray]:
        """
        提取图片特征向量
        
        Args:
            image: BGR 格式图片 (H, W, 3)
        
        Returns:
            512 维特征向量，失败返回 None
        """
        try:
            # 1. 预处理
            processed = self._preprocess(image)
            if processed is None:
                return None
            
            # 2. 检测猫区域（简单版：用整个图片）
            # TODO: 可以集成 YOLO 猫检测模型
            cat_region = processed
            
            # 3. 提取 ORB 特征点
            orb_features = self._extract_orb(cat_region)
            
            # 4. 提取颜色直方图
            color_hist = self._extract_color_histogram(cat_region)
            
            # 5. 提取纹理特征（LBP）
            texture = self._extract_texture(cat_region)
            
            # 6. 拼接特征
            combined = np.concatenate([orb_features, color_hist, texture])
            
            # 7. 归一化
            combined = combined / (np.linalg.norm(combined) + 1e-8)
            
            return combined.astype(np.float32)
            
        except Exception as e:
            logger.error(f"特征提取失败：{e}", exc_info=True)
            return None
    
    def _preprocess(self, image: np.ndarray) -> Optional[np.ndarray]:
        """预处理：调整大小、增强对比度"""
        try:
            # 调整到统一尺寸
            resized = cv2.resize(image, (224, 224))
            
            # 增强对比度（CLAHE）
            lab = cv2.cvtColor(resized, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
            l_clahe = clahe.apply(l)
            lab_clahe = cv2.merge([l_clahe, a, b])
            enhanced = cv2.cvtColor(lab_clahe, cv2.COLOR_LAB2BGR)
            
            return enhanced
            
        except Exception as e:
            logger.error(f"预处理失败：{e}", exc_info=True)
            return None
    
    def _extract_orb(self, image: np.ndarray) -> np.ndarray:
        """提取 ORB 特征并聚合"""
        # 检测特征点和描述子
        keypoints, descriptors = self.orb.detectAndCompute(image, None)
        
        if descriptors is None or len(descriptors) == 0:
            # 没有检测到特征点，返回与正常路径一致的零向量（mean+std 各32维）
            return np.zeros(64, dtype=np.float32)
        
        # 聚合描述子（取平均 + 标准差）
        # descriptors shape: (N, 32), N 是特征点数量
        mean_desc = np.mean(descriptors, axis=0)
        std_desc = np.std(descriptors, axis=0)
        
        # 拼接
        aggregated = np.concatenate([mean_desc, std_desc])
        
        return aggregated.astype(np.float32)
    
    def _extract_color_histogram(self, image: np.ndarray) -> np.ndarray:
        """提取颜色直方图特征"""
        # 转换到 HSV 空间
        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        
        # 计算 H 和 S 通道的 2D 直方图
        # H: 0-180, S: 0-256
        hist = cv2.calcHist([hsv], [0, 1], None, [8, 8], [0, 180, 0, 256])
        
        # 归一化
        hist = cv2.normalize(hist, hist).flatten()
        
        return hist.astype(np.float32)
    
    def _extract_texture(self, image: np.ndarray) -> np.ndarray:
        """提取纹理特征（简化版 LBP）"""
        # 转换到灰度图
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        
        # 计算梯度（简化纹理特征）
        grad_x = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
        grad_y = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
        
        # 梯度幅值和方向
        magnitude = np.sqrt(grad_x**2 + grad_y**2)
        direction = np.arctan2(grad_y, grad_x)
        
        # 量化方向到 8 个 bin
        direction_bins = np.floor((direction + np.pi) / (2 * np.pi) * 8).astype(int)
        direction_bins = np.clip(direction_bins, 0, 7)
        
        # 计算每个方向的平均梯度幅值
        texture_features = np.zeros(8, dtype=np.float32)
        for i in range(8):
            mask = (direction_bins == i)
            if np.sum(mask) > 0:
                texture_features[i] = np.mean(magnitude[mask])
        
        # 再添加一些统计特征
        stats = np.array([
            np.mean(magnitude),
            np.std(magnitude),
            np.mean(gray),
            np.std(gray),
            np.mean(grad_x),
            np.std(grad_x),
            np.mean(grad_y),
            np.std(grad_y)
        ], dtype=np.float32)
        
        return np.concatenate([texture_features, stats])
    
    def compute_similarity(self, feature1: np.ndarray, feature2: np.ndarray) -> float:
        """
        计算两个特征向量的余弦相似度
        
        Returns:
            相似度 [0, 1]
        """
        dot_product = np.dot(feature1, feature2)
        norm1 = np.linalg.norm(feature1)
        norm2 = np.linalg.norm(feature2)
        
        similarity = dot_product / (norm1 * norm2 + 1e-8)
        
        return float(np.clip(similarity, 0, 1))
