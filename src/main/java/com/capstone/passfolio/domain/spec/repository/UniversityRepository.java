package com.capstone.passfolio.domain.spec.repository;

import com.capstone.passfolio.domain.spec.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<University, String> {
}
