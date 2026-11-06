package com.straycat.util;

import java.util.Map;
import java.util.Set;

/**
 * 猫咪生活状态（cat_profile.life_status）的取值和允许的状态跳转。
 */
public final class CatLifeStatus {

    public static final int STRAYING = 0;
    public static final int FEEDING = 1;
    public static final int WAITING_ADOPTION = 2;
    public static final int ADOPTED = 3;
    public static final int FOLLOWING_UP = 4;

    private static final Map<Integer, Set<Integer>> ALLOWED = Map.of(
            STRAYING, Set.of(FEEDING, WAITING_ADOPTION),
            FEEDING, Set.of(WAITING_ADOPTION, ADOPTED),
            WAITING_ADOPTION, Set.of(FEEDING, ADOPTED),
            ADOPTED, Set.of(FOLLOWING_UP),
            FOLLOWING_UP, Set.of(ADOPTED, WAITING_ADOPTION)
    );

    private CatLifeStatus() {
    }

    /**
     * 同一个状态重复提交算合法，方便打卡这类会反复触发的联动
     */
    public static boolean canMove(Integer from, Integer to) {
        int form = from == null ? STRAYING : from;
        if (to == null || !ALLOWED.containsKey(to)) {
            return false;
        }
        return form == to || ALLOWED.getOrDefault(form, Set.of()).contains(to);
    }

    public static String text(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STRAYING -> "流浪中";
            case FEEDING -> "投喂中";
            case WAITING_ADOPTION -> "待领养";
            case ADOPTED -> "已领养";
            case FOLLOWING_UP -> "领养回访中";
            default -> "未知";
        };
    }
}
