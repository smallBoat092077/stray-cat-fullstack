"""
数据库操作：特征向量存储与检索（花纹识别二期治本）
使用 MySQL 存储特征向量 + ORB 局部描述子，FAISS 做向量粗召回。

二期改动：
- feature_dim 与 recognizer.FEATURE_DIM 统一（import，杜绝散落魔法数字）。
- _init_database() 幂等新增 descriptors BLOB NULL 列（先查 information_schema，
  ALTER 再 try/except 兜底并发竞态）。
- save_feature_with_cat_id() 支持 descriptors / keypoints / image_url。
- 新增 search_candidates(query_vector, top_k)：FAISS 粗召回候选（含
  feature_id / cat_id / image_url / global_cos / descriptors 反序列化 / keypoints）。
- get_stats() 增加 feature_dim / gate_enabled / usable_features。
- _load_features_to_index() 跳过维度不匹配的旧行并打日志。

descriptors 存储格式（自描述，避免列数歧义）：
    [int32 cols LE] + float32[cols*N] 数据
    三期泛化：不再限定描述子宽度，支持 ORB(32)/AKAZE(61)/SIFT(128)。
    cols = desc_w + 2 -> 每行 [desc(desc_w) + 关键点坐标(x,y)]
    cols = 32        -> 仅 ORB 描述子（旧数据/无坐标），重排时退化为不含几何校验
    cols == 34 旧格式按 ORB(32)+坐标 解析，与新格式 (desc_w=32) 完全一致。
    新格式描述子统一以 float32 存储，反序列化保持 float32（SIFT 正确）；
    二进制描述子以 float32 参与 L2 匹配，排序与 Hamming 等价
    （二值向量欧氏距离平方 == Hamming 距离），正确性不受影响。
"""
import os
import struct
import logging
from typing import List, Dict, Optional

import pymysql
import numpy as np
import faiss

from recognizer import FEATURE_DIM

logger = logging.getLogger(__name__)


