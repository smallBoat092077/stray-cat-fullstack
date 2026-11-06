package com.straycat.util;

public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6371000;

    private GeoDistance() {
    }

    /**
     * 两点间大圆距离，单位米。只要求两个坐标点来自同一坐标系（本项目统一 gcj02）。
     */
    public static double metersBetween(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
