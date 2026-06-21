package com.kk2004.kmessage.persistence;
import com.kk2004.kmessage.domain.Entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GrantRepository extends JpaRepository<Grant, GrantId> {
    boolean existsByCallerIdAndChannelInstanceId(String callerId, String channelInstanceId);
    void deleteByCallerIdAndChannelInstanceId(String callerId, String channelInstanceId);
    void deleteByCallerId(String callerId);
    List<Grant> findByCallerId(String callerId);
    List<Grant> findByChannelInstanceId(String channelInstanceId);
    void deleteByChannelInstanceId(String channelInstanceId);
}
