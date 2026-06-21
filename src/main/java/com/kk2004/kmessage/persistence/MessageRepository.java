package com.kk2004.kmessage.persistence;
import com.kk2004.kmessage.domain.Entities.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
public interface MessageRepository extends JpaRepository<Message, String> {
    Optional<Message> findByCallerIdAndIdempotencyKey(String callerId, String key);
    Optional<Message> findByIdAndCallerId(String id, String callerId);
    long countByChannelInstanceId(String channelInstanceId);
    @Query(value = "select id from messages where channel_instance_id = :channelInstanceId", nativeQuery = true)
    List<String> findIdsByChannelInstanceId(@Param("channelInstanceId") String channelInstanceId);
    void deleteByChannelInstanceId(String channelInstanceId);

    /**
     * 按状态分组统计消息数量，返回 [status, count] 对。
     * 用于概览页状态分布环形图与总量/成功/失败指标。
     * 用原生 SQL 避免嵌套实体 Entities.Message 的 JPQL 实体名解析问题，
     * 同时兼容 H2（测试）与 MySQL（生产）。
     */
    @Query(value = "select status, count(*) from messages group by status", nativeQuery = true)
    List<Object[]> countGroupByStatus();

    /**
     * 按天分组统计某时刻以来的消息数量，按状态维度返回。
     * 结果行：[日期(yyyy-MM-dd), status, count]，同一日期可能有多行（每个状态一行）。
     * 用于概览页近 7 天趋势折线图。
     * 日期处理用 FORMATDATETIME（H2）兼容；生产 MySQL 的 DATE_FORMAT 在 nativeQuery 里
     * 由方言层处理，此处用 SQL 标准的 CAST 到 DATE 保证跨库兼容。
     */
    @Query(value = "select cast(created_at as date) as d, status, count(*) as c from messages " +
            "where created_at >= :start " +
            "group by d, status order by d",
            nativeQuery = true)
    List<Object[]> countGroupByDayAndStatus(@Param("start") Instant start);
}
