package com.kk2004.kmessage.persistence;
import com.kk2004.kmessage.domain.Entities.DeliveryTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
public interface TaskRepository extends JpaRepository<DeliveryTask, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DeliveryTask t where t.nextAttemptAt <= :now and (t.leaseUntil is null or t.leaseUntil < :now) order by t.nextAttemptAt")
    List<DeliveryTask> findClaimable(@Param("now") Instant now, Pageable pageable);
    long countByNextAttemptAtLessThanEqual(Instant now);
    void deleteByMessageIdIn(List<String> messageIds);
}
