package com.kk2004.kmessage.stats;

import com.kk2004.kmessage.domain.MessageStatus;
import com.kk2004.kmessage.persistence.MessageRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private static final int TREND_DAYS = 7;
    private static final ZoneId TREND_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final MessageRepository messages;

    public StatsService(MessageRepository messages) {
        this.messages = messages;
    }

    public StatsView load() {
        // 1. 按状态聚合（总量 + 状态分布）
        List<Object[]> statusRows = messages.countGroupByStatus();
        long total = 0;
        long delivered = 0;
        long failed = 0;
        List<StatsView.StatusCount> statusCounts = new ArrayList<>();
        for (Object[] row : statusRows) {
            String status = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            total += count;
            if (MessageStatus.DELIVERED.name().equals(status)) delivered = count;
            if (MessageStatus.FAILED.name().equals(status)) failed = count;
            statusCounts.add(new StatsView.StatusCount(status, count));
        }
        double successRate = (delivered + failed) > 0
                ? Math.round(delivered * 10000.0 / (delivered + failed)) / 100.0
                : 0.0;

        // 2. 近 7 天趋势（补齐无数据的日期为 0）
        Instant start = Instant.now().minus(TREND_DAYS, ChronoUnit.DAYS);
        List<Object[]> dayRows = messages.countGroupByDayAndStatus(start);

        // 按日期分组：date -> {TOTAL, DELIVERED, FAILED}
        Map<String, long[]> byDate = new HashMap<>();
        for (Object[] row : dayRows) {
            String date = String.valueOf(row[0]);
            String status = String.valueOf(row[1]);
            long count = ((Number) row[2]).longValue();
            long[] agg = byDate.computeIfAbsent(date, k -> new long[3]);
            agg[0] += count; // total
            if (MessageStatus.DELIVERED.name().equals(status)) agg[1] = count;
            if (MessageStatus.FAILED.name().equals(status)) agg[2] = count;
        }

        // 生成最近 TREND_DAYS 天连续序列（含今天），缺失日期补 0
        List<StatsView.DailyTrend> trend = new ArrayList<>();
        LocalDate today = LocalDate.now(TREND_ZONE);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.format(DATE_KEY);
            long[] agg = byDate.getOrDefault(key, new long[3]);
            trend.add(new StatsView.DailyTrend(day.format(DATE_LABEL), agg[0], agg[1], agg[2]));
        }

        return new StatsView(total, delivered, failed, successRate, statusCounts, trend);
    }
}
