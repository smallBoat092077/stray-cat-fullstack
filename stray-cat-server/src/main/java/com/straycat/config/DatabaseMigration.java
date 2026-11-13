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
