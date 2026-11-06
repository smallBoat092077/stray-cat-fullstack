#!/bin/bash
# 更新 Nginx 配置，添加识别服务反向代理

set -e

echo "========================================"
echo "  更新 Nginx 配置（添加识别服务）"
echo "========================================"
echo ""

NGINX_CONF="/opt/stray-cat/nginx/default.conf"
RECOGNIZER_CONF="/opt/stray-cat/nginx/recognizer-location.conf"

# 检查配置文件是否存在
if [[ ! -f "$NGINX_CONF" ]]; then
    echo "错误：Nginx 配置文件不存在: $NGINX_CONF"
    exit 1
fi

if [[ ! -f "$RECOGNIZER_CONF" ]]; then
    echo "错误：识别服务配置不存在: $RECOGNIZER_CONF"
    exit 1
fi

# 备份原配置
BACKUP_FILE="${NGINX_CONF}.bak.$(date +%Y%m%d_%H%M%S)"
cp "$NGINX_CONF" "$BACKUP_FILE"
echo "✓ 备份原配置到: $BACKUP_FILE"

# 检查是否已包含识别服务配置
if grep -q "recognizer:8000" "$NGINX_CONF"; then
    echo "⚠ Nginx 配置已包含识别服务，跳过更新"
    exit 0
fi

# 在最后一个 location 块后插入识别服务配置
# 找到 } 前的位置插入
TEMP_FILE=$(mktemp)
awk '
/^}$/ {
    # 在最后的 } 前插入识别服务配置
    while ((getline line < "'"$RECOGNIZER_CONF"'") > 0) {
        print line
    }
}
{ print }
' "$NGINX_CONF" > "$TEMP_FILE"

mv "$TEMP_FILE" "$NGINX_CONF"
echo "✓ 已添加识别服务反向代理配置"

# 验证 Nginx 配置语法
echo ""
echo "验证 Nginx 配置..."
docker exec straycat-web nginx -t

if [[ $? -eq 0 ]]; then
    echo "✓ Nginx 配置语法正确"
    
    # 重载 Nginx
    echo ""
    echo "重载 Nginx..."
    docker exec straycat-web nginx -s reload
    
    if [[ $? -eq 0 ]]; then
        echo "✓ Nginx 重载成功"
    else
        echo "✗ Nginx 重载失败，恢复备份..."
        cp "$BACKUP_FILE" "$NGINX_CONF"
        exit 1
    fi
else
    echo "✗ Nginx 配置语法错误，恢复备份..."
    cp "$BACKUP_FILE" "$NGINX_CONF"
    exit 1
fi

echo ""
echo "========================================"
echo "  Nginx 配置更新完成"
echo "========================================"
echo ""
echo "测试识别服务代理："
echo "  curl http://localhost/api/recognize/health"
echo ""
