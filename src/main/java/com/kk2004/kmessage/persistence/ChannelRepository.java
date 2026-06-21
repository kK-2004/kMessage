package com.kk2004.kmessage.persistence;
import com.kk2004.kmessage.domain.Entities.ChannelInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ChannelRepository extends JpaRepository<ChannelInstance, String> { Optional<ChannelInstance> findByName(String name); }
