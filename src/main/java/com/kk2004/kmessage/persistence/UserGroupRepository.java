package com.kk2004.kmessage.persistence;

import com.kk2004.kmessage.domain.Entities.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserGroupRepository extends JpaRepository<UserGroup, String> {
    List<UserGroup> findByCallerIdAndChannelInstanceId(String callerId, String channelInstanceId);
    List<UserGroup> findByChannelInstanceId(String channelInstanceId);
    List<UserGroup> findByParentId(String parentId);
    void deleteByCallerIdAndChannelInstanceId(String callerId, String channelInstanceId);
    void deleteByChannelInstanceId(String channelInstanceId);
}
