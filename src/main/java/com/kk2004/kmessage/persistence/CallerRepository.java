package com.kk2004.kmessage.persistence;
import com.kk2004.kmessage.domain.Entities.Caller;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CallerRepository extends JpaRepository<Caller, String> { Optional<Caller> findByName(String name); }
