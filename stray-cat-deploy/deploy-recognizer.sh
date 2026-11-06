#!/bin/bash
# 流浪猫花纹识别服务部署脚本
# 在 47.95.149.170 上执行

set -e

echo "=== 部署流浪猫花纹识别服务 ==="

# 1. 创建目录
echo "1. 创建目录..."
mkdir -p /opt/stray-cat/recognizer

# 2. 复制代码（从本地或 Git）
echo "2. 复制代码..."
# 方式 A：从本地复制（需要 scp）
# scp -r stray-cat-recognizer/* root@47.95.149.170:/opt/stray-cat/recognizer/

# 方式 B：从 Git 拉取
cd /opt/stray-cat/recognizer
git clone https://github.com/smallBoat092077/stray-cat-fullstack.git . || git pull

# 3. 构建 Docker 镜像
echo "3. 构建 Docker 镜像..."
cd /opt/stray-cat
docker-compose build recognizer

# 4. 启动服务
echo "4. 启动服务..."
docker-compose up -d recognizer

# 5. 查看日志
echo "5. 查看日志..."
docker-compose logs -f recognizer &
sleep 10

# 6. 健康检查
echo "6. 健康检查..."
curl -f http://localhost:8001/health || echo "服务启动中，请稍后检查"

echo "=== 部署完成 ==="
echo "访问：http://47.95.149.170:8001/health"
echo "API 文档：http://47.95.149.170:8001/docs"
