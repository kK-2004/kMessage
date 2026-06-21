package com.kk2004.kmessage.persistence;
import com.kk2004.kmessage.domain.Entities.ApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ApiCredentialRepository extends JpaRepository<ApiCredential, String> {
    Optional<ApiCredential> findByAppKeyAndActiveTrue(String appKey);
    java.util.List<ApiCredential> findByCallerId(String callerId);
}
