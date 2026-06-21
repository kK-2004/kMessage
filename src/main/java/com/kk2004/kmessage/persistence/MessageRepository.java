package com.kk2004.kmessage.persistence;
import com.kk2004.kmessage.domain.Entities.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface MessageRepository extends JpaRepository<Message, String> {
    Optional<Message> findByCallerIdAndIdempotencyKey(String callerId, String key);
    Optional<Message> findByIdAndCallerId(String id, String callerId);
    long countByChannelInstanceId(String channelInstanceId);
    @Query(value = "select id from messages where channel_instance_id = :channelInstanceId", nativeQuery = true)
    List<String> findIdsByChannelInstanceId(@Param("channelInstanceId") String channelInstanceId);
    void deleteByChannelInstanceId(String channelInstanceId);
}
