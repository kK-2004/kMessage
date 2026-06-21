package com.kk2004.kmessage.persistence;

import com.kk2004.kmessage.domain.Entities.UserGroupMember;
import com.kk2004.kmessage.domain.Entities.UserGroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserGroupMemberRepository extends JpaRepository<UserGroupMember, UserGroupMemberId> {
    List<UserGroupMember> findByGroupId(String groupId);
    List<UserGroupMember> findByAppUserId(String appUserId);
    void deleteByGroupId(String groupId);
    void deleteByAppUserId(String appUserId);
    void deleteByGroupIdIn(List<String> groupIds);
    long countByGroupId(String groupId);

    /** AppUser ids that are members of the given group. */
    @Query(value = "select app_user_id from user_group_members where group_id = :groupId", nativeQuery = true)
    List<String> findAppUserIdsByGroupId(@Param("groupId") String groupId);

    /** AppUser ids that are direct members of ANY of the given groups (not deduplicated). */
    @Query(value = "select app_user_id from user_group_members where group_id in (:groupIds)", nativeQuery = true)
    List<String> findAppUserIdsByGroupIdIn(@Param("groupIds") List<String> groupIds);
}
