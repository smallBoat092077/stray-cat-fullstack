# SIT 环境快速部署指南

## 前置条件

- 已安装 Docker 和 Docker Compose
- SSH 密钥已配置到服务器 `47.97.253.32`
- 微信 APPID、SECRET、腾讯地图 Key 等凭据已准备好

## 一键初始化（推荐）

在本地执行：

```bash
cd stray-cat-deploy
chmod +x prepare-sit-server.sh
bash prepare-sit-server.sh
```

该脚本会自动：
1. 测试 SSH 连接
2. 创建部署目录 `/opt/stray-cat-sit`
3. 克隆代码仓库
4. 提示配置文件准备

## 手动部署步骤

### 1. SSH 登录服务器

```bash
ssh root@47.97.253.32
```

### 2. 创建目录并克隆代码

```bash
mkdir -p /opt/stray-cat-sit
cd /opt/stray-cat-sit
git clone https://github.com/smallBoat092077/stray-cat-fullstack.git .
```

### 3. 准备配置文件

#### 创建 `.env` 文件

```bash
cp stray-cat-deploy/.env.example .env
vi .env
```

填入以下内容（**修改密码**）：

```env
MYSQL_ROOT_PASSWORD=StrayCat2026!Root
MYSQL_PASSWORD=StrayCat2026!DB
REDIS_PASSWORD=StrayCat2026!Redis
```

#### 创建 `app.env` 文件

```bash
cp stray-cat-deploy/app.env.example app.env
vi app.env
```

填入以下内容（**替换真实凭据**）：

```env
WECHAT_APPID=wx3f05ea02cf194e9a
WECHAT_SECRET=你的微信小程序Secret
TENCENT_MAP_KEY=你的腾讯位置服务Key
XPAY_OFFER_ID=1450691900
XPAY_APP_KEY=你的虚拟支付AppKey
XPAY_PUSH_TOKEN=straycat2026payczone
CONTENT_SECURITY_ENABLED=false
SUBSCRIBE_TEMPLATE_FEEDING=你的订阅消息模板ID
SUBSCRIBE_TEMPLATE_CHECKIN=你的订阅消息模板ID
RECOGNIZER_BASE_URL=http://recognizer:8000
RECOGNIZER_MATCH_THRESHOLD=0.85
RECOGNIZER_MANUAL_THRESHOLD=0.70
```

### 4. 创建 Nginx 配置

```bash
mkdir -p nginx/ssl nginx/webroot
vi nginx/default.conf
```

填入：

```nginx
server {
    listen 8083;
    server_name localhost;

    # API 反向代理
    location /api/ {
        proxy_pass http://app:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 静态文件
    location / {
        root /var/www/html;
        try_files $uri $uri/ /index.html;
    }
}
```

### 5. 执行部署脚本

```bash
chmod +x deploy-sit.sh
./deploy-sit.sh
```

脚本会自动：
- 拉取最新代码
- 构建 Python 识别服务镜像
- 启动 MySQL、Redis、Recognizer、App、Nginx
- 执行健康检查

### 6. 验证服务

部署完成后，检查输出中的健康检查结果。

手动验证：

```bash
# Spring Boot API
curl http://localhost:8082/api/actuator/health

# Python 识别服务
curl http://localhost:8002/health

# Nginx
curl -I http://localhost:8083/
```

查看日志：

```bash
# Spring Boot
docker-compose -f docker-compose-sit.yml logs -f app

# Python 识别服务
docker-compose -f docker-compose-sit.yml logs -f recognizer

# Nginx
docker-compose -f docker-compose-sit.yml logs -f web
```

## 端口映射（SIT 环境）

| 服务 | 宿主机端口 | 容器端口 | 说明 |
|------|-----------|---------|------|
| MySQL | 3307 | 3306 | 数据库 |
| Redis | 6380 | 6379 | 缓存 |
| Spring Boot | 8082 | 8080 | 后端 API |
| Nginx | 8083 | 80 | 前端 |
| Recognizer | 8002 | 8000 | Python 识别服务 |

## 小程序端配置

修改 `stray-cat-miniapp/app.js` 中的 `baseUrl`：

```javascript
globalData: {
  baseUrl: 'http://47.97.253.32:8082/api',  // SIT 环境
  // baseUrl: 'https://straycat.fun/api',   // 生产环境（注释掉）
}
```

然后在微信开发者工具中：
1. 编译项目
2. 真机调试（扫码）
3. 测试猫咪识别功能

## 常用命令

```bash
# 重启服务
docker-compose -f docker-compose-sit.yml restart app
docker-compose -f docker-compose-sit.yml restart recognizer

# 停止所有服务
docker-compose -f docker-compose-sit.yml down

# 查看运行状态
docker-compose -f docker-compose-sit.yml ps

# 进入容器
docker exec -it straycat-app-sit bash
docker exec -it straycat-recognizer-sit bash
```

## 故障排查

### Spring Boot 启动失败

```bash
# 查看日志
docker-compose -f docker-compose-sit.yml logs app

# 常见原因：
# 1. MySQL 未就绪 -> 等待 healthcheck 完成
# 2. 配置文件错误 -> 检查 .env 和 app.env
# 3. 端口冲突 -> netstat -tlnp | grep 8082
```

### Python 识别服务失败

```bash
# 查看日志
docker-compose -f docker-compose-sit.yml logs recognizer

# 常见原因：
# 1. 依赖安装失败 -> 检查 requirements.txt
# 2. OpenCV 导入错误 -> 确认使用 opencv-python-headless
# 3. FAISS CPU 版本不匹配 -> 重新构建镜像
```

### 数据库连接失败

```bash
# 检查 MySQL 是否运行
docker ps | grep mysql

# 测试连接
mysql -h 47.97.253.32 -P 3307 -u straycat -p

# 初始化脚本
ls -l /opt/stray-cat-sit/init/
```

## 下一步

SIT 环境验证通过后：

1. 测试猫咪识别打卡流程
2. 验证自动匹配（≥85%）、手动确认（70-85%）、新猫注册（<70%）
3. 确认位置变更追踪功能
4. 准备生产环境部署（47.95.149.170）
