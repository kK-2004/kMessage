package com.kk2004.kmessage.persistence;
import com.kk2004.kmessage.domain.Entities.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AttemptRepository extends JpaRepository<DeliveryAttempt, String> {
    List<DeliveryAttempt> findByMessageIdOrderByAttemptNumberAsc(String messageId);
    void deleteByMessageIdIn(List<String> messageIds);
}
