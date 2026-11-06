#!/bin/bash
# SIT 服务器初始化脚本（在本地执行，通过 SSH 远程操作）
# 使用方法: bash prepare-sit-server.sh

set -e

SERVER="47.97.253.32"
REMOTE_DIR="/opt/stray-cat-sit"
REPO_URL="https://github.com/smallBoat092077/stray-cat-fullstack.git"

echo "=========================================="
echo "  SIT 服务器初始化向导"
echo "  目标服务器: $SERVER"
echo "=========================================="
echo ""

# 检查 SSH 连接
echo "[1/5] 测试 SSH 连接..."
ssh root@$SERVER "echo 'SSH 连接成功'" || {
    echo "❌ SSH 连接失败，请检查:"
    echo "   1. 服务器是否运行"
    echo "   2. SSH 密钥是否配置 (ssh-copy-id root@$SERVER)"
    exit 1
}

# 创建目录
echo "[2/5] 创建部署目录..."
ssh root@$SERVER "mkdir -p $REMOTE_DIR"

# 克隆或更新代码
echo "[3/5] 克隆代码仓库..."
ssh root@$SERVER "cd $REMOTE_DIR && (git status >/dev/null 2>&1 || git clone $REPO_URL . || true)"

# 提示配置文件
echo "[4/5] 配置文件准备提醒"
echo ""
echo "⚠️  请在服务器上执行以下命令创建配置文件："
echo ""
echo "  cd $REMOTE_DIR"
echo "  cp stray-cat-deploy/.env.example .env"
echo "  cp stray-cat-deploy/app.env.example app.env"
echo "  vi .env      # 编辑数据库密码等"
echo "  vi app.env   # 编辑微信 APPID、SECRET 等"
echo ""
echo "需要我帮你生成配置文件的示例内容吗？(y/n)"
read -r answer
if [[ "$answer" == "y" ]]; then
    echo ""
    echo "=== .env 文件内容 ==="
    cat <<'EOF'
MYSQL_ROOT_PASSWORD=your_mysql_root_password_here
MYSQL_PASSWORD=your_straycat_db_password_here
REDIS_PASSWORD=your_redis_password_here
EOF
    echo ""
    echo "=== app.env 文件内容 ==="
    cat <<'EOF'
WECHAT_APPID=wx3f05ea02cf194e9a
WECHAT_SECRET=your_wechat_app_secret_here
TENCENT_MAP_KEY=your_tencent_map_key_here
XPAY_OFFER_ID=1450691900
XPAY_APP_KEY=your_xpay_app_key_here
XPAY_PUSH_TOKEN=straycat2026payczone
CONTENT_SECURITY_ENABLED=false
SUBSCRIBE_TEMPLATE_FEEDING=your_template_id_here
SUBSCRIBE_TEMPLATE_CHECKIN=your_template_id_here
RECOGNIZER_BASE_URL=http://recognizer:8000
RECOGNIZER_MATCH_THRESHOLD=0.85
RECOGNIZER_MANUAL_THRESHOLD=0.70
EOF
fi

# 创建 Nginx 目录
echo "[5/5] 创建 Nginx 目录结构..."
ssh root@$SERVER "mkdir -p $REMOTE_DIR/nginx/ssl $REMOTE_DIR/nginx/webroot"

# 提示创建 Nginx 配置
echo ""
echo "⚠️  请在服务器上创建 Nginx 配置文件："
echo ""
echo "  vi $REMOTE_DIR/nginx/default.conf"
echo ""
echo "参考配置内容（SIT 环境，无 HTTPS）："
cat <<'EOF'
server {
    listen 8083;
    server_name localhost;

    location /api/ {
        proxy_pass http://app:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        root /var/www/html;
        try_files $uri $uri/ /index.html;
    }
}
EOF

echo ""
echo "=========================================="
echo "  初始化完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo "1. SSH 到服务器: ssh root@$SERVER"
echo "2. 进入目录: cd $REMOTE_DIR"
echo "3. 编辑 .env 和 app.env 填入真实凭据"
echo "4. 创建 nginx/default.conf"
echo "5. 执行部署: chmod +x deploy-sit.sh && ./deploy-sit.sh"
echo ""
