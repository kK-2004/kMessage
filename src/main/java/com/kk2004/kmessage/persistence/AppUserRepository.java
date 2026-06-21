package com.kk2004.kmessage.persistence;

import com.kk2004.kmessage.domain.Entities.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    List<AppUser> findByChannelInstanceId(String channelInstanceId);
    Page<AppUser> findByChannelInstanceId(String channelInstanceId, Pageable pageable);
    Optional<AppUser> findByChannelInstanceIdAndTargetId(String channelInstanceId, String targetId);
    void deleteByChannelInstanceId(String channelInstanceId);
}
