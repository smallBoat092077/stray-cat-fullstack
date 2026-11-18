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

        } catch (Exception e) {
            log.warn("区域迁移异常: {}", e.getMessage());
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