def _env_gate_enabled() -> bool:
    v = os.getenv("GATE_ENABLED")
    if v is None:
        return True
    return v.strip().lower() in ("1", "true", "yes", "on")


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
            "charset": "utf8mb4",
        }

        # FAISS 索引：维度与 recognizer 对齐
        self.feature_dim = FEATURE_DIM
        self.index = faiss.IndexFlatIP(self.feature_dim)  # 内积索引（输入向量已 L2 归一化）

        # 映射：FAISS 索引位置 -> 基准信息（含反序列化后的描述子与关键点）
        self.id_map = []

        # 闸门开关（透传给 /stats，便于 UAT 灰度对比）
        self.gate_enabled = _env_gate_enabled()

        self._init_database()
        self._load_features_to_index()

        logger.info(f"Database 初始化完成，feature_dim={self.feature_dim}")

    # ------------------------------------------------------------------
    # 表结构
    # ------------------------------------------------------------------
    def _init_database(self):
        """初始化数据库表；幂等新增 descriptors 列。"""
        conn = self._get_connection()
        try:
            with conn.cursor(pymysql.cursors.DictCursor) as cursor:
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS cat_feature (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '特征 ID',
                        cat_id BIGINT NOT NULL COMMENT '关联猫咪 ID',
                        image_url VARCHAR(255) NOT NULL DEFAULT '' COMMENT '特征照片 URL',
                        feature_vector BLOB NOT NULL COMMENT '384 维特征向量（二进制）',
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        INDEX idx_cat_id (cat_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪花纹特征表'
                """)
                # 幂等加列：先查 information_schema
                cursor.execute("""
                    SELECT COUNT(*) AS c FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'cat_feature'
                      AND COLUMN_NAME = 'descriptors'
                """)
                row = cursor.fetchone()
                has_col = bool(row and row["c"])
                if not has_col:
                    try:
                        cursor.execute(
                            "ALTER TABLE cat_feature "
                            "ADD COLUMN descriptors BLOB NULL "
                            "COMMENT 'ORB 局部描述子+关键点坐标（二期重排用）'"
                        )
                        logger.info("cat_feature.descriptors 列已新增")
                    except Exception as alter_err:
                        # 并发/旧版本兜底：Duplicate column 直接吞掉
                        if "1060" in str(alter_err) or "Duplicate column" in str(alter_err):
                            logger.info("cat_feature.descriptors 列已存在（并发），跳过")
                        else:
                            raise
            conn.commit()
            logger.info("数据库表初始化完成")
        except Exception as e:
            logger.error(f"数据库初始化失败：{e}", exc_info=True)
            raise
        finally:
            conn.close()

    def _get_connection(self):
        return pymysql.connect(**self.db_config)

    # ------------------------------------------------------------------
    # descriptors 打包 / 反序列化
    # ------------------------------------------------------------------
    @staticmethod
    def _pack_descriptors(descriptors, keypoints) -> Optional[bytes]:
        """把 (N×W 描述子, N×2 关键点) 打包为 BLOB；无描述子返回 None。

        三期泛化：描述子宽度不再限定 32（ORB），支持 AKAZE(61)/SIFT(128)。
        带关键点时 cols = desc_w + 2；无关键点时 cols = desc_w。
        """
        if descriptors is None:
            return None
        desc = np.asarray(descriptors)
        if desc.ndim != 2 or desc.shape[1] < 2 or len(desc) == 0:
            return None
        kp = np.asarray(keypoints, dtype=np.float32) if keypoints is not None else None
        if kp is not None and kp.shape == (len(desc), 2):
            arr = np.concatenate([desc.astype(np.float32), kp], axis=1)  # N×(desc_w+2)
        else:
            arr = desc.astype(np.float32)                                  # N×desc_w
        arr = np.ascontiguousarray(arr, dtype=np.float32)
        return struct.pack("<i", arr.shape[1]) + arr.tobytes()

    @staticmethod
    def _unpack_descriptors(blob):
        """BLOB -> (descriptors N×W | None, keypoints float32 N×2 | None)。"""
        if blob is None or len(blob) < 4:
            return None, None
        try:
            cols = struct.unpack("<i", blob[:4])[0]
            arr = np.frombuffer(blob, dtype=np.float32, offset=4)
            if arr.size == 0 or arr.size % cols != 0:
                return None, None
            if cols == 32:
                # 旧格式：仅 ORB 描述子（uint8 语义），无坐标
                return arr.reshape(-1, 32).astype(np.uint8), None
            if cols == 34:
                # 旧格式/新格式一致：ORB(32) + 关键点坐标
                m = arr.reshape(-1, 34)
                return m[:, :32].astype(np.uint8), m[:, 32:34].astype(np.float32)
            if cols > 2:
                # 三期泛化格式：desc(cols-2) + 关键点坐标；保持 float32（SIFT 正确）
                m = arr.reshape(-1, cols)
                return np.ascontiguousarray(m[:, :cols - 2]), m[:, cols - 2:].copy()
        except Exception as e:
            logger.warning(f"descriptors 反序列化失败：{e}")
        return None, None

    # ------------------------------------------------------------------
    # 加载索引
    # ------------------------------------------------------------------
    def _load_features_to_index(self):
        """从数据库加载特征到 FAISS 索引；跳过维度不匹配的旧行并打日志。"""
        conn = self._get_connection()
        try:
            with conn.cursor(pymysql.cursors.DictCursor) as cursor:
                cursor.execute("""
                    SELECT id, cat_id, image_url, feature_vector, descriptors
                    FROM cat_feature
                    ORDER BY id
                """)
                rows = cursor.fetchall()

                if not rows:
                    logger.info("数据库为空，跳过加载")
                    return

                vectors = []
                for row in rows:
                    vector = np.frombuffer(row["feature_vector"], dtype=np.float32)
                    if len(vector) != self.feature_dim:
                        # 维度变更（一期 144 / 二期 384）后的旧行：跳过，不进索引
                        logger.warning(
                            f"跳过维度不匹配的旧特征行 id={row['id']} "
                            f"dim={len(vector)} != {self.feature_dim}"
                        )
                        continue
                    desc, kp = self._unpack_descriptors(row.get("descriptors"))
                    vectors.append(vector)
                    self.id_map.append({
                        "feature_id": row["id"],
                        "cat_id": row["cat_id"],
                        "image_url": row["image_url"] or "",
                        "descriptors": desc,
                        "keypoints": kp,
                    })

                if vectors:
                    matrix = np.array(vectors, dtype=np.float32)
                    self.index.add(matrix)
                    logger.info(f"加载 {len(vectors)} 个特征到索引")
        except Exception as e:
            logger.error(f"加载特征失败：{e}", exc_info=True)
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # 写入
    # ------------------------------------------------------------------
    def save_feature(self, feature_vector: np.ndarray) -> int:
        """保存临时特征（cat_id=0，不进索引，用于 /extract 临时行）。"""
        vector_bytes = np.asarray(feature_vector, dtype=np.float32).tobytes()
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO cat_feature (cat_id, image_url, feature_vector, descriptors)
                    VALUES (0, '', %s, NULL)
                """, (vector_bytes,))
            conn.commit()
            return cursor.lastrowid
        except Exception as e:
            logger.error(f"保存特征失败：{e}", exc_info=True)
            raise
        finally:
            conn.close()

    def save_feature_with_cat_id(self, feature_vector: np.ndarray, cat_id: int,
                                 image_url: str = "", descriptors=None,
                                 keypoints=None) -> int:
        """保存基准特征并关联 cat_id；同步落库 descriptors / image_url。"""
        vector_bytes = np.asarray(feature_vector, dtype=np.float32).tobytes()
        desc_blob = self._pack_descriptors(descriptors, keypoints)
        desc_arr = np.asarray(descriptors, dtype=np.uint8) if descriptors is not None else None
        kp_arr = np.asarray(keypoints, dtype=np.float32) if keypoints is not None else None

        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO cat_feature (cat_id, image_url, feature_vector, descriptors)
                    VALUES (%s, %s, %s, %s)
                """, (cat_id, image_url or "", vector_bytes, desc_blob))
            conn.commit()
            feature_id = cursor.lastrowid

            # 同步加入 FAISS 索引与映射
            self.index.add(np.asarray(feature_vector, dtype=np.float32).reshape(1, -1))
            self.id_map.append({
                "feature_id": feature_id,
                "cat_id": cat_id,
                "image_url": image_url or "",
                "descriptors": desc_arr,
                "keypoints": kp_arr,
            })
            return feature_id
        except Exception as e:
            logger.error(f"保存特征失败：{e}", exc_info=True)
            raise
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # 检索
    # ------------------------------------------------------------------
    def search_candidates(self, query_vector: np.ndarray, top_k: int = 3) -> List[Dict]:
        """FAISS 粗召回候选（不在这里做局部重排、不做阈值过滤）。

        Returns:
            [{feature_id, cat_id, image_url, global_cos, descriptors, keypoints}, ...]
            descriptors/keypoints 为反序列化后的 ndarray（可能为 None）。
        """
        if self.index.ntotal == 0:
            return []
        query_norm = query_vector / (np.linalg.norm(query_vector) + 1e-8)
        query_matrix = query_norm.reshape(1, -1).astype(np.float32)
        k = max(1, min(top_k, self.index.ntotal))
        distances, indices = self.index.search(query_matrix, k)

        results = []
        for i, idx in enumerate(indices[0]):
            if idx == -1 or idx >= len(self.id_map):
                continue
            info = self.id_map[idx]
            results.append({
                "feature_id": info["feature_id"],
                "cat_id": info["cat_id"],
                "image_url": info["image_url"],
                "global_cos": float(distances[0][i]),
                "descriptors": info.get("descriptors"),
                "keypoints": info.get("keypoints"),
            })
        results.sort(key=lambda x: x["global_cos"], reverse=True)
        return results

    def search_similar(self, query_vector: np.ndarray, top_k: int = 3) -> List[Dict]:
        """兼容一期签名：返回 [{cat_id, similarity, image_url, feature_id}]。"""
        candidates = self.search_candidates(query_vector, top_k)
        return [
            {
                "feature_id": c["feature_id"],
                "cat_id": c["cat_id"],
                "image_url": c["image_url"],
                "similarity": c["global_cos"],
            }
            for c in candidates
        ]

    # ------------------------------------------------------------------
    # 删除（三期：样本误登记/重传需要，删除后重建内存索引）
    # ------------------------------------------------------------------
    def delete_feature(self, feature_id: int) -> bool:
        """删除一条基准特征（仅允许删 cat_id>0 的基准行），并重建 FAISS 内存索引。

        Returns:
            True=已删除；False=行不存在或为临时行（cat_id=0）。
        """
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                affected = cursor.execute(
                    "DELETE FROM cat_feature WHERE id = %s AND cat_id > 0", (feature_id,))
            conn.commit()
            if affected:
                self._reload_index()
                logger.info(f"特征 {feature_id} 已删除，索引已重建（{self.index.ntotal} 条）")
            return affected > 0
        except Exception as e:
            logger.error(f"删除特征失败：{e}", exc_info=True)
            raise
        finally:
            conn.close()

    def _reload_index(self):
        """清空内存索引并从数据库全量重建（样本量小，全量重建成本可忽略）。"""
        self.index.reset()
        self.id_map.clear()
        self._load_features_to_index()

    # ------------------------------------------------------------------
    # 统计
    # ------------------------------------------------------------------
    def get_stats(self) -> Dict:
        """统计信息；新增 feature_dim / gate_enabled / usable_features。"""
        conn = self._get_connection()
        try:
            with conn.cursor(pymysql.cursors.DictCursor) as cursor:
                cursor.execute("SELECT COUNT(*) as total FROM cat_feature")
                total = cursor.fetchone()["total"]
                cursor.execute("SELECT COUNT(DISTINCT cat_id) as cats FROM cat_feature WHERE cat_id > 0")
                cats = cursor.fetchone()["cats"]

                # 可用特征：已进索引（维度匹配）且带描述子（可参与 L3 重排）
                usable_features = sum(
                    1 for info in self.id_map if info.get("descriptors") is not None
                )

                return {
                    "total_features": total,
                    "total_cats": cats,
                    "index_size": self.index.ntotal,
                    "feature_dim": self.feature_dim,
                    "gate_enabled": self.gate_enabled,
                    "usable_features": usable_features,
                }
        except Exception as e:
            logger.error(f"获取统计失败：{e}", exc_info=True)
            return {"error": str(e)}
        finally:
            conn.close()
