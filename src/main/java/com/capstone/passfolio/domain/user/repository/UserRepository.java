package com.capstone.passfolio.domain.user.repository;

import com.capstone.passfolio.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Modifying
    @Query("DELETE FROM User u WHERE u.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    Optional<User> findByUsername(String username);
}
