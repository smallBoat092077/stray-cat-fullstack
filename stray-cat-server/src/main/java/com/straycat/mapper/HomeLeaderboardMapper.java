package com.straycat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 首页志愿者三榜聚合（方案 6.3）。三榜互不重复：
 * feeding = 投喂打卡次数、food = 送出猫粮份数、active = 签到天数。
 *
 * 时间窗统一 [start, end) 半开区间，由 Service 按 week/month/all 计算后传入；
 * feeding 用 COALESCE(check_in_time, created_at)，food / active 用 created_at。
 * 排序口径：按用户分组，数值降序、相同数值按 userId 升序，LIMIT 截断。
 */
@Mapper
public interface HomeLeaderboardMapper {

    // ---------------- 榜单 ----------------

    @Select("SELECT volunteer_id AS userId, COUNT(*) AS val " +
            "FROM feeding_record " +
            "WHERE status = 1 AND volunteer_id IS NOT NULL " +
            "AND COALESCE(check_in_time, created_at) >= #{start} " +
            "AND COALESCE(check_in_time, created_at) < #{end} " +
            "GROUP BY volunteer_id " +
            "ORDER BY val DESC, userId ASC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> feedingBoard(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end,
                                           @Param("limit") int limit);

    @Select("SELECT from_user_id AS userId, COALESCE(SUM(coin_amount), 0) AS val " +
            "FROM cat_tip " +
            "WHERE created_at >= #{start} AND created_at < #{end} " +
            "GROUP BY from_user_id " +
            "ORDER BY val DESC, userId ASC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> foodBoard(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("limit") int limit);

    @Select("SELECT user_id AS userId, COUNT(*) AS val " +
            "FROM coin_transaction " +
            "WHERE biz_type = 'SIGNIN' " +
            "AND created_at >= #{start} AND created_at < #{end} " +
            "GROUP BY user_id " +
            "ORDER BY val DESC, userId ASC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> activeBoard(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("limit") int limit);

    // ---------------- 我的数值 ----------------

    @Select("SELECT COUNT(*) " +
            "FROM feeding_record " +
            "WHERE status = 1 AND volunteer_id = #{userId} " +
            "AND COALESCE(check_in_time, created_at) >= #{start} " +
            "AND COALESCE(check_in_time, created_at) < #{end}")
    Long feedingValue(@Param("userId") Long userId,
                      @Param("start") LocalDateTime start,
                      @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(coin_amount), 0) " +
            "FROM cat_tip " +
            "WHERE from_user_id = #{userId} " +
            "AND created_at >= #{start} AND created_at < #{end}")
    Long foodValue(@Param("userId") Long userId,
                   @Param("start") LocalDateTime start,
                   @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) " +
            "FROM coin_transaction " +
            "WHERE biz_type = 'SIGNIN' AND user_id = #{userId} " +
            "AND created_at >= #{start} AND created_at < #{end}")
    Long activeValue(@Param("userId") Long userId,
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

    // ---------------- 比我数值高的人数（同值按 userId 升序，保证与榜单名次一致） ----------------

    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT volunteer_id AS uid, COUNT(*) AS v FROM feeding_record " +
            "  WHERE status = 1 AND volunteer_id IS NOT NULL " +
            "  AND COALESCE(check_in_time, created_at) >= #{start} " +
            "  AND COALESCE(check_in_time, created_at) < #{end} " +
            "  GROUP BY volunteer_id" +
            ") t WHERE t.v > #{value} OR (t.v = #{value} AND t.uid < #{userId})")
    Long feedingHigherCount(@Param("value") long value,
                            @Param("userId") Long userId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT from_user_id AS uid, COALESCE(SUM(coin_amount), 0) AS v FROM cat_tip " +
            "  WHERE created_at >= #{start} AND created_at < #{end} " +
            "  GROUP BY from_user_id" +
            ") t WHERE t.v > #{value} OR (t.v = #{value} AND t.uid < #{userId})")
    Long foodHigherCount(@Param("value") long value,
                         @Param("userId") Long userId,
                         @Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT user_id AS uid, COUNT(*) AS v FROM coin_transaction " +
            "  WHERE biz_type = 'SIGNIN' " +
            "  AND created_at >= #{start} AND created_at < #{end} " +
            "  GROUP BY user_id" +
            ") t WHERE t.v > #{value} OR (t.v = #{value} AND t.uid < #{userId})")
    Long activeHigherCount(@Param("value") long value,
                           @Param("userId") Long userId,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);
}
