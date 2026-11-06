#!/bin/bash
# 流浪猫喂养系统完整部署脚本
# 在 47.95.149.170 上执行

set -e

echo "========================================"
echo "  流浪猫喂养系统部署脚本 v2.0"
echo "  包含：Spring Boot + Python 识别服务"
echo "========================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查是否在服务器上
if [[ ! -d "/opt/stray-cat" ]]; then
    log_error "请在服务器上执行此脚本，目录 /opt/stray-cat 不存在"
    exit 1
fi

cd /opt/stray-cat

# 1. 拉取最新代码
log_info "1. 拉取最新代码..."
git pull origin main || {
    log_error "Git pull 失败"
    exit 1
}

# 2. 备份旧 jar
log_info "2. 备份旧 jar..."
if [[ -f "app/stray-cat-server-1.0.0.jar" ]]; then
    BACKUP_DIR="backup/$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    cp app/stray-cat-server-1.0.0.jar "$BACKUP_DIR/"
    log_info "   备份到: $BACKUP_DIR"
fi

# 3. 构建 Docker 镜像
log_info "3. 构建 Docker 镜像..."
docker-compose build recognizer || {
    log_error "Recognizer 镜像构建失败"
    exit 1
}

# 4. 停止旧容器
log_info "4. 停止旧容器..."
docker-compose down app recognizer || true

# 5. 启动所有服务
log_info "5. 启动所有服务..."
docker-compose up -d mysql redis recognizer web

# 等待 MySQL 就绪
log_info "   等待 MySQL 就绪..."
sleep 10

# 6. 上传新 jar 并启动 Spring Boot
log_info "6. 启动 Spring Boot 应用..."
docker-compose up -d app

# 7. 查看日志
log_info "7. 查看服务状态..."
echo ""
docker-compose ps
echo ""

# 8. 健康检查
log_info "8. 健康检查..."

# 检查 Spring Boot
SPRING_BOOT_HEALTH=$(curl -s http://localhost:8080/api/actuator/health 2>/dev/null || echo "FAIL")
if [[ "$SPRING_BOOT_HEALTH" == *"UP"* ]]; then
    log_info "   ✓ Spring Boot 正常"
else
    log_warn "   ✗ Spring Boot 未就绪，请稍后检查日志"
fi

# 检查 Python 识别服务
PYTHON_HEALTH=$(curl -s http://localhost:8001/health 2>/dev/null || echo "FAIL")
if [[ "$PYTHON_HEALTH" == *"ok"* ]]; then
    log_info "   ✓ Python 识别服务正常"
else
    log_warn "   ✗ Python 识别服务未就绪，请稍后检查日志"
fi

# 检查 Nginx
NGINX_CHECK=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/ 2>/dev/null || echo "000")
if [[ "$NGINX_CHECK" == "200" || "$NGINX_CHECK" == "404" ]]; then
    log_info "   ✓ Nginx 正常"
else
    log_warn "   ✗ Nginx 异常 (HTTP $NGINX_CHECK)"
fi

echo ""
log_info "========================================"
log_info "  部署完成！"
log_info "========================================"
echo ""
log_info "访问地址："
echo "  - API:       https://straycat.fun/api"
echo "  - 识别服务:  http://47.95.149.170:8001"
echo "  - API 文档:  https://straycat.fun/api/doc.html"
echo ""
log_info "查看日志："
echo "  docker-compose logs -f app         # Spring Boot"
echo "  docker-compose logs -f recognizer  # Python 识别"
echo "  docker-compose logs -f web         # Nginx"
echo ""
log_info "重启服务："
echo "  docker-compose restart app"
echo "  docker-compose restart recognizer"
echo ""
