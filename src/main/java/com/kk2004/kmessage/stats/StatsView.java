package com.kk2004.kmessage.stats;

import java.util.List;

/**
 * 概览页消息统计视图。
 *
 * @param total        消息总量（所有状态）
 * @param delivered    成功投递数（status = DELIVERED）
 * @param failed       失败数（status = FAILED）
 * @param successRate  成功率（0-100，分母为 0 时为 0.0）
 * @param statusCounts 各状态分布，用于环形图 [{status, count}]
 * @param dailyTrend   近 N 天趋势，用于折线图 [{date, total, delivered, failed}]
 */
public record StatsView(
        long total,
        long delivered,
        long failed,
        double successRate,
        List<StatusCount> statusCounts,
        List<DailyTrend> dailyTrend
) {
    public record StatusCount(String status, long count) {}
    public record DailyTrend(String date, long total, long delivered, long failed) {}
}
