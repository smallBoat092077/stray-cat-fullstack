# 花纹识别服务部署指南

## 前置条件

1. **服务器**: 47.95.149.170 (Ubuntu 22.04)
2. **Docker Compose**: 已安装
3. **Git**: 已配置 SSH key
4. **MySQL**: 已有 `stray_cat` 数据库

## 快速部署（推荐）

### 方式一：一键部署脚本

```bash
# SSH 到服务器
ssh root@47.95.149.170

# 进入部署目录
cd /opt/stray-cat

# 拉取最新代码
git pull origin main

# 执行一键部署
chmod +x deploy-all.sh
./deploy-all.sh
```

脚本会自动完成：
- ✓ 拉取最新代码
- ✓ 备份旧 jar
- ✓ 构建 Docker 镜像
- ✓ 启动所有服务（MySQL、Redis、Spring Boot、Python 识别、Nginx）
- ✓ 健康检查

### 方式二：手动部署

#### 1. 拉取代码

```bash
cd /opt/stray-cat
git pull origin main
```

#### 2. 构建并启动 Python 识别服务

```bash
docker-compose build recognizer
docker-compose up -d recognizer
```

#### 3. 更新 Nginx 配置

```bash
# 自动更新（推荐）
chmod +x update-nginx.sh
./update-nginx.sh

# 或手动添加以下内容到 /opt/stray-cat/nginx/default.conf
# 在最后一个 location 块的 } 前插入：

location /api/recognize/ {
    proxy_pass http://recognizer:8000/api/recognize/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 10m;
    proxy_connect_timeout 10s;
    proxy_read_timeout 30s;
    proxy_send_timeout 30s;
}

# 重载 Nginx
docker exec straycat-web nginx -s reload
```

#### 4. 重新构建并启动 Spring Boot

```bash
# 本地构建
cd stray-cat-server
mvn clean package -DskipTests

# 上传 jar 到服务器
scp target/stray-cat-server-1.0.0.jar root@47.95.149.170:/opt/stray-cat/app/

# SSH 到服务器重启
ssh root@47.95.149.170
cd /opt/stray-cat
docker-compose restart app
```

## 验证部署

### 1. 测试 Python 识别服务

```bash
# 健康检查
curl http://localhost:8001/health

# 获取统计信息
curl http://localhost:8001/api/recognize/stats

# 使用测试脚本（需要准备猫照片）
chmod +x test-recognizer.sh
./test-recognizer.sh /path/to/cat.jpg
```

### 2. 测试 Spring Boot 集成

```bash
# 健康检查
curl http://localhost:8080/api/actuator/health

# 测试识别接口（需要登录 token）
curl -X POST \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "photo=@/path/to/cat.jpg" \
  http://localhost:8080/api/feeding/recognize
```

### 3. 测试 Nginx 代理

```bash
# 通过 Nginx 访问识别服务
curl http://localhost/api/recognize/health
```

### 4. 小程序端测试

1. 打开微信开发者工具
2. 真机调试（扫码）
3. 进入投喂任务详情页
4. 点击「拍照识别猫咪」
5. 选择一张猫的照片
6. 查看识别结果

## 常见问题

### 1. Python 服务启动失败

**症状**: `docker-compose logs -f recognizer` 显示错误

**解决**:
```bash
# 查看日志
docker-compose logs -f recognizer

# 常见原因：
# - MySQL 未就绪：等待 MySQL 启动后再启动 recognizer
# - 依赖缺失：重新构建镜像 docker-compose build recognizer
# - 端口冲突：检查 8001 端口是否被占用 lsof -i:8001
```

### 2. 识别接口返回 500

**可能原因**:
- MySQL 连接失败：检查 `.env` 中的数据库配置
- 特征提取失败：照片格式不支持或质量太差
- FAISS 索引为空：数据库中还没有特征向量

**排查**:
```bash
# 查看 Python 服务日志
docker-compose logs -f recognizer

# 检查数据库表是否存在
docker exec straycat-mysql mysql -ustray_cat -p stray_cat -e "SHOW TABLES LIKE 'cat_feature';"
```

### 3. Spring Boot 调用识别服务超时

**可能原因**:
- Python 服务未启动
- Nginx 代理配置错误
- 网络不通

**解决**:
```bash
# 检查 Python 服务状态
docker-compose ps recognizer

# 测试直连 Python 服务
curl http://localhost:8001/health

# 测试 Nginx 代理
curl http://localhost/api/recognize/health

# 检查 Spring Boot 配置
cat /opt/stray-cat/config/application.yml | grep recognizer
```

### 4. 识别准确率低

**优化建议**:
1. **增加训练数据**: 每只猫至少 5-10 张不同角度照片
2. **调整阈值**: 修改 `application.yml` 中的 `match-threshold`
3. **改进特征提取**: 后续可升级为深度学习模型

## 监控与维护

### 查看日志

```bash
# Python 识别服务
docker-compose logs -f recognizer

# Spring Boot
docker-compose logs -f app

# Nginx
docker-compose logs -f web

# 最近 100 行
docker-compose logs --tail=100 recognizer
```

### 重启服务

```bash
# 重启单个服务
docker-compose restart recognizer
docker-compose restart app

# 重启所有服务
docker-compose restart
```

### 备份数据

```bash
# 备份 MySQL
docker exec straycat-mysql mysqldump -ustray_cat -p stray_cat > backup_$(date +%Y%m%d).sql

# 备份特征向量表
docker exec straycat-mysql mysqldump -ustray_cat -p stray_cat cat_feature > cat_feature_backup.sql
```

### 性能监控

```bash
# 查看容器资源占用
docker stats

# 查看识别服务 QPS
# 需要在 Nginx 中启用 access_log，然后分析日志
tail -f /opt/stray-cat/nginx/logs/access.log | grep "/api/recognize/"
```

## 升级流程

### 小版本升级（Bug 修复）

```bash
cd /opt/stray-cat
git pull
./deploy-all.sh
```

### 大版本升级（架构变更）

1. **备份数据**
   ```bash
   docker exec straycat-mysql mysqldump -ustray_cat -p stray_cat > backup_before_upgrade.sql
   ```

2. **停止服务**
   ```bash
   docker-compose down
   ```

3. **拉取新代码**
   ```bash
   git pull
   ```

4. **重新构建**
   ```bash
   docker-compose build
   ```

5. **启动服务**
   ```bash
   docker-compose up -d
   ```

6. **验证**
   ```bash
   ./test-recognizer.sh
   ```

## 回滚方案

如果新版本有问题，可以快速回滚：

```bash
# 1. 停止当前服务
docker-compose down

# 2. 恢复旧 jar
cp backup/YYYYMMDD_HHMMSS/stray-cat-server-1.0.0.jar app/

# 3. 恢复旧代码
git checkout <old-commit-hash>

# 4. 重新启动
docker-compose up -d
```

## 联系支持

如遇问题，请提供以下信息：
1. 错误日志（`docker-compose logs`）
2. 请求参数和响应
3. 复现步骤

---

**最后更新**: 2026-11-07  
**维护者**: 曹舟
