# 流浪猫花纹识别服务

基于 ORB 特征点 + 颜色直方图 + 纹理特征的流浪猫个体识别服务。

## 技术栈

- **框架**: FastAPI (Python 3.11)
- **特征提取**: OpenCV (ORB + 颜色直方图 + LBP 纹理)
- **向量检索**: FAISS (CPU 版)
- **数据库**: MySQL 8.0 (存储特征向量)
- **部署**: Docker Compose

## 功能

1. **特征提取** (`POST /api/recognize/extract`)
   - 上传猫照片，提取 512 维特征向量
   - 返回特征 ID

2. **猫咪匹配** (`POST /api/recognize/match`)
   - 上传照片，与数据库中的猫匹配
   - 返回 Top-K 匹配结果（相似度 ≥ 85%）

3. **注册新猫** (`POST /api/recognize/register`)
   - 首次打卡时，将照片特征与猫咪 ID 关联

4. **统计信息** (`GET /api/recognize/stats`)
   - 查看数据库中特征数量和猫咪数量

## 本地开发

### 1. 安装依赖

```bash
cd stray-cat-recognizer
pip install -r requirements.txt
```

### 2. 配置环境变量

创建 `.env` 文件：

```env
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_USER=stray_cat
MYSQL_PASSWORD=stray_cat_2024
MYSQL_DATABASE=stray_cat
```

### 3. 运行服务

```bash
uvicorn main:app --reload --port 8000
```

访问 API 文档：http://localhost:8000/docs

### 4. 测试

```bash
python test_recognizer.py
```

## 部署到服务器

### 1. 上传代码到服务器

```bash
scp -r stray-cat-recognizer root@47.95.149.170:/opt/stray-cat/recognizer
```

### 2. 在服务器上构建并启动

```bash
ssh root@47.95.149.170
cd /opt/stray-cat
docker-compose build recognizer
docker-compose up -d recognizer
```

### 3. 查看日志

```bash
docker-compose logs -f recognizer
```

### 4. 健康检查

```bash
curl http://localhost:8001/health
```

## Nginx 反向代理

在 `/opt/stray-cat/nginx/nginx.conf` 中添加：

```nginx
location /api/recognize/ {
    proxy_pass http://recognizer:8000/api/recognize/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    client_max_body_size 10m;
    proxy_read_timeout 30s;
}
```

重启 Nginx：

```bash
docker-compose restart web
```

## 识别流程

### 打卡验证

```
用户拍照 → 上传服务端
         ↓
    调用 /api/recognize/match
         ↓
    相似度 > 85% → 匹配成功，记录打卡
    70-85% → 人工确认
    < 70% → 新猫，调用 /api/recognize/register
```

### 位置变更检测

```
用户拍新照片 → 调用 /api/recognize/match
             ↓
    找到匹配 → 返回猫咪信息 + 原位置
             ↓
    提示：「这只猫原来在 XX 位置」
```

## 性能指标

- **特征提取**: ~2 秒/张 (CPU)
- **向量检索**: ~0.1 秒 (1000 只猫)
- **并发**: 5-10 QPS
- **存储**: 512 字节/特征，1000 只猫 ≈ 0.5MB

## 后续优化

1. **猫检测模型**: 集成 YOLO 自动检测猫区域
2. **多照片融合**: 同一只猫多张照片特征平均
3. **增量学习**: 定期用新数据更新特征库
4. **GPU 加速**: 可选 NVIDIA GPU + CUDA 版本 FAISS

## 阈值说明

- **85%**: 自动匹配阈值（高置信度）
- **70-85%**: 人工确认区间
- **< 70%**: 视为新猫

可根据实际数据调整 `main.py` 中的 `MATCH_THRESHOLD`。
