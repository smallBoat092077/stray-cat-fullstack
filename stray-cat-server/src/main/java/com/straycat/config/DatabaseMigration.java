package com.straycat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Component
@Order(1)
public class DatabaseMigration implements ApplicationRunner {

    private final DataSource dataSource;

    public DatabaseMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN session_key VARCHAR(128) DEFAULT NULL");
            // 账号密码登录：username/password 列，openid 改为可空且唯一（账号用户没有 openid）
            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN username VARCHAR(50) DEFAULT NULL");
            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN password VARCHAR(100) DEFAULT NULL");
            execQuietly(stmt, "ALTER TABLE sys_user MODIFY openid VARCHAR(64) NULL");
            execQuietly(stmt, "ALTER TABLE sys_user ADD UNIQUE INDEX uk_username (username)");
            // 猫粮：站内虚拟积分，打赏走积分不走现金
            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN coin_balance INT NOT NULL DEFAULT 0");
            execQuietly(stmt, "ALTER TABLE cat_profile ADD COLUMN coin_total INT NOT NULL DEFAULT 0");
            // 账号安全：已验证的安全邮箱（重复列异常由 execQuietly 忽略）
            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN email VARCHAR(120) DEFAULT NULL");
            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN email_verified TINYINT DEFAULT 0");

        } catch (Exception e) {
            log.warn("sys_user 迁移异常: {}", e.getMessage());
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS adoption_follow_up (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  application_id BIGINT NOT NULL," +
                "  cat_id BIGINT," +
                "  adopter_id BIGINT," +
                "  cat_condition TEXT," +
                "  living_environment TEXT," +
                "  health_status VARCHAR(100)," +
                "  images VARCHAR(500)," +
                "  follow_up_type TINYINT DEFAULT 0," +
                "  status TINYINT DEFAULT 0," +
                "  remark TEXT," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_application (application_id)," +
                "  INDEX idx_adopter (adopter_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("迁移: adoption_follow_up 表已就绪");

        } catch (Exception e) {
            log.warn("adoption_follow_up 建表异常: {}", e.getMessage());
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS coin_transaction (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  user_id BIGINT NOT NULL," +
                "  amount INT NOT NULL," +
                "  balance_after INT NOT NULL DEFAULT 0," +
                "  biz_type VARCHAR(20) NOT NULL," +
                "  biz_id BIGINT NOT NULL DEFAULT 0," +
                "  remark VARCHAR(100)," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX uk_biz (user_id, biz_type, biz_id)," +
                "  INDEX idx_user_time (user_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS cat_tip (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  cat_id BIGINT NOT NULL," +
                "  from_user_id BIGINT NOT NULL," +
                "  coin_amount INT NOT NULL," +
                "  message VARCHAR(200)," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_cat_time (cat_id, created_at)," +
                "  INDEX idx_from_user (from_user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("迁移: coin_transaction / cat_tip 表已就绪");

        } catch (Exception e) {
            log.warn("猫粮相关建表异常: {}", e.getMessage());
        }

        try (Connection conn = dataSource.getConnection();
             Statement orderStmt = conn.createStatement()) {

            orderStmt.execute(
                "CREATE TABLE IF NOT EXISTS coin_recharge_order (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  user_id BIGINT NOT NULL," +
                "  openid VARCHAR(64) NOT NULL," +
                "  out_trade_no VARCHAR(32) NOT NULL," +
                "  product_id VARCHAR(64) NOT NULL," +
                "  coin_amount INT NOT NULL," +
                "  price_fen INT NOT NULL," +
                "  status TINYINT NOT NULL DEFAULT 0," +
                "  sign_data VARCHAR(500)," +
                "  wx_order_id VARCHAR(64)," +
                "  paid_at DATETIME," +
                "  delivered_at DATETIME," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX uk_out_trade_no (out_trade_no)," +
                "  INDEX idx_user_time (user_id, created_at)," +
                "  INDEX idx_status (status, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("迁移: coin_recharge_order 表已就绪");

        } catch (Exception e) {
            log.warn("充值订单建表异常: {}", e.getMessage());
        }

        // 打赏即委托投喂 + 领养申请联系方式
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            execQuietly(stmt, "ALTER TABLE feeding_task ADD COLUMN tip_id BIGINT DEFAULT NULL COMMENT '来源打赏记录ID'");
            execQuietly(stmt, "ALTER TABLE feeding_task ADD INDEX idx_sponsor (sponsor_id)");
            execQuietly(stmt, "ALTER TABLE adoption_application ADD COLUMN contact_phone VARCHAR(20) DEFAULT NULL");
            execQuietly(stmt, "ALTER TABLE adoption_application ADD COLUMN contact_wechat VARCHAR(50) DEFAULT NULL");

        } catch (Exception e) {
            log.warn("投喂/领养结构迁移异常: {}", e.getMessage());
        }

        // 消息中心：审核结果、接单、回访提醒都先落这张表，订阅消息只是它的补充
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS user_message (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  user_id BIGINT NOT NULL," +
                "  type VARCHAR(32) NOT NULL," +
                "  title VARCHAR(60) NOT NULL," +
                "  content VARCHAR(300) NOT NULL," +
                "  page VARCHAR(120)," +
                "  biz_id BIGINT," +
                "  read_flag TINYINT NOT NULL DEFAULT 0," +
                "  push_status TINYINT NOT NULL DEFAULT 0 COMMENT '0仅站内 1已推送 2无订阅额度 3推送失败 4未绑定微信'," +
                "  push_error VARCHAR(120)," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_user_read (user_id, read_flag, id)," +
                "  INDEX idx_user_time (user_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("迁移: user_message 表已就绪");

            // 老库补列：推送结果落在这里，消息中心据此提示「没收到微信通知」的原因
            execQuietly(stmt, "ALTER TABLE user_message ADD COLUMN push_status TINYINT NOT NULL DEFAULT 0 " +
                    "COMMENT '0仅站内 1已推送 2无订阅额度 3推送失败 4未绑定微信'");
            execQuietly(stmt, "ALTER TABLE user_message ADD COLUMN push_error VARCHAR(120) DEFAULT NULL");

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS cat_poster (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  image_url VARCHAR(255) NOT NULL," +
                "  sort_order INT NOT NULL DEFAULT 0," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("迁移: cat_poster 表已就绪");

        } catch (Exception e) {
            log.warn("user_message 建表异常: {}", e.getMessage());
        }

        // 共同领养：一张申请单最多 3 位共同领养人，uk 保证同一个人不会被重复邀请
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS adoption_co_adopter (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  application_id BIGINT NOT NULL," +
                "  cat_id BIGINT," +
                "  inviter_id BIGINT NOT NULL," +
                "  user_id BIGINT NOT NULL," +
                "  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待确认 1已同意 2已拒绝'," +
                "  message VARCHAR(200)," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  responded_at DATETIME," +
                "  UNIQUE INDEX uk_app_user (application_id, user_id)," +
                "  INDEX idx_user_status (user_id, status)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("迁移: adoption_co_adopter 表已就绪");

            // 志愿者等级：由已完成打卡数推导，存下来便于列表/排行榜直接读，升级时只增不减
            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN volunteer_level TINYINT NOT NULL DEFAULT 1");
            // 历史账号按已有打卡数回填一次（不回发升级奖励，避免上线瞬间集体爆猫粮）
            execQuietly(stmt,
                "UPDATE sys_user u JOIN (" +
                "  SELECT volunteer_id AS uid, COUNT(*) AS cnt FROM feeding_record" +
                "  WHERE status = 1 AND volunteer_id IS NOT NULL GROUP BY volunteer_id" +
                ") c ON c.uid = u.id SET u.volunteer_level = CASE" +
                "  WHEN c.cnt >= 100 THEN 6 WHEN c.cnt >= 60 THEN 5 WHEN c.cnt >= 30 THEN 4" +
                "  WHEN c.cnt >= 10 THEN 3 WHEN c.cnt >= 3 THEN 2 ELSE 1 END" +
                " WHERE u.volunteer_level < CASE" +
                "  WHEN c.cnt >= 100 THEN 6 WHEN c.cnt >= 60 THEN 5 WHEN c.cnt >= 30 THEN 4" +
                "  WHEN c.cnt >= 10 THEN 3 WHEN c.cnt >= 3 THEN 2 ELSE 1 END");

        } catch (Exception e) {
            log.warn("共同领养/志愿者等级迁移异常: {}", e.getMessage());
        }

        // 上报补充周边环境：环境照 + 环境描述；同时把用户上传的多张猫咪照片存全量，详情轮播才有图可翻
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            execQuietly(stmt, "ALTER TABLE cat_profile ADD COLUMN images VARCHAR(500) DEFAULT NULL COMMENT '猫咪照片，逗号分隔相对路径，最多3张'");
            execQuietly(stmt, "ALTER TABLE cat_profile ADD COLUMN environment_images VARCHAR(1000) DEFAULT NULL COMMENT '周边环境照片，逗号分隔相对路径，最多6张'");
            execQuietly(stmt, "ALTER TABLE cat_profile ADD COLUMN environment_desc VARCHAR(300) DEFAULT NULL COMMENT '周边环境描述'");
            // 历史数据只有 cover_image，补一份到 images，进入详情至少能翻到一张
            execQuietly(stmt, "UPDATE cat_profile SET images = cover_image WHERE (images IS NULL OR images = '') AND cover_image IS NOT NULL AND cover_image <> ''");

        } catch (Exception e) {
            log.warn("猫咪环境信息迁移异常: {}", e.getMessage());
        }
        // 用户反馈：建议/问题收集入口，图片存逗号分隔相对路径（与 cat_profile.images 一致）
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS feedback (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  user_id BIGINT NOT NULL," +
                "  type TINYINT NOT NULL DEFAULT 0 COMMENT '0功能建议 1问题反馈 2其他'," +
                "  content VARCHAR(500) NOT NULL," +
                "  images VARCHAR(500) COMMENT '截图相对路径，逗号分隔，最多3张'," +
                "  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已回复 2已忽略'," +
                "  reply VARCHAR(500)," +
                "  replied_at DATETIME," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_user_time (user_id, created_at)," +
                "  INDEX idx_status_time (status, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("迁移: feedback 表已就绪");

        } catch (Exception e) {
            log.warn("feedback 建表异常: {}", e.getMessage());
        }

        // P0 渠道归因：分享卡片 / 小程序码 / 公众号图文共用一个短码，首触来源只在用户创建时写
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN union_id VARCHAR(64) DEFAULT NULL");
            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN user_source VARCHAR(20) DEFAULT NULL COMMENT '首触来源'");
            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN source_sid VARCHAR(12) DEFAULT NULL COMMENT '首触短码'");
            execQuietly(stmt, "ALTER TABLE sys_user ADD COLUMN inviter_id BIGINT DEFAULT NULL COMMENT '首触邀请人'");
            execQuietly(stmt, "ALTER TABLE sys_user ADD INDEX idx_user_source (user_source)");
            execQuietly(stmt, "ALTER TABLE sys_user ADD INDEX idx_inviter (inviter_id)");
            execQuietly(stmt, "ALTER TABLE sys_user ADD INDEX idx_union_id (union_id)");

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS share_link (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  sid VARCHAR(12) NOT NULL," +
                "  channel VARCHAR(20) NOT NULL," +
                "  campaign VARCHAR(40) DEFAULT NULL," +
                "  inviter_id BIGINT DEFAULT NULL," +
                "  biz_type VARCHAR(20) DEFAULT NULL," +
                "  biz_id BIGINT DEFAULT NULL," +
                "  landing_page VARCHAR(80) DEFAULT NULL," +
                "  owner_role TINYINT NOT NULL DEFAULT 0," +
                "  status TINYINT NOT NULL DEFAULT 1," +
                "  expire_at DATETIME DEFAULT NULL," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX uk_sid (sid)," +
                "  INDEX idx_campaign (campaign)," +
                "  INDEX idx_inviter (inviter_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS share_visit (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  sid VARCHAR(12) NOT NULL," +
                "  visit_user_id BIGINT NOT NULL," +
                "  is_new_user TINYINT NOT NULL DEFAULT 0," +
                "  entry_type VARCHAR(16) DEFAULT NULL," +
                "  scene_value VARCHAR(64) DEFAULT NULL," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX uk_sid_user (sid, visit_user_id)," +
                "  INDEX idx_created (created_at)," +
                "  INDEX idx_sid (sid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("迁移: share_link / share_visit 表已就绪");

        } catch (Exception e) {
            log.warn("渠道归因迁移异常: {}", e.getMessage());
        }

        // P1：海报可点击跳转 + 营销活动表
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 只允许站内页面路径（外链校验在 Controller），空值 = 点击无动作
            execQuietly(stmt, "ALTER TABLE cat_poster ADD COLUMN link_url VARCHAR(120) DEFAULT NULL COMMENT '点击跳转的小程序内页面路径'");

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS campaign (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  code VARCHAR(40) NOT NULL COMMENT '活动代号，与 share_link.campaign 对齐'," +
                "  name VARCHAR(60) NOT NULL COMMENT '活动名'," +
                "  description VARCHAR(500) COMMENT '活动介绍'," +
                "  cover_url VARCHAR(255) COMMENT '封面图相对路径'," +
                "  status TINYINT NOT NULL DEFAULT 1 COMMENT '0下线 1上线'," +
                "  start_time DATETIME DEFAULT NULL COMMENT '开始时间，NULL 不限'," +
                "  end_time DATETIME DEFAULT NULL COMMENT '结束时间，NULL 不限'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE KEY uk_code (code)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动表'"
            );
            log.info("迁移: cat_poster.link_url / campaign 表已就绪");

        } catch (Exception e) {
            log.warn("海报链接/活动表迁移异常: {}", e.getMessage());
        }

        // 首页改造：热点/通告内容位 + 寻猫启事（方案 4）。IF NOT EXISTS 保证 UAT 启动即建表，
        // 与 sql/init.sql 末尾两条 DDL 保持一致，只做追加、不改动上面的既有迁移语句。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            execQuietly(stmt,
                "CREATE TABLE IF NOT EXISTS home_content (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '内容ID'," +
                "  type VARCHAR(16) NOT NULL COMMENT 'HOTSPOT 热点 / NOTICE 通告'," +
                "  title VARCHAR(80) NOT NULL COMMENT '热点标题 / 通告标题'," +
                "  summary VARCHAR(200) COMMENT '副标题 / 通告正文摘要'," +
                "  cover_url VARCHAR(255) COMMENT '热点封面，NOTICE 可空'," +
                "  tag VARCHAR(20) COMMENT '角标文案，如 热点/公告'," +
                "  link_type VARCHAR(16) DEFAULT 'none' COMMENT 'campaign / cat / page / none'," +
                "  link_url VARCHAR(255) COMMENT '站内路径或业务参数，须过 LINK_PATTERN'," +
                "  sort_order INT NOT NULL DEFAULT 0 COMMENT '升序；同序按 created_at 倒序'," +
                "  pinned TINYINT NOT NULL DEFAULT 0 COMMENT '通告置顶'," +
                "  status TINYINT NOT NULL DEFAULT 1 COMMENT '0 下线 / 1 上线'," +
                "  start_time DATETIME DEFAULT NULL COMMENT '生效开始时间，NULL 不限'," +
                "  end_time DATETIME DEFAULT NULL COMMENT '生效结束时间，NULL 不限'," +
                "  pv INT NOT NULL DEFAULT 0 COMMENT '点击/阅读数'," +
                "  created_by BIGINT COMMENT '管理员 id'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  INDEX idx_type_status (type, status, sort_order)," +
                "  INDEX idx_time (start_time, end_time)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页内容位（热点+系统通告）'"
            );
            execQuietly(stmt,
                "CREATE TABLE IF NOT EXISTS lost_cat_post (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '启事ID'," +
                "  user_id BIGINT NOT NULL COMMENT '发布人'," +
                "  cat_name VARCHAR(40) COMMENT '猫咪名字'," +
                "  coat_desc VARCHAR(200) COMMENT '毛色 / 花纹描述（为二期花纹识别预留）'," +
                "  images VARCHAR(1000) COMMENT '逗号分隔 /uploads/...，最多 6 张'," +
                "  lost_at DATETIME COMMENT '丢失时间'," +
                "  latitude DECIMAL(10,7) COMMENT '丢失地点纬度'," +
                "  longitude DECIMAL(10,7) COMMENT '丢失地点经度'," +
                "  address_desc VARCHAR(200) COMMENT '地点文字'," +
                "  contact_way VARCHAR(20) DEFAULT 'inapp' COMMENT 'inapp 站内消息 / phone 电话'," +
                "  contact_value VARCHAR(100) COMMENT '联系方式，公开展示前脱敏'," +
                "  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 寻找中 / 1 已找到 / 2 已关闭'," +
                "  audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待审 / 1 通过 / 2 驳回'," +
                "  audit_remark VARCHAR(200) COMMENT '驳回原因'," +
                "  reviewer_id BIGINT COMMENT '审核人'," +
                "  reviewed_at DATETIME COMMENT '审核时间'," +
                "  view_count INT NOT NULL DEFAULT 0 COMMENT '浏览次数'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  INDEX idx_status_audit (status, audit_status, created_at)," +
                "  INDEX idx_user (user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻猫启事'"
            );
            log.info("迁移: home_content / lost_cat_post 表已就绪");

        } catch (Exception e) {
            log.warn("首页改造建表异常: {}", e.getMessage());
        }

        // 花纹识别一期（方案 4.1）：feeding_record 4 个识别字段。
        // 与 sql/init.sql 建表语句双写；全走 execQuietly，已存在时靠 "Duplicate column" 吞掉，
        // 上面既有迁移语句一行不动。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            execQuietly(stmt, "ALTER TABLE feeding_record ADD COLUMN matched_cat_id BIGINT COMMENT '识别候选猫ID（人工确认后，仅作参考）'");
            execQuietly(stmt, "ALTER TABLE feeding_record ADD COLUMN similarity DECIMAL(5,4) COMMENT '最高候选相似度'");
            execQuietly(stmt, "ALTER TABLE feeding_record ADD COLUMN recognize_status TINYINT DEFAULT 0 COMMENT '0未识别 1高置信确认 2人工确认 3低置信 4服务降级'");
            execQuietly(stmt, "ALTER TABLE feeding_record ADD COLUMN recognized_at DATETIME COMMENT '识别时间'");
            log.info("迁移: feeding_record 识别字段已就绪");

        } catch (Exception e) {
            log.warn("feeding_record 识别字段迁移异常: {}", e.getMessage());
        }

        // 寻猫线索上报（二期 B，方案 4.2）：先审后发，与 sql/init.sql 末尾 DDL 双写，只做追加、不改上面既有语句
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS lost_cat_clue (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '线索ID'," +
                "  post_id BIGINT NOT NULL COMMENT '关联 lost_cat_post.id'," +
                "  reporter_id BIGINT NOT NULL COMMENT '上报人'," +
                "  found_at DATETIME COMMENT '发现时间'," +
                "  latitude DECIMAL(10,7) COMMENT '发现地点纬度'," +
                "  longitude DECIMAL(10,7) COMMENT '发现地点经度'," +
                "  address_desc VARCHAR(200) COMMENT '地点文字'," +
                "  images VARCHAR(1000) COMMENT '逗号分隔 /uploads/...，最多 6 张'," +
                "  description VARCHAR(500) COMMENT '线索描述'," +
                "  audit_status TINYINT DEFAULT 0 COMMENT '0 待审 / 1 通过 / 2 驳回'," +
                "  audit_remark VARCHAR(200) COMMENT '驳回原因'," +
                "  reviewer_id BIGINT COMMENT '审核人'," +
                "  reviewed_at DATETIME COMMENT '审核时间'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  INDEX idx_post_audit (post_id, audit_status, created_at)," +
                "  INDEX idx_reporter (reporter_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻猫线索'"
            );
            log.info("迁移: lost_cat_clue 表已就绪");

        } catch (Exception e) {
            log.warn("lost_cat_clue 建表异常: {}", e.getMessage());
        }

        // 行政区域（区域划分需求，功能设计 §1）：region 字典表 + 业务表省市区归属列
        // 数据来源为管理端 /admin/region/sync 从腾讯 district API 拉取（D1 只读字典），迁移只建结构
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS region (" +
                "  code VARCHAR(12) PRIMARY KEY COMMENT '国标行政区码，如 420100'," +
                "  name VARCHAR(50) NOT NULL COMMENT '名称，如 武汉市'," +
                "  parent_code VARCHAR(12) COMMENT '上级行政区码，省级为 NULL'," +
                "  level TINYINT NOT NULL COMMENT '1省 2市 3区'," +
                "  sort INT NOT NULL DEFAULT 0 COMMENT '同级排序'," +
                "  INDEX idx_parent (parent_code)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区域（国标，只读）'"
            );
            execQuietly(stmt, "ALTER TABLE cat_profile ADD COLUMN province_code VARCHAR(12) COMMENT '上报时归属省'");
            execQuietly(stmt, "ALTER TABLE cat_profile ADD COLUMN city_code VARCHAR(12) COMMENT '上报时归属市'");
            execQuietly(stmt, "ALTER TABLE cat_profile ADD COLUMN district_code VARCHAR(12) COMMENT '上报时归属区'");
            execQuietly(stmt, "ALTER TABLE lost_cat_post ADD COLUMN province_code VARCHAR(12) COMMENT '发布时归属省'");
            execQuietly(stmt, "ALTER TABLE lost_cat_post ADD COLUMN city_code VARCHAR(12) COMMENT '发布时归属市'");
            execQuietly(stmt, "ALTER TABLE lost_cat_post ADD COLUMN district_code VARCHAR(12) COMMENT '发布时归属区'");
            log.info("迁移: region 表与业务表区域列已就绪");

            // 猫咪真实地点名（地点名错位修复）：上报选点的真实地址，与 location_desc 手填描述分离
            execQuietly(stmt, "ALTER TABLE cat_profile ADD COLUMN location_name VARCHAR(255) COMMENT '上报选点的真实地址'");
            log.info("迁移: cat_profile.location_name 已就绪");

            // 上报去重：封面感知哈希（dhash64）。同一张照片重新上传（文件名/压缩不同）哈希几乎不变，
            // 与既有档案汉明距离 ≤8 判定为同一张图，拦截重复上报
            execQuietly(stmt, "ALTER TABLE cat_profile ADD COLUMN cover_phash BIGINT DEFAULT NULL COMMENT '封面感知哈希(dhash64)'");
            log.info("迁移: cat_profile.cover_phash 已就绪");

        } catch (Exception e) {
            log.warn("区域迁移异常: {}", e.getMessage());
        }

        // 首页六宫格新增功能：爱猫搬家 2 表 + 猫咪百科 3 表（工具箱一期无新表）。
        // 与 sql/init.sql 末尾 DDL 双写，只做追加、不改上面既有迁移语句。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            execQuietly(stmt,
                "CREATE TABLE IF NOT EXISTS cat_transfer_post (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '转让信息ID'," +
                "  user_id BIGINT NOT NULL COMMENT '发布人'," +
                "  cat_name VARCHAR(40) COMMENT '猫咪名字'," +
                "  breed VARCHAR(40) COMMENT '品种'," +
                "  age_months INT COMMENT '月龄'," +
                "  gender VARCHAR(10) COMMENT '性别'," +
                "  neutered TINYINT NOT NULL DEFAULT 0 COMMENT '0未绝育 1已绝育'," +
                "  vaccinated TINYINT NOT NULL DEFAULT 0 COMMENT '0未疫苗 1已疫苗'," +
                "  health_note VARCHAR(300) COMMENT '健康情况说明'," +
                "  personality VARCHAR(300) COMMENT '性格描述'," +
                "  province_code VARCHAR(12) COMMENT '归属省'," +
                "  city_code VARCHAR(12) COMMENT '归属市'," +
                "  district_code VARCHAR(12) COMMENT '归属区'," +
                "  address_hint VARCHAR(100) COMMENT '模糊地点，不到门牌'," +
                "  reason VARCHAR(500) COMMENT '转让原因'," +
                "  requirement VARCHAR(500) COMMENT '领养要求'," +
                "  photos VARCHAR(1000) COMMENT '逗号分隔 /uploads/...，最多6张'," +
                "  contact_name VARCHAR(40) COMMENT '联系人称呼，公开接口不返回'," +
                "  contact_phone VARCHAR(20) COMMENT '联系手机号，公开接口不返回'," +
                "  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待审 1在架 2已转让 3已下架 4驳回'," +
                "  view_count INT NOT NULL DEFAULT 0 COMMENT '浏览次数'," +
                "  apply_count INT NOT NULL DEFAULT 0 COMMENT '申请次数'," +
                "  reviewer_id BIGINT COMMENT '审核人'," +
                "  review_remark VARCHAR(200) COMMENT '审核意见'," +
                "  reviewed_at DATETIME COMMENT '审核时间'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  INDEX idx_status_created (status, created_at)," +
                "  INDEX idx_user (user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爱猫搬家-转让信息'"
            );
            execQuietly(stmt,
                "CREATE TABLE IF NOT EXISTS cat_transfer_apply (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID'," +
                "  post_id BIGINT NOT NULL COMMENT '转让信息ID'," +
                "  applicant_id BIGINT NOT NULL COMMENT '申请人'," +
                "  apply_note VARCHAR(500) COMMENT '申请说明'," +
                "  contact_phone VARCHAR(20) COMMENT '申请人手机号，仅发布人可见'," +
                "  family_agree TINYINT COMMENT '家人是否同意'," +
                "  has_other_pets TINYINT COMMENT '是否已有其他宠物'," +
                "  promise_visit TINYINT COMMENT '是否同意回访'," +
                "  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已联系 2不合适'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间'," +
                "  UNIQUE INDEX uk_post_user (post_id, applicant_id)," +
                "  INDEX idx_applicant (applicant_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爱猫搬家-接猫申请'"
            );
            execQuietly(stmt,
                "CREATE TABLE IF NOT EXISTS wiki_question (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '题目ID'," +
                "  category VARCHAR(20) NOT NULL COMMENT 'feeding/health/behavior/hygiene/firstaid'," +
                "  title VARCHAR(200) NOT NULL COMMENT '题干'," +
                "  options_json VARCHAR(500) COMMENT '选项JSON数组'," +
                "  answer_index INT COMMENT '正确答案原始下标'," +
                "  analysis VARCHAR(300) COMMENT '解析'," +
                "  sort INT NOT NULL DEFAULT 0 COMMENT '排序'," +
                "  status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1启用'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  INDEX idx_category_status (category, status, sort)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪百科-题库'"
            );
            execQuietly(stmt,
                "CREATE TABLE IF NOT EXISTS wiki_article (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文章ID'," +
                "  title VARCHAR(120) NOT NULL COMMENT '标题'," +
                "  summary VARCHAR(300) COMMENT '摘要'," +
                "  content TEXT COMMENT '正文'," +
                "  cover_url VARCHAR(255) COMMENT '封面'," +
                "  category VARCHAR(20) COMMENT '分类'," +
                "  tags VARCHAR(200) COMMENT '逗号分隔标签'," +
                "  read_minutes INT NOT NULL DEFAULT 3 COMMENT '阅读分钟数'," +
                "  status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1发布'," +
                "  sort INT NOT NULL DEFAULT 0 COMMENT '排序'," +
                "  view_count INT NOT NULL DEFAULT 0 COMMENT '阅读数'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  INDEX idx_category_status (category, status, sort)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪百科-文章'"
            );
            execQuietly(stmt,
                "CREATE TABLE IF NOT EXISTS wiki_quiz_result (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '测评ID'," +
                "  user_id BIGINT NOT NULL COMMENT '答题用户'," +
                "  answers_json VARCHAR(1000) COMMENT '答案JSON'," +
                "  score INT NOT NULL DEFAULT 0 COMMENT '总分'," +
                "  weak_categories_json VARCHAR(300) COMMENT '薄弱维度JSON'," +
                "  recommended_json VARCHAR(300) COMMENT '推荐文章ID JSON'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间'," +
                "  INDEX idx_user_time (user_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猫咪百科-测评记录'"
            );
            log.info("迁移: 爱猫搬家/猫咪百科 5 张表已就绪");

        } catch (Exception e) {
            log.warn("爱猫搬家/猫咪百科建表异常: {}", e.getMessage());
        }

        // 后台用户管理：操作日志 + 密码找回申请。与 sql/init.sql 末尾 DDL 双写，只做追加。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS admin_operation_log (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                "  operator_id BIGINT COMMENT '操作管理员id'," +
                "  operator_name VARCHAR(50) COMMENT '操作管理员账号名（冗余防改名）'," +
                "  action VARCHAR(30) NOT NULL COMMENT 'CREATE_USER / RESET_PASSWORD / RESET_REQUEST_HANDLE'," +
                "  target_user_id BIGINT COMMENT '目标用户id（找回申请场景为申请id）'," +
                "  target_username VARCHAR(50) COMMENT '目标用户账号'," +
                "  detail VARCHAR(300) COMMENT '备注，不含密码'," +
                "  ip VARCHAR(50) COMMENT '操作来源IP'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'," +
                "  INDEX idx_time (created_at)," +
                "  INDEX idx_target (target_user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台操作日志'"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS password_reset_request (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                "  username VARCHAR(50) NOT NULL COMMENT '用户填写的账号'," +
                "  nickname VARCHAR(50) COMMENT '用户填写的昵称'," +
                "  phone VARCHAR(20) NOT NULL COMMENT '预留手机号'," +
                "  reason VARCHAR(300) COMMENT '情况说明'," +
                "  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已重置 2已忽略'," +
                "  operator_id BIGINT COMMENT '处理管理员id'," +
                "  operator_name VARCHAR(50) COMMENT '处理管理员账号名'," +
                "  handled_at DATETIME COMMENT '处理时间'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间'," +
                "  ip VARCHAR(50) COMMENT '提交来源IP'," +
                "  INDEX idx_phone_created (phone, created_at)," +
                "  INDEX idx_status (status)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码找回申请'"
            );
            log.info("迁移: admin_operation_log / password_reset_request 表已就绪");

        } catch (Exception e) {
            log.warn("后台用户管理建表异常: {}", e.getMessage());
        }

        // 账号安全：阶梯锁定状态 + 安全事件审计。与 sql/init.sql 双写，只做追加。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS user_login_security (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                "  user_id BIGINT COMMENT '用户id（账号不存在时为空）'," +
                "  username VARCHAR(50) NOT NULL COMMENT '规范化账号'," +
                "  failed_count INT DEFAULT 0 COMMENT '当前周期连续失败次数'," +
                "  lock_stage TINYINT DEFAULT 0 COMMENT '0正常 1临时锁 2永久锁'," +
                "  lock_until DATETIME COMMENT '临时锁到期时间；永久锁为NULL'," +
                "  first_failed_at DATETIME COMMENT '本周期首次失败时间'," +
                "  last_failed_at DATETIME COMMENT '本周期末次失败时间'," +
                "  unlocked_at DATETIME COMMENT '最近一次解锁时间（管理员或24小时到期）'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  UNIQUE KEY uk_username (username)," +
                "  KEY idx_user (user_id)," +
                "  KEY idx_stage (lock_stage)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录安全状态（阶梯锁定）'"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS user_security_event (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                "  user_id BIGINT COMMENT '用户id'," +
                "  username VARCHAR(50) COMMENT '账号（冗余）'," +
                "  event_type VARCHAR(30) NOT NULL COMMENT '事件类型'," +
                "  detail VARCHAR(300) COMMENT '备注（绝不记录密码与验证码）'," +
                "  ip VARCHAR(50) COMMENT '来源IP'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间'," +
                "  KEY idx_user_time (user_id, created_at)," +
                "  KEY idx_type (event_type)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号安全事件审计'"
            );
            log.info("迁移: user_login_security / user_security_event 表已就绪");

        } catch (Exception e) {
            log.warn("账号安全建表异常: {}", e.getMessage());
        }

        // 「关于我们」页面配置（单行表）。与 sql/init.sql 双写，只做追加。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS app_about (" +
                "  id BIGINT PRIMARY KEY COMMENT '恒为1，单行配置'," +
                "  app_name VARCHAR(50) NOT NULL COMMENT '应用名'," +
                "  version VARCHAR(20) NOT NULL COMMENT '当前版本号'," +
                "  slogan VARCHAR(100) COMMENT '品牌slogan'," +
                "  motto_main VARCHAR(200) COMMENT '理念主句'," +
                "  motto_sub VARCHAR(200) COMMENT '理念副句'," +
                "  phone VARCHAR(20) COMMENT '管理员电话（点击拨打）'," +
                "  email VARCHAR(120) COMMENT '联系邮箱（点击复制）'," +
                "  contact_tip VARCHAR(300) COMMENT '联系时间提示'," +
                "  copyright VARCHAR(300) COMMENT '底部版权文案'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关于我们页面配置'"
            );
            stmt.execute(
                "INSERT IGNORE INTO app_about (id, app_name, version, slogan, motto_main, motto_sub, phone, email, contact_tip, copyright) " +
                "VALUES (1, '猫遇猫舍', 'v1.1.0', '遇一只猫 · 安一个家', '让领养代替购买\\n爱不流浪', '遇一只猫，安一个家', " +
                "'17671710525', '1343076057@qq.com', '如有领养咨询、流浪猫求助或合作意向，欢迎在工作时间（9:00–21:00）与我们联系。', " +
                "'猫遇猫舍 · 用心善待每一只小猫\\n© 2026 CatMeet Cattery')"
            );
            log.info("迁移: app_about 表已就绪（含默认配置）");

        } catch (Exception e) {
            log.warn("app_about 建表异常: {}", e.getMessage());
        }

        // 志愿者申请表。与 sql/init.sql 双写，只做追加。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS volunteer_application (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID'," +
                "  applicant_id BIGINT NOT NULL COMMENT '申请人ID'," +
                "  real_name VARCHAR(50) NOT NULL COMMENT '真实姓名'," +
                "  contact VARCHAR(100) NOT NULL COMMENT '联系方式（手机号或微信）'," +
                "  region VARCHAR(150) COMMENT '所在区域（省市区）'," +
                "  service_time VARCHAR(100) COMMENT '可服务时间'," +
                "  reason TEXT COMMENT '申请理由'," +
                "  status TINYINT DEFAULT 0 COMMENT '状态: 0待审核 1已通过 2已拒绝'," +
                "  reviewer_id BIGINT COMMENT '审核人ID'," +
                "  review_comment TEXT COMMENT '审核意见'," +
                "  reviewed_at DATETIME COMMENT '审核时间'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  INDEX idx_applicant (applicant_id)," +
                "  INDEX idx_status (status)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿者申请表'"
            );
            log.info("迁移: volunteer_application 表已就绪");

        } catch (Exception e) {
            log.warn("volunteer_application 建表异常: {}", e.getMessage());
        }
    }

    private void execQuietly(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
            log.info("迁移执行: {}", sql);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Duplicate column") || msg.contains("Duplicate entry")
                    || msg.contains("check that it already exists"))) {
                log.debug("跳过已存在的结构: {}", sql);
            } else {
                log.warn("迁移失败 [{}]: {}", sql, msg);
            }
        }
    }
}
