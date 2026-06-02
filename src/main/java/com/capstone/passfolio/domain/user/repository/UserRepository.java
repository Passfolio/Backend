package com.capstone.passfolio.domain.user.repository;

import com.capstone.passfolio.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Modifying
    @Query("DELETE FROM User u WHERE u.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    Optional<User> findByUsername(String username);

    Optional<User> findByGithubId(Long githubId);

    /**
     * 가입일(createdAt)을 일 단위로 잘라 날짜별 가입자 수를 집계(오름차순).
     * 반환 row = [날짜(LocalDate/Date), count(Long)]. (가입자 없는 날은 행 없음 → 갭은 FE가 0으로 채움)
     */
    @Query("SELECT cast(u.createdAt as date) AS d, COUNT(u) FROM User u "
            + "GROUP BY cast(u.createdAt as date) ORDER BY cast(u.createdAt as date)")
    List<Object[]> countDailySignups();
}
