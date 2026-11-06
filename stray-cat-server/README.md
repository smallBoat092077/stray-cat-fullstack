# 流浪猫喂养小程序 - 后端服务

## 项目简介

流浪猫喂养小程序后端服务，基于 Spring Boot + MyBatis-Plus + Redis 构建。

## 技术栈

- **框架**: Spring Boot 3.2.0
- **ORM**: MyBatis-Plus 3.5.5
- **数据库**: MySQL 8.0
- **缓存**: Redis 7.x + Redisson
- **API文档**: Knife4j (Swagger)
- **工具**: Hutool, Lombok, Fastjson2

## 项目结构

```
stray-cat-server/
├── src/main/java/com/straycat/
│   ├── config/           # 配置类
│   ├── common/           # 公共类
│   ├── entity/           # 实体类
│   ├── mapper/           # Mapper接口
│   ├── service/          # 服务接口
│   ├── service/impl/     # 服务实现
│   ├── controller/       # 控制器
│   ├── dto/              # 数据传输对象
│   ├── vo/               # 视图对象
│   ├── enums/            # 枚举
│   ├── exception/        # 异常处理
│   ├── interceptor/      # 拦截器
│   ├── task/             # 定时任务
│   └── utils/            # 工具类
├── src/main/resources/
│   ├── mapper/           # MyBatis XML
│   └── application.yml   # 配置文件
└── sql/
    └── init.sql          # 数据库初始化脚本
```

## 快速开始

### 1. 初始化数据库

```bash
# 连接MySQL
mysql -u root -p

# 执行初始化脚本
source sql/init.sql
```

### 2. 配置Redis

Redis运行在虚拟机Docker容器中：
- 地址: 192.168.156.132
- 端口: 6379
- 密码: caozhou092077

### 3. 修改配置

编辑 `src/main/resources/application.yml`：
- 修改MySQL连接信息（如果不在本机）
- Redis配置已预设

### 4. 启动项目

```bash
# 使用Maven启动
mvn spring-boot:run

# 或者打包后运行
mvn clean package
java -jar target/stray-cat-server-1.0.0.jar
```

### 5. 访问API文档

启动后访问: http://localhost:8080/api/doc.html

## 核心功能

### 用户模块
- 微信登录
- 用户信息管理
- 角色管理（普通用户/志愿者/领养人/管理员）

### 猫咪模块
- 猫咪档案CRUD
- 流浪猫上报
- 附近猫咪查询（基于Redis GEO）
- 猫咪状态流转

### 投喂模块
- 投喂任务创建
- 志愿者接单
- GPS打卡（距离校验）
- 投喂记录

### 领养模块
- 领养申请
- 审核流程
- 回访管理

## API接口

### 用户相关
- `POST /api/user/login` - 微信登录
- `GET /api/user/info` - 获取用户信息
- `PUT /api/user/info` - 更新用户信息

### 猫咪相关
- `GET /api/cat/list` - 猫咪列表
- `GET /api/cat/{id}` - 猫咪详情
- `POST /api/cat/report` - 上报流浪猫
- `GET /api/cat/nearby` - 附近猫咪

### 投喂相关
- `POST /api/feeding/task` - 创建投喂任务
- `POST /api/feeding/task/{taskId}/accept` - 接单
- `POST /api/feeding/task/{taskId}/checkin` - 打卡
- `GET /api/feeding/task/list` - 任务列表

## 开发规范

1. **命名规范**
   - 类名: 大驼峰 (PascalCase)
   - 方法名: 小驼峰 (camelCase)
   - 常量: 全大写+下划线 (UPPER_SNAKE_CASE)

2. **代码规范**
   - 使用Lombok简化代码
   - 统一返回Result封装
   - 异常统一处理

3. **Git规范**
   - feat: 新功能
   - fix: 修复bug
   - docs: 文档更新
   - refactor: 重构

## 注意事项

1. 首次运行需要执行 `sql/init.sql` 初始化数据库
2. 确保Redis服务正常运行
3. 微信登录需要配置小程序的AppID和AppSecret
4. 生产环境请修改默认密码和密钥

## 后续开发计划

- [ ] 完善微信支付功能
- [ ] 添加订阅消息推送
- [ ] 实现共同领养功能
- [ ] 添加志愿者等级体系
- [ ] 完善管理后台接口
