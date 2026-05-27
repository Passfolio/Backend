package com.capstone.passfolio.domain.ai.repository;

import com.capstone.passfolio.domain.ai.entity.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    Optional<AiJob> findByIdAndUserId(Long id, Long userId);
}
