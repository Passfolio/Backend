package com.capstone.passfolio.domain.spec.repository;

import com.capstone.passfolio.domain.spec.entity.DevSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DevSpecRepository extends JpaRepository<DevSpec, Long> {

    // 회원탈퇴 — dev_spec은 @MapsId라 PK(id)==user.id. 행 일괄 삭제(없으면 no-op).
    // 자식(education/career)은 호출부에서 먼저 deleteAllByDevSpecId로 정리한다.
    @Modifying
    @Query("DELETE FROM DevSpec d WHERE d.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
