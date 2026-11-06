#!/bin/bash
# SIT 环境部署脚本
# 在 47.97.253.32 上执行

set -e

echo "========================================"
echo "  流浪猫喂养系统 SIT 环境部署"
echo "  服务器: 47.97.253.32"
echo "========================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查目录
if [[ ! -d "/opt/stray-cat-sit" ]]; then
    log_error "SIT 目录不存在: /opt/stray-cat-sit"
    exit 1
fi

cd /opt/stray-cat-sit

# 1. 拉取最新代码
log_info "1. 拉取最新代码..."
git pull origin main || {
    log_error "Git pull 失败"
    exit 1
}

# 2. 检查配置文件
log_info "2. 检查配置文件..."
if [[ ! -f ".env" ]]; then
    log_error ".env 文件不存在，请先创建并配置"
    exit 1
fi

if [[ ! -f "app.env" ]]; then
    log_error "app.env 文件不存在，请先创建并配置"
    exit 1
fi

# 3. 构建 Docker 镜像
log_info "3. 构建 Docker 镜像..."
docker-compose -f docker-compose-sit.yml build recognizer || {
    log_error "Recognizer 镜像构建失败"
    exit 1
}

# 4. 停止旧容器
log_info "4. 停止旧容器..."
docker-compose -f docker-compose-sit.yml down || true

# 5. 启动所有服务
log_info "5. 启动 SIT 环境服务..."
docker-compose -f docker-compose-sit.yml up -d mysql redis recognizer web

# 等待 MySQL 就绪
log_info "   等待 MySQL 就绪..."
sleep 15

# 6. 启动 Spring Boot
log_info "6. 启动 Spring Boot 应用..."
docker-compose -f docker-compose-sit.yml up -d app

# 7. 查看服务状态
log_info "7. 查看服务状态..."
echo ""
docker-compose -f docker-compose-sit.yml ps
echo ""

# 8. 健康检查
log_info "8. 健康检查..."

# 检查 Spring Boot (端口 8082)
SPRING_BOOT_HEALTH=$(curl -s http://localhost:8082/api/actuator/health 2>/dev/null || echo "FAIL")
if [[ "$SPRING_BOOT_HEALTH" == *"UP"* ]]; then
    log_info "   ✓ Spring Boot 正常 (端口 8082)"
else
    log_warn "   ✗ Spring Boot 未就绪，请稍后检查日志"
fi

# 检查 Python 识别服务 (端口 8002)
PYTHON_HEALTH=$(curl -s http://localhost:8002/health 2>/dev/null || echo "FAIL")
if [[ "$PYTHON_HEALTH" == *"ok"* ]]; then
    log_info "   ✓ Python 识别服务正常 (端口 8002)"
else
    log_warn "   ✗ Python 识别服务未就绪，请稍后检查日志"
fi

# 检查 Nginx (端口 8083)
NGINX_CHECK=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/ 2>/dev/null || echo "000")
if [[ "$NGINX_CHECK" == "200" || "$NGINX_CHECK" == "404" ]]; then
    log_info "   ✓ Nginx 正常 (端口 8083)"
else
    log_warn "   ✗ Nginx 异常 (HTTP $NGINX_CHECK)"
fi

echo ""
log_info "========================================"
log_info "  SIT 环境部署完成！"
log_info "========================================"
echo ""
log_info "访问地址（SIT 环境）："
echo "  - API:       http://47.97.253.32:8082/api"
echo "  - 识别服务:  http://47.97.253.32:8002"
echo "  - Nginx:     http://47.97.253.32:8083"
echo "  - API 文档:  http://47.97.253.32:8082/api/doc.html"
echo ""
log_info "数据库连接："
echo "  - MySQL:     47.97.253.32:3307 (容器内 3306)"
echo "  - Redis:     47.97.253.32:6380 (容器内 6379)"
echo ""
log_info "查看日志："
echo "  docker-compose -f docker-compose-sit.yml logs -f app         # Spring Boot"
echo "  docker-compose -f docker-compose-sit.yml logs -f recognizer  # Python 识别"
echo "  docker-compose -f docker-compose-sit.yml logs -f web         # Nginx"
echo ""
log_info "重启服务："
echo "  docker-compose -f docker-compose-sit.yml restart app"
echo "  docker-compose -f docker-compose-sit.yml restart recognizer"
echo ""
log_info "停止 SIT 环境："
echo "  docker-compose -f docker-compose-sit.yml down"
echo ""
