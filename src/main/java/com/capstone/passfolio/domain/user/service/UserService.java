package com.capstone.passfolio.domain.user.service;

import com.capstone.passfolio.common.dto.PageDto;
import com.capstone.passfolio.domain.user.dto.UserDto;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;

    @Transactional(readOnly = true)
    public UserDto.UserResponse retrieveMe(UserPrincipal userPrincipal) {
        log.info("💡 Called retrieve api");

        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        return UserDto.UserResponse.from(foundUser);
    }

    /**
     * Admin 계정을 하드 딜리트하면서, 해당 작성자가 남긴 모든 Article 과 그 fileUrls 의 S3 객체를
     * 함께 정리한다. (T14 — admin-user-delete → bulk article delete + S3 cleanup wiring)
     *
     * <p>설계 결정 (요약 — 상세는 {@code 0001-harness-design.md} §"Decisions" 의
     * "Admin user delete = bulk article delete + bulk S3 delete"):
     * <ul>
     *   <li><b>호출자 가드는 Service 레이어</b> — 프로젝트 컨벤션 (역할 체크는 {@code @PreAuthorize} 가
     *       아니라 service 가 수행). 호출자({@code caller}) 가 ADMIN 이 아니면 {@code AUTH_FORBIDDEN}
     *       으로 거부한다. ({@code ARTICLE_FORBIDDEN} 은 article 도메인 한정 의미를 가지므로 user
     *       도메인 진입점에서는 일반 {@code AUTH_FORBIDDEN} 을 사용한다.)</li>
     *   <li><b>대상 사용자도 ADMIN 이어야 한다</b> — 본 메서드의 책임은 "admin-user 삭제 시 Article 정리"
     *       (요구사항 R6). USER 계정 삭제는 article 작성 권한이 없으므로 Article 정리가 의미 없다.
     *       다만 방어적으로 {@code articleService.deleteAllByWriter} 를 호출해도 안전한 no-op 가
     *       되므로(작성한 article 이 없음 → 즉시 반환), 본 진입점은 의도를 명확히 하기 위해 ADMIN 만
     *       허용한다. 다른 USER 삭제 경로는 {@link com.capstone.passfolio.domain.auth.service.AuthService}
     *       의 self-delete 가 별도로 담당.</li>
     *   <li><b>Article 삭제 → User 삭제 순서</b> — 동일 {@link Transactional} 안에서 article bulk delete
     *       이 user row delete 보다 먼저 일어나야 한다. 이유:
     *       <ol>
     *         <li>외래키 무결성 — {@code articles.created_by} → {@code users.id} FK 가 ON DELETE 정책
     *             없이 정의되어 있으면 user row 를 먼저 지울 때 FK violation 으로 롤백된다. 명시적
     *             선후 순서로 무결성을 보장.</li>
     *         <li>S3 cleanup 가시성 — {@link ArticleService#deleteAllByWriter(Long)} 는 fileUrls 를
     *             펼쳐 S3 batch delete 를 호출하는데, user 가 먼저 사라지면 audit log 상에서
     *             "어떤 admin 의 article 정리였는지" 추적이 어려워진다.</li>
     *       </ol></li>
     *   <li><b>단일 트랜잭션 — atomic</b> — 클래스/메서드에 {@link Transactional} 이 적용되어 있고,
     *       {@code ArticleService.deleteAllByWriter} 는 propagation {@code REQUIRED} (default) 라
     *       호출 측 트랜잭션에 합류한다. DB 작업 (article DELETE + user DELETE) 은 함께 commit/rollback
     *       된다. S3 batch delete 는 트랜잭션 외 부수효과지만 fail-soft (예외 비전파) 라 롤백을 유발하지
     *       않는다 — 잔여 객체는 lifecycle rule 로 회수.</li>
     *   <li><b>{@code articleService.deleteAllByWriter} 호출 전에 article 을 미리 로드하지 않는다</b> —
     *       T10 의 risks 절에서 명시적으로 권고된 사항: {@code @Modifying} bulk DELETE 가 영속성 컨텍스트
     *       플러시/클리어를 자동 수행하지 않으므로, 같은 트랜잭션에서 article 을 변경/조회 후 호출하면
     *       변경분이 유실되거나 stale read 가 일어날 수 있다. 본 메서드는 user 만 검증 후 article 정리를
     *       위임하므로 안전.</li>
     *   <li><b>self-delete 방지</b> — caller 가 자기 자신을 삭제하는 시나리오는 운영적으로 위험하므로
     *       (admin 0명 상태 가능) 본 메서드는 차단한다. 자기 계정은 {@code AuthService#delete} 사용.
     *       이는 보호 차원이지 RBAC 결정이 아니므로 {@code GLOBAL_INVALID_PARAMETER} (400) 로 응답.</li>
     * </ul>
     *
     * <p>실패 모드:
     * <ul>
     *   <li>{@code caller} 가 null 이거나 ADMIN 이 아닌 경우 — {@code AUTH_FORBIDDEN} (403).</li>
     *   <li>{@code targetUserId} 가 caller 본인 — {@code GLOBAL_INVALID_PARAMETER} (400).</li>
     *   <li>{@code targetUserId} 에 해당하는 user 미존재 — {@code USER_NOT_FOUND} (404).</li>
     *   <li>대상이 ADMIN 이 아닌 경우 — {@code GLOBAL_INVALID_PARAMETER} (400).</li>
     *   <li>article DELETE / user DELETE DB 오류 — 트랜잭션 롤백 (양쪽 모두 원복).</li>
     *   <li>S3 batch delete 부분 실패 — 로그만 남기고 계속 (fail-soft, T09/S3Service.deleteObjects 계약).</li>
     * </ul>
     *
     * @param targetUserId 삭제 대상 admin 사용자 ID. {@code null} 금지.
     * @param caller       호출 사용자 principal. ADMIN 권한 필수, 자기 자신 삭제 금지.
     * @throws RestException ({@code AUTH_FORBIDDEN}) caller 가 ADMIN 아님
     * @throws RestException ({@code GLOBAL_INVALID_PARAMETER}) self-delete 또는 대상이 ADMIN 아님
     * @throws RestException ({@code USER_NOT_FOUND}) 대상 user 미존재
     * @see ArticleService#deleteAllByWriter(Long)
     */
    @Transactional
    public void deleteAdmin(Long targetUserId, UserPrincipal caller) {
        assertAdminCaller(caller);

        if (targetUserId == null) {
            throw new RestException(ErrorCode.GLOBAL_INVALID_PARAMETER);
        }
        if (targetUserId.equals(caller.getUserId())) {
            // self-delete 는 AuthService.delete 가 담당 — 본 진입점은 거부
            log.warn("[User] Admin attempted self-delete via deleteAdmin: userId={}", targetUserId);
            throw new RestException(ErrorCode.GLOBAL_INVALID_PARAMETER);
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        if (target.getRole() != Role.ADMIN) {
            // T14 진입점은 admin 삭제 한정 (요구사항 R6). USER 삭제는 다른 경로 사용.
            throw new RestException(ErrorCode.GLOBAL_INVALID_PARAMETER);
        }

        // 관련 데이터(article+S3·file+S3·analysis·repo_availability·dev_spec·ai_jobs) 정리 + User 행 처리.
        // 대상이 ADMIN 이므로 purge 내부에서 User 행은 Hard Delete 된다(자식 먼저 정리 → FK 위반 없음).
        accountDeletionService.purge(target);

        log.info("[User] Admin deleted: targetUserId={}, by callerId={}", targetUserId, caller.getUserId());
    }

    /**
     * 호출자가 ADMIN 인지 검증한다. 미인증/role null/USER 모두 {@code AUTH_FORBIDDEN}.
     *
     * <p>{@code ARTICLE_FORBIDDEN} 이 아니라 {@code AUTH_FORBIDDEN} 을 사용하는 이유:
     * 본 메서드는 user 도메인의 admin 전용 작업이며, "article 작성/수정/삭제 권한 부족" 의미가 아니다.
     * 일반 RBAC 차단은 {@code AUTH_FORBIDDEN} 으로 통일.
     */
    private void assertAdminCaller(UserPrincipal caller) {
        if (caller == null || caller.getRole() == null || caller.getRole() != Role.ADMIN) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // ============================================================
    // ADMIN 회원 관리 — 사용자 유입 집계 / 회원 목록(상태 레벨)
    // ============================================================

    /** 날짜별 가입자 수(오름차순). 가입 없는 날은 행이 없으므로 갭은 FE가 0으로 채워 연속 시계열로 그린다. */
    @Transactional(readOnly = true)
    public List<UserDto.DailySignupResponse> getDailySignups(UserPrincipal caller) {
        assertAdminCaller(caller);
        return userRepository.countDailySignups().stream()
                .map(row -> UserDto.DailySignupResponse.builder()
                        .date(toLocalDate(row[0]).format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();
    }

    /** 회원 목록(페이지네이션). 우선 id·nickname만 — 추후 QueryDSL/커버링 인덱스로 필드 확장. */
    @Transactional(readOnly = true)
    public PageDto.PageListResponse<UserDto.UserSummaryResponse> listUsers(UserPrincipal caller, Pageable pageable) {
        assertAdminCaller(caller);
        Page<UserDto.UserSummaryResponse> page = userRepository.findAll(pageable)
                .map(UserDto.UserSummaryResponse::from);
        return PageDto.PageListResponse.of("회원 목록", page);
    }

    /** 네이티브/JPQL cast 결과(LocalDate / java.sql.Date / LocalDateTime 등)를 LocalDate로 정규화. */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (value instanceof LocalDateTime dateTime) return dateTime.toLocalDate();
        return LocalDate.parse(value.toString());
    }
}
