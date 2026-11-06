-- 流浪猫喂养小程序数据库初始化脚本

CREATE DATABASE IF NOT EXISTS stray_cat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE stray_cat;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) UNIQUE COMMENT '登录账号',
    password VARCHAR(100) COMMENT 'BCrypt密码哈希',
    openid VARCHAR(64) UNIQUE COMMENT '微信openid',
    session_key VARCHAR(128) COMMENT '微信session_key',
    union_id VARCHAR(64) COMMENT '微信unionid',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar_url VARCHAR(255) COMMENT '头像',
    phone VARCHAR(20) COMMENT '手机号',
    role TINYINT DEFAULT 0 COMMENT '角色: 0普通用户 1志愿者 2领养人 3管理员',
    credit_score INT DEFAULT 100 COMMENT '信用积分',
    coin_balance INT NOT NULL DEFAULT 0 COMMENT '猫粮余额(站内虚拟积分,无现金价值)',
    real_name VARCHAR(50) COMMENT '真实姓名',
    id_card VARCHAR(20) COMMENT '身份证号',
    status TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1正常',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_openid (openid),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 猫咪档案表
CREATE TABLE IF NOT EXISTS cat_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '猫咪ID',
    name VARCHAR(50) COMMENT '猫咪昵称',
    gender TINYINT COMMENT '性别: 0母 1公 2未知',
    age_estimate VARCHAR(20) COMMENT '年龄估算',
    breed VARCHAR(50) COMMENT '品种',
    color VARCHAR(50) COMMENT '毛色',
    personality VARCHAR(100) COMMENT '性格描述',
    health_status TINYINT DEFAULT 0 COMMENT '健康状态: 0未检查 1已检查 2已绝育 3已疫苗 4已驱虫',
    life_status TINYINT DEFAULT 0 COMMENT '生活状态: 0流浪中 1投喂中 2待领养 3已领养 4领养回访中',
    latitude DECIMAL(10, 7) COMMENT '纬度',
    longitude DECIMAL(10, 7) COMMENT '经度',
    location_desc VARCHAR(200) COMMENT '位置描述',
    discoverer_id BIGINT COMMENT '发现人ID',
    caregiver_id BIGINT COMMENT '主要照顾者ID',
    adopter_id BIGINT COMMENT '领养人ID',
    description TEXT COMMENT '详细描述',
    cover_image VARCHAR(255) COMMENT '封面图',
    view_count INT DEFAULT 0 COMMENT '浏览次数',
    like_count INT DEFAULT 0 COMMENT '点赞次数',
    comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数（含回复，删除时同步 -1）',
    coin_total INT NOT NULL DEFAULT 0 COMMENT '累计收到的猫粮',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (life_status),
    INDEX idx_location (latitude, longitude),
    INDEX idx_adopter (adopter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪档案表';

-- 投喂任务表
CREATE TABLE IF NOT EXISTS feeding_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
    cat_id BIGINT NOT NULL COMMENT '猫咪ID',
    task_type TINYINT DEFAULT 0 COMMENT '类型: 0日常投喂 1紧急投喂 2医疗救助',
    status TINYINT DEFAULT 0 COMMENT '状态: 0待接单 1已接单 2已投喂待审核 3已完成 4已取消',
    volunteer_id BIGINT COMMENT '志愿者ID',
    sponsor_id BIGINT COMMENT '认捐人ID',
    tip_id BIGINT COMMENT '来源打赏记录ID，人工派单时为空',
    amount DECIMAL(10, 2) COMMENT '认捐猫粮数量',
    food_type VARCHAR(50) COMMENT '猫粮类型',
    food_quantity INT COMMENT '猫粮数量',
    latitude DECIMAL(10, 7) COMMENT '投喂点纬度',
    longitude DECIMAL(10, 7) COMMENT '投喂点经度',
    radius INT DEFAULT 100 COMMENT '打卡有效半径(米)',
    deadline DATETIME COMMENT '任务截止时间',
    completed_at DATETIME COMMENT '完成时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_cat_id (cat_id),
    INDEX idx_status (status),
    INDEX idx_volunteer (volunteer_id),
    INDEX idx_sponsor (sponsor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投喂任务表';

-- 投喂记录表
CREATE TABLE IF NOT EXISTS feeding_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    cat_id BIGINT NOT NULL COMMENT '猫咪ID',
    volunteer_id BIGINT NOT NULL COMMENT '志愿者ID',
    check_in_time DATETIME COMMENT '打卡时间',
    check_in_latitude DECIMAL(10, 7) COMMENT '打卡纬度',
    check_in_longitude DECIMAL(10, 7) COMMENT '打卡经度',
    distance INT COMMENT '距离投喂点距离(米)',
    food_used VARCHAR(100) COMMENT '使用的猫粮',
    cat_condition TEXT COMMENT '猫咪状况描述',
    images VARCHAR(500) COMMENT '打卡照片，逗号分隔的 /uploads/ 相对路径',
    status TINYINT DEFAULT 0 COMMENT '审核状态: 0待审核 1已通过 2已拒绝',
    reviewer_id BIGINT COMMENT '审核人ID',
    review_comment VARCHAR(200) COMMENT '审核意见',
    reviewed_at DATETIME COMMENT '审核时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_cat_id (cat_id),
    INDEX idx_volunteer (volunteer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投喂记录表';

-- 领养申请表
CREATE TABLE IF NOT EXISTS adoption_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID',
    cat_id BIGINT NOT NULL COMMENT '猫咪ID',
    applicant_id BIGINT NOT NULL COMMENT '申请人ID',
    housing_type TINYINT COMMENT '住房类型: 0整租 1合租 2自有 3宿舍',
    housing_area DECIMAL(8, 2) COMMENT '住房面积(平米)',
    has_other_pets TINYINT DEFAULT 0 COMMENT '是否有其他宠物',
    cat_experience TEXT COMMENT '养猫经验',
    adoption_reason TEXT COMMENT '领养原因',
    family_agree TINYINT DEFAULT 0 COMMENT '家人是否同意',
    work_schedule VARCHAR(100) COMMENT '工作时间',
    contact_phone VARCHAR(20) COMMENT '联系手机号，必填',
    contact_wechat VARCHAR(50) COMMENT '微信号，选填',
    status TINYINT DEFAULT 0 COMMENT '状态: 0待审核 1审核中 2已通过 3已拒绝 4已签约 5已完成',
    reviewer_id BIGINT COMMENT '审核人ID',
    review_comment TEXT COMMENT '审核意见',
    reviewed_at DATETIME COMMENT '审核时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_cat_id (cat_id),
    INDEX idx_applicant (applicant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领养申请表';

-- 猫粮收支流水表
CREATE TABLE IF NOT EXISTS coin_transaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '流水ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    amount INT NOT NULL COMMENT '变动数量: 正数获得 负数支出',
    balance_after INT NOT NULL DEFAULT 0 COMMENT '变动后余额',
    biz_type VARCHAR(20) NOT NULL COMMENT '类型: REGISTER SIGNIN REPORT CHECKIN FOLLOWUP TIP',
    biz_id BIGINT NOT NULL DEFAULT 0 COMMENT '业务主键, 签到用yyyyMMdd',
    remark VARCHAR(100) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX uk_biz (user_id, biz_type, biz_id),
    INDEX idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫粮收支流水表';

-- 送猫粮(打赏)记录表
CREATE TABLE IF NOT EXISTS cat_tip (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '打赏ID',
    cat_id BIGINT NOT NULL COMMENT '猫咪ID',
    from_user_id BIGINT NOT NULL COMMENT '打赏人ID',
    coin_amount INT NOT NULL COMMENT '送出猫粮数量',
    message VARCHAR(200) COMMENT '留言',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_cat_time (cat_id, created_at),
    INDEX idx_from_user (from_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送猫粮记录表';

CREATE TABLE IF NOT EXISTS cat_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '点赞ID',
    cat_id BIGINT NOT NULL COMMENT '猫咪ID',
    user_id BIGINT NOT NULL COMMENT '点赞人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_cat_user (cat_id, user_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪点赞表';

-- 猫咪评论表：两级模型，parent_id=0 是顶级评论，回复一律指向所属顶级评论
CREATE TABLE IF NOT EXISTS cat_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    cat_id BIGINT NOT NULL COMMENT '猫咪ID',
    user_id BIGINT NOT NULL COMMENT '评论人ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=顶级评论，否则为所属顶级评论ID',
    reply_to_user_id BIGINT COMMENT '回复目标用户ID，用于「回复 @昵称」前缀与站内通知',
    content VARCHAR(200) NOT NULL COMMENT '评论内容',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_cat_time (cat_id, created_at),
    INDEX idx_parent (parent_id, created_at),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪评论表';

-- 插入测试数据
INSERT INTO sys_user (openid, nickname, role, status) VALUES
('test_openid_001', '测试用户1', 0, 1),
('test_openid_002', '志愿者张三', 1, 1),
('test_openid_003', '管理员', 3, 1);

INSERT INTO cat_profile (name, gender, age_estimate, color, health_status, life_status, latitude, longitude, location_desc, description) VALUES
('小花', 0, '2岁', '橘白相间', 2, 1, 31.230416, 121.473701, '上海市黄浦区南京东路', '性格温顺，喜欢被人摸'),
('咪咪', 1, '1岁', '黑色', 1, 0, 31.240416, 121.483701, '上海市黄浦区外滩', '比较怕生，需要耐心接近'),
('大黄', 1, '3岁', '黄色', 3, 2, 31.220416, 121.463701, '上海市黄浦区人民广场', '已绝育已疫苗，等待领养');
