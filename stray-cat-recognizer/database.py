"""
数据库操作：特征向量存储与检索
使用 MySQL 存储特征向量，FAISS 做向量检索
"""
import pymysql
import numpy as np
import faiss
import os
import logging
from typing import List, Dict, Optional
from datetime import datetime

logger = logging.getLogger(__name__)


class Database:
    """特征向量数据库"""
    
    def __init__(self):
        # 数据库配置（从环境变量读取）
        self.db_config = {
            "host": os.getenv("MYSQL_HOST", "mysql"),
            "port": int(os.getenv("MYSQL_PORT", 3306)),
            "user": os.getenv("MYSQL_USER", "stray_cat"),
            "password": os.getenv("MYSQL_PASSWORD", "stray_cat_2024"),
            "database": os.getenv("MYSQL_DATABASE", "stray_cat"),
            "charset": "utf8mb4"
        }
        
        # FAISS 索引（内存中，启动时从数据库加载）
        self.feature_dim = 144
        self.index = faiss.IndexFlatIP(self.feature_dim)  # 内积索引（余弦相似度）
        
        # 映射：FAISS 索引位置 -> (feature_id, cat_id, image_url)
        self.id_map = []
        
        # 初始化
        self._init_database()
        self._load_features_to_index()
        
        logger.info("Database 初始化完成")
    
    def _init_database(self):
        """初始化数据库表"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                # 创建特征表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS cat_feature (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '特征 ID',
                        cat_id BIGINT NOT NULL COMMENT '关联猫咪 ID',
                        image_url VARCHAR(255) NOT NULL COMMENT '特征照片 URL',
                        feature_vector BLOB NOT NULL COMMENT '512 维特征向量（二进制）',
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        INDEX idx_cat_id (cat_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪花纹特征表'
                """)
            conn.commit()
            logger.info("数据库表初始化完成")
        except Exception as e:
            logger.error(f"数据库初始化失败：{e}", exc_info=True)
            raise
        finally:
            conn.close()
    
    def _get_connection(self):
        """获取数据库连接"""
        return pymysql.connect(**self.db_config)
    
    def _load_features_to_index(self):
        """从数据库加载所有特征到 FAISS 索引"""
        conn = self._get_connection()
        try:
            with conn.cursor(pymysql.cursors.DictCursor) as cursor:
                cursor.execute("""
                    SELECT id, cat_id, image_url, feature_vector 
                    FROM cat_feature 
                    ORDER BY id
                """)
                rows = cursor.fetchall()
                
                if not rows:
                    logger.info("数据库为空，跳过加载")
                    return
                
                # 构建特征矩阵
                vectors = []
                for row in rows:
                    # 反序列化特征向量
                    vector = np.frombuffer(row["feature_vector"], dtype=np.float32)
                    if len(vector) == self.feature_dim:
                        vectors.append(vector)
                        self.id_map.append({
                            "feature_id": row["id"],
                            "cat_id": row["cat_id"],
                            "image_url": row["image_url"]
                        })
                
                if vectors:
                    # 添加到 FAISS 索引
                    matrix = np.array(vectors, dtype=np.float32)
                    self.index.add(matrix)
                    logger.info(f"加载 {len(vectors)} 个特征到索引")
        
        except Exception as e:
            logger.error(f"加载特征失败：{e}", exc_info=True)
        finally:
            conn.close()
    
    def save_feature(self, feature_vector: np.ndarray) -> int:
        """
        保存特征向量（不关联 cat_id，用于临时提取）
        
        Returns:
            feature_id
        """
        # 序列化特征向量
        vector_bytes = feature_vector.tobytes()
        
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO cat_feature (cat_id, image_url, feature_vector)
                    VALUES (0, '', %s)
                """, (vector_bytes,))
            conn.commit()
            return cursor.lastrowid
        except Exception as e:
            logger.error(f"保存特征失败：{e}", exc_info=True)
            raise
        finally:
            conn.close()
    
    def save_feature_with_cat_id(self, feature_vector: np.ndarray, cat_id: int, image_url: str = "") -> int:
        """
        保存特征向量并关联 cat_id
        
        Returns:
            feature_id
        """
        vector_bytes = feature_vector.tobytes()
        
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO cat_feature (cat_id, image_url, feature_vector)
                    VALUES (%s, %s, %s)
                """, (cat_id, image_url, vector_bytes))
            conn.commit()
            
            feature_id = cursor.lastrowid
            
            # 添加到 FAISS 索引
            self.index.add(feature_vector.reshape(1, -1))
            self.id_map.append({
                "feature_id": feature_id,
                "cat_id": cat_id,
                "image_url": image_url
            })
            
            return feature_id
        
        except Exception as e:
            logger.error(f"保存特征失败：{e}", exc_info=True)
            raise
        finally:
            conn.close()
    
    def search_similar(self, query_vector: np.ndarray, top_k: int = 3) -> List[Dict]:
        """
        搜索相似特征
        
        Args:
            query_vector: 查询特征向量 (512,)
            top_k: 返回前 K 个结果
        
        Returns:
            匹配结果列表 [{cat_id, similarity, image_url, feature_id}]
        """
        if self.index.ntotal == 0:
            return []
        
        # 归一化查询向量
        query_norm = query_vector / (np.linalg.norm(query_vector) + 1e-8)
        query_matrix = query_norm.reshape(1, -1).astype(np.float32)
        
        # FAISS 搜索（内积 = 余弦相似度，因为向量已归一化）
        distances, indices = self.index.search(query_matrix, top_k)
        
        results = []
        for i, idx in enumerate(indices[0]):
            if idx == -1:
                continue
            
            # 获取映射信息
            if idx < len(self.id_map):
                info = self.id_map[idx]
                results.append({
                    "feature_id": info["feature_id"],
                    "cat_id": info["cat_id"],
                    "image_url": info["image_url"],
                    "similarity": float(distances[0][i])
                })
        
        # 按相似度降序排序
        results.sort(key=lambda x: x["similarity"], reverse=True)
        
        return results
    
    def get_stats(self) -> Dict:
        """获取数据库统计信息"""
        conn = self._get_connection()
        try:
            with conn.cursor(pymysql.cursors.DictCursor) as cursor:
                # 总特征数
                cursor.execute("SELECT COUNT(*) as total FROM cat_feature")
                total = cursor.fetchone()["total"]
                
                # 关联的猫咪数
                cursor.execute("SELECT COUNT(DISTINCT cat_id) as cats FROM cat_feature WHERE cat_id > 0")
                cats = cursor.fetchone()["cats"]
                
                return {
                    "total_features": total,
                    "total_cats": cats,
                    "index_size": self.index.ntotal
                }
        except Exception as e:
            logger.error(f"获取统计失败：{e}", exc_info=True)
            return {"error": str(e)}
        finally:
            conn.close()
