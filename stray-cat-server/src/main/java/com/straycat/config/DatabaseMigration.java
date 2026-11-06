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
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_user_read (user_id, read_flag, id)," +
                "  INDEX idx_user_time (user_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("迁移: user_message 表已就绪");

        } catch (Exception e) {
            log.warn("user_message 建表异常: {}", e.getMessage());
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
