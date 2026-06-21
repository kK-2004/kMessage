package com.kk2004.kmessage.persistence;

import com.kk2004.kmessage.domain.Entities.ChannelContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelContactRepository extends JpaRepository<ChannelContact, String> {
    Optional<ChannelContact> findByChannelInstanceIdAndTargetId(String channelInstanceId, String targetId);
    List<ChannelContact> findByChannelInstanceIdOrderByLastSeenAtDesc(String channelInstanceId);
    void deleteByChannelInstanceId(String channelInstanceId);
}
