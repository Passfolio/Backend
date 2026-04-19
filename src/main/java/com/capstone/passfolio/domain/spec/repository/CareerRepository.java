package com.capstone.passfolio.domain.spec.repository;

import com.capstone.passfolio.domain.spec.entity.Career;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerRepository extends JpaRepository<Career, String> {
}
