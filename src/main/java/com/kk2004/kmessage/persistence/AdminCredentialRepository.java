package com.kk2004.kmessage.persistence;
import com.kk2004.kmessage.domain.Entities.AdminCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AdminCredentialRepository extends JpaRepository<AdminCredential, String> { Optional<AdminCredential> findById(String id); }
