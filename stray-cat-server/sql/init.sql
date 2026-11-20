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
    email VARCHAR(120) COMMENT '已验证安全邮箱',
    email_verified TINYINT DEFAULT 0 COMMENT '邮箱验证状态: 0未绑定 1已验证',
    role TINYINT DEFAULT 0 COMMENT '角色: 0普通用户 1志愿者 2领养人 3管理员',
    credit_score INT DEFAULT 100 COMMENT '信用积分',
    coin_balance INT NOT NULL DEFAULT 0 COMMENT '猫粮余额(站内虚拟积分,无现金价值)',
    real_name VARCHAR(50) COMMENT '真实姓名',
    id_card VARCHAR(20) COMMENT '身份证号',
    user_source VARCHAR(20) COMMENT '首触来源: mp/share/wxacode/offline/direct',
    source_sid VARCHAR(12) COMMENT '首触渠道短码',
    inviter_id BIGINT COMMENT '首触邀请人',
    status TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1正常',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_openid (openid),
    INDEX idx_role (role),
    INDEX idx_union_id (union_id),
    INDEX idx_user_source (user_source),
    INDEX idx_inviter (inviter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 渠道归因：公众号图文 / 微信分享卡片 / 小程序码 / 线下物料共用一个短码
CREATE TABLE IF NOT EXISTS share_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sid VARCHAR(12) NOT NULL COMMENT '对外短码',
    channel VARCHAR(20) NOT NULL COMMENT 'mp_article/mp_menu/mp_card/wxacode/share_card/offline',
    campaign VARCHAR(40) COMMENT '活动代号',
    inviter_id BIGINT COMMENT '分享人 userId，官方物料为 NULL',
    biz_type VARCHAR(20) COMMENT 'cat/feeding/adoption',
    biz_id BIGINT COMMENT '落地对象 id',
    landing_page VARCHAR(80) COMMENT '已发布页面路径，不带 / 与 query',
    owner_role TINYINT NOT NULL DEFAULT 0 COMMENT '0官方 1用户分享',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1正常',
    expire_at DATETIME COMMENT 'NULL 表示长期有效',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_sid (sid),
    INDEX idx_campaign (campaign),
    INDEX idx_inviter (inviter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道短码主表';

CREATE TABLE IF NOT EXISTS share_visit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sid VARCHAR(12) NOT NULL,
    visit_user_id BIGINT NOT NULL,
    is_new_user TINYINT NOT NULL DEFAULT 0,
    entry_type VARCHAR(16) COMMENT 'share_card/wxacode/mp_card/mp_article',
    scene_value VARCHAR(64) COMMENT '原始 scene，仅排查用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_sid_user (sid, visit_user_id),
    INDEX idx_created (created_at),
    INDEX idx_sid (sid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道归因明细';

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
    location_name VARCHAR(255) COMMENT '上报选点的真实地址（地点名错位修复）',
    province_code VARCHAR(12) COMMENT '上报时归属省（国标行政区码）',
    city_code VARCHAR(12) COMMENT '上报时归属市',
    district_code VARCHAR(12) COMMENT '上报时归属区',
    discoverer_id BIGINT COMMENT '发现人ID',
    caregiver_id BIGINT COMMENT '主要照顾者ID',
    adopter_id BIGINT COMMENT '领养人ID',
    description TEXT COMMENT '详细描述',
    cover_image VARCHAR(255) COMMENT '封面图',
    cover_phash BIGINT DEFAULT NULL COMMENT '封面感知哈希(dhash64)，上报去重用',
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
    matched_cat_id BIGINT COMMENT '识别候选猫ID（人工确认后，仅作参考，不决定归属）',
    similarity DECIMAL(5,4) COMMENT '最高候选相似度',
    recognize_status TINYINT DEFAULT 0 COMMENT '0未识别 1高置信确认 2人工确认 3低置信 4服务降级',
    recognized_at DATETIME COMMENT '识别时间',
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

-- 首页海报轮播
CREATE TABLE IF NOT EXISTS cat_poster (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '海报ID',
    image_url VARCHAR(255) NOT NULL COMMENT '海报图片URL',
    link_url VARCHAR(120) DEFAULT NULL COMMENT '点击跳转的小程序内页面路径',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页海报轮播表';

-- 营销活动：code 与 share_link.campaign 对齐，带参小程序码/分享落地 pages/campaign/index
CREATE TABLE IF NOT EXISTS campaign (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '活动ID',
    code VARCHAR(40) NOT NULL COMMENT '活动代号，与 share_link.campaign 对齐',
    name VARCHAR(60) NOT NULL COMMENT '活动名',
    description VARCHAR(500) COMMENT '活动介绍',
    cover_url VARCHAR(255) COMMENT '封面图相对路径',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0下线 1上线',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间，NULL 不限',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间，NULL 不限',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动表';

-- 插入测试数据
INSERT INTO sys_user (openid, nickname, role, status) VALUES
('test_openid_001', '测试用户1', 0, 1),
('test_openid_002', '志愿者张三', 1, 1),
('test_openid_003', '管理员', 3, 1);

INSERT INTO cat_profile (name, gender, age_estimate, color, health_status, life_status, latitude, longitude, location_desc, description) VALUES
('小花', 0, '2岁', '橘白相间', 2, 1, 31.230416, 121.473701, '上海市黄浦区南京东路', '性格温顺，喜欢被人摸'),
('咪咪', 1, '1岁', '黑色', 1, 0, 31.240416, 121.483701, '上海市黄浦区外滩', '比较怕生，需要耐心接近'),
('大黄', 1, '3岁', '黄色', 3, 2, 31.220416, 121.463701, '上海市黄浦区人民广场', '已绝育已疫苗，等待领养');

-- 首页内容位：热点轮播 + 系统通告统一内容表（方案 4.1）
CREATE TABLE IF NOT EXISTS home_content (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '内容ID',
    type VARCHAR(16) NOT NULL COMMENT 'HOTSPOT 热点 / NOTICE 通告',
    title VARCHAR(80) NOT NULL COMMENT '热点标题 / 通告标题',
    summary VARCHAR(200) COMMENT '副标题 / 通告正文摘要',
    cover_url VARCHAR(255) COMMENT '热点封面，NOTICE 可空',
    tag VARCHAR(20) COMMENT '角标文案，如 热点/公告',
    link_type VARCHAR(16) DEFAULT 'none' COMMENT 'campaign / cat / page / none',
    link_url VARCHAR(255) COMMENT '站内路径或业务参数，须过 LINK_PATTERN',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '升序；同序按 created_at 倒序',
    pinned TINYINT NOT NULL DEFAULT 0 COMMENT '通告置顶',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 下线 / 1 上线',
    start_time DATETIME DEFAULT NULL COMMENT '生效开始时间，NULL 不限',
    end_time DATETIME DEFAULT NULL COMMENT '生效结束时间，NULL 不限',
    pv INT NOT NULL DEFAULT 0 COMMENT '点击/阅读数',
    created_by BIGINT COMMENT '管理员 id',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_type_status (type, status, sort_order),
    INDEX idx_time (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页内容位（热点+系统通告）';

-- 寻猫启事（方案 4.2）。不设赏金/悬赏/支付字段，联系方式默认站内消息
CREATE TABLE IF NOT EXISTS lost_cat_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '启事ID',
    user_id BIGINT NOT NULL COMMENT '发布人',
    cat_name VARCHAR(40) COMMENT '猫咪名字',
    coat_desc VARCHAR(200) COMMENT '毛色 / 花纹描述（为二期花纹识别预留）',
    images VARCHAR(1000) COMMENT '逗号分隔 /uploads/...，最多 6 张',
    lost_at DATETIME COMMENT '丢失时间',
    latitude DECIMAL(10,7) COMMENT '丢失地点纬度',
    longitude DECIMAL(10,7) COMMENT '丢失地点经度',
    address_desc VARCHAR(200) COMMENT '地点文字',
    province_code VARCHAR(12) COMMENT '发布时归属省（国标行政区码）',
    city_code VARCHAR(12) COMMENT '发布时归属市',
    district_code VARCHAR(12) COMMENT '发布时归属区',
    contact_way VARCHAR(20) DEFAULT 'inapp' COMMENT 'inapp 站内消息 / phone 电话',
    contact_value VARCHAR(100) COMMENT '联系方式，公开展示前脱敏',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 寻找中 / 1 已找到 / 2 已关闭',
    audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待审 / 1 通过 / 2 驳回',
    audit_remark VARCHAR(200) COMMENT '驳回原因',
    reviewer_id BIGINT COMMENT '审核人',
    reviewed_at DATETIME COMMENT '审核时间',
    view_count INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status_audit (status, audit_status, created_at),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻猫启事';

-- 行政区域（国标，只读字典）。数据由管理端 /admin/region/sync 从腾讯 district API 拉取，与 DatabaseMigration 双写。
CREATE TABLE IF NOT EXISTS region (
    code VARCHAR(12) PRIMARY KEY COMMENT '国标行政区码，如 420100',
    name VARCHAR(50) NOT NULL COMMENT '名称，如 武汉市',
    parent_code VARCHAR(12) COMMENT '上级行政区码，省级为 NULL',
    level TINYINT NOT NULL COMMENT '1省 2市 3区',
    sort INT NOT NULL DEFAULT 0 COMMENT '同级排序',
    INDEX idx_parent (parent_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区域（国标，只读）';

-- 寻猫线索上报（二期 B，方案 4.2）：先审后发，与 DatabaseMigration.run() 末尾 DDL 双写。
-- 双方不暴露任何联系方式，表内不含手机号 / 微信号 / openid / userId 明细字段。
CREATE TABLE IF NOT EXISTS lost_cat_clue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '线索ID',
    post_id BIGINT NOT NULL COMMENT '关联 lost_cat_post.id',
    reporter_id BIGINT NOT NULL COMMENT '上报人',
    found_at DATETIME COMMENT '发现时间',
    latitude DECIMAL(10,7) COMMENT '发现地点纬度',
    longitude DECIMAL(10,7) COMMENT '发现地点经度',
    address_desc VARCHAR(200) COMMENT '地点文字',
    images VARCHAR(1000) COMMENT '逗号分隔 /uploads/...，最多 6 张',
    description VARCHAR(500) COMMENT '线索描述',
    audit_status TINYINT DEFAULT 0 COMMENT '0 待审 / 1 通过 / 2 驳回',
    audit_remark VARCHAR(200) COMMENT '驳回原因',
    reviewer_id BIGINT COMMENT '审核人',
    reviewed_at DATETIME COMMENT '审核时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_post_audit (post_id, audit_status, created_at),
    INDEX idx_reporter (reporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻猫线索';

-- 首页六宫格新增：爱猫搬家（转让信息 + 接猫申请）。手机号仅受控接口返回，不在列表/详情下发。
CREATE TABLE IF NOT EXISTS cat_transfer_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '转让信息ID',
    user_id BIGINT NOT NULL COMMENT '发布人',
    cat_name VARCHAR(40) COMMENT '猫咪名字',
    breed VARCHAR(40) COMMENT '品种',
    age_months INT COMMENT '月龄',
    gender VARCHAR(10) COMMENT '性别',
    neutered TINYINT NOT NULL DEFAULT 0 COMMENT '0未绝育 1已绝育',
    vaccinated TINYINT NOT NULL DEFAULT 0 COMMENT '0未疫苗 1已疫苗',
    health_note VARCHAR(300) COMMENT '健康情况说明',
    personality VARCHAR(300) COMMENT '性格描述',
    province_code VARCHAR(12) COMMENT '归属省',
    city_code VARCHAR(12) COMMENT '归属市',
    district_code VARCHAR(12) COMMENT '归属区',
    address_hint VARCHAR(100) COMMENT '模糊地点，不到门牌',
    reason VARCHAR(500) COMMENT '转让原因',
    requirement VARCHAR(500) COMMENT '领养要求',
    photos VARCHAR(1000) COMMENT '逗号分隔 /uploads/...，最多6张',
    contact_name VARCHAR(40) COMMENT '联系人称呼，公开接口不返回',
    contact_phone VARCHAR(20) COMMENT '联系手机号，公开接口不返回',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待审 1在架 2已转让 3已下架 4驳回',
    view_count INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
    apply_count INT NOT NULL DEFAULT 0 COMMENT '申请次数',
    reviewer_id BIGINT COMMENT '审核人',
    review_remark VARCHAR(200) COMMENT '审核意见',
    reviewed_at DATETIME COMMENT '审核时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status_created (status, created_at),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爱猫搬家-转让信息';

CREATE TABLE IF NOT EXISTS cat_transfer_apply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID',
    post_id BIGINT NOT NULL COMMENT '转让信息ID',
    applicant_id BIGINT NOT NULL COMMENT '申请人',
    apply_note VARCHAR(500) COMMENT '申请说明',
    contact_phone VARCHAR(20) COMMENT '申请人手机号，仅发布人可见',
    family_agree TINYINT COMMENT '家人是否同意',
    has_other_pets TINYINT COMMENT '是否已有其他宠物',
    promise_visit TINYINT COMMENT '是否同意回访',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已联系 2不合适',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    UNIQUE INDEX uk_post_user (post_id, applicant_id),
    INDEX idx_applicant (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爱猫搬家-接猫申请';

-- 首页六宫格新增：猫咪百科（题库 / 文章 / 测评记录）。与 DatabaseMigration 双写。
CREATE TABLE IF NOT EXISTS wiki_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '题目ID',
    category VARCHAR(20) NOT NULL COMMENT 'feeding/health/behavior/hygiene/firstaid',
    title VARCHAR(200) NOT NULL COMMENT '题干',
    options_json VARCHAR(500) COMMENT '选项JSON数组',
    answer_index INT COMMENT '正确答案原始下标',
    analysis VARCHAR(300) COMMENT '解析',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_category_status (category, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪百科-题库';

CREATE TABLE IF NOT EXISTS wiki_article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文章ID',
    title VARCHAR(120) NOT NULL COMMENT '标题',
    summary VARCHAR(300) COMMENT '摘要',
    content TEXT COMMENT '正文',
    cover_url VARCHAR(255) COMMENT '封面',
    category VARCHAR(20) COMMENT '分类',
    tags VARCHAR(200) COMMENT '逗号分隔标签',
    read_minutes INT NOT NULL DEFAULT 3 COMMENT '阅读分钟数',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1发布',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序',
    view_count INT NOT NULL DEFAULT 0 COMMENT '阅读数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category_status (category, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪百科-文章';

CREATE TABLE IF NOT EXISTS wiki_quiz_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '测评ID',
    user_id BIGINT NOT NULL COMMENT '答题用户',
    answers_json VARCHAR(1000) COMMENT '答案JSON',
    score INT NOT NULL DEFAULT 0 COMMENT '总分',
    weak_categories_json VARCHAR(300) COMMENT '薄弱维度JSON',
    recommended_json VARCHAR(300) COMMENT '推荐文章ID JSON',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    INDEX idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪百科-测评记录';

-- 后台用户新增/重置密码 + 小程序密码找回。与 DatabaseMigration 双写。
CREATE TABLE IF NOT EXISTS admin_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    operator_id BIGINT COMMENT '操作管理员id',
    operator_name VARCHAR(50) COMMENT '操作管理员账号名（冗余防改名）',
    action VARCHAR(30) NOT NULL COMMENT 'CREATE_USER / RESET_PASSWORD / RESET_REQUEST_HANDLE',
    target_user_id BIGINT COMMENT '目标用户id（找回申请场景为申请id）',
    target_username VARCHAR(50) COMMENT '目标用户账号',
    detail VARCHAR(300) COMMENT '备注，不含密码',
    ip VARCHAR(50) COMMENT '操作来源IP',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_time (created_at),
    INDEX idx_target (target_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台操作日志';

CREATE TABLE IF NOT EXISTS password_reset_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL COMMENT '用户填写的账号',
    nickname VARCHAR(50) COMMENT '用户填写的昵称',
    phone VARCHAR(20) NOT NULL COMMENT '预留手机号',
    reason VARCHAR(300) COMMENT '情况说明',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已重置 2已忽略',
    operator_id BIGINT COMMENT '处理管理员id',
    operator_name VARCHAR(50) COMMENT '处理管理员账号名',
    handled_at DATETIME COMMENT '处理时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    ip VARCHAR(50) COMMENT '提交来源IP',
    INDEX idx_phone_created (phone, created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码找回申请';

-- 账号安全：阶梯锁定状态 + 安全事件审计。与 DatabaseMigration.java 双写。
CREATE TABLE IF NOT EXISTS user_login_security (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT COMMENT '用户id（账号不存在时为空）',
    username VARCHAR(50) NOT NULL COMMENT '规范化账号',
    failed_count INT DEFAULT 0 COMMENT '当前周期连续失败次数',
    lock_stage TINYINT DEFAULT 0 COMMENT '0正常 1临时锁 2永久锁',
    lock_until DATETIME COMMENT '临时锁到期时间；永久锁为NULL',
    first_failed_at DATETIME COMMENT '本周期首次失败时间',
    last_failed_at DATETIME COMMENT '本周期末次失败时间',
    unlocked_at DATETIME COMMENT '最近一次解锁时间（管理员或24小时到期）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    KEY idx_user (user_id),
    KEY idx_stage (lock_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录安全状态（阶梯锁定）';

CREATE TABLE IF NOT EXISTS user_security_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT COMMENT '用户id',
    username VARCHAR(50) COMMENT '账号（冗余）',
    event_type VARCHAR(30) NOT NULL COMMENT 'PWD_CHANGE/TEMP_LOCK/PERMANENT_LOCK/ADMIN_UNLOCK/EMAIL_CODE_SEND/EMAIL_BIND/EMAIL_UNBIND/ADMIN_PWD_RESET',
    detail VARCHAR(300) COMMENT '备注（绝不记录密码与验证码）',
    ip VARCHAR(50) COMMENT '来源IP',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    KEY idx_user_time (user_id, created_at),
    KEY idx_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号安全事件审计';

CREATE TABLE IF NOT EXISTS app_about (
    id BIGINT PRIMARY KEY COMMENT '恒为1，单行配置',
    app_name VARCHAR(50) NOT NULL COMMENT '应用名',
    version VARCHAR(20) NOT NULL COMMENT '当前版本号',
    slogan VARCHAR(100) COMMENT '品牌slogan',
    motto_main VARCHAR(200) COMMENT '理念主句',
    motto_sub VARCHAR(200) COMMENT '理念副句',
    phone VARCHAR(20) COMMENT '管理员电话（点击拨打）',
    email VARCHAR(120) COMMENT '联系邮箱（点击复制）',
    contact_tip VARCHAR(300) COMMENT '联系时间提示',
    copyright VARCHAR(300) COMMENT '底部版权文案',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关于我们页面配置';

INSERT IGNORE INTO app_about (id, app_name, version, slogan, motto_main, motto_sub, phone, email, contact_tip, copyright)
VALUES (1, '猫遇猫舍', 'v1.1.0', '遇一只猫 · 安一个家', '让领养代替购买\n爱不流浪', '遇一只猫，安一个家',
        '17671710525', '1343076057@qq.com', '如有领养咨询、流浪猫求助或合作意向，欢迎在工作时间（9:00–21:00）与我们联系。',
        '猫遇猫舍 · 用心善待每一只小猫\n© 2026 CatMeet Cattery');
