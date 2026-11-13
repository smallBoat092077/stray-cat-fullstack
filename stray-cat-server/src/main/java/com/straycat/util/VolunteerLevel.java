package com.straycat.util;

/**
 * 志愿者等级：只看「审核通过的投喂打卡次数」，服务时长按每次 0.5 小时折算。
 * 等级只升不降，升级时一次性发猫粮奖励（coin_transaction 的唯一键保证只发一次）。
 */
public enum VolunteerLevel {

    TRAINEE(1, 0, "见习志愿者", 0),
    JUNIOR(2, 3, "初级志愿者", 10),
    BACKBONE(3, 10, "骨干志愿者", 30),
    SENIOR(4, 30, "资深志愿者", 60),
    GOLD(5, 60, "金牌志愿者", 100),
    GUARDIAN(6, 100, "守护者", 200);

    /**
     * 一次审核通过的打卡记 0.5 小时服务时长
     */
    public static final double HOURS_PER_CHECKIN = 0.5;

    private final int level;
    private final int minCompleted;
    private final String title;
    private final int coinReward;

    VolunteerLevel(int level, int minCompleted, String title, int coinReward) {
        this.level = level;
        this.minCompleted = minCompleted;
        this.title = title;
        this.coinReward = coinReward;
    }

    public int getLevel() {
        return level;
    }

    public int getMinCompleted() {
        return minCompleted;
    }

    public String getTitle() {
        return title;
    }

    public int getCoinReward() {
        return coinReward;
    }

    public VolunteerLevel next() {
        VolunteerLevel[] all = values();
        return level < all.length ? all[level] : null;
    }

    /**
     * 按已完成次数落到对应等级
     */
    public static VolunteerLevel of(int completed) {
        VolunteerLevel matched = TRAINEE;
        for (VolunteerLevel item : values()) {
            if (completed >= item.minCompleted) {
                matched = item;
            }
        }
        return matched;
    }

    /**
     * 历史数据可能没有等级列，按 1 兜底
     */
    public static VolunteerLevel byLevel(Integer level) {
        if (level == null) {
            return TRAINEE;
        }
        for (VolunteerLevel item : values()) {
            if (item.level == level) {
                return item;
            }
        }
        return TRAINEE;
    }
}
