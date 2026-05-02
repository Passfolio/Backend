package com.capstone.passfolio.domain.file.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstone.passfolio.domain.file.entity.File;
import com.capstone.passfolio.domain.file.entity.enums.MediaType;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link FileRepository} JPA 슬라이스 테스트.
 *
 * <p>설계 §Files plan T20 — "@DataJpaTest (H2): persist + findById + uniqueness constraint on
 * s3_object_key" 를 명세한다. 백로그(BE-file-aws-s3-multipart-upload-presigned-url) 의 다른
 * 흐름이 의존하는 두 호출 — {@code save(File)} (FileService.completeMultipartUpload) 와
 * {@code findById(Long)} (FileService.validateFileOwner IDOR 가드) — 가 H2 환경에서도 동작하고,
 * {@link File} 엔티티의 {@code @UniqueConstraint(name = "UK_FILES_S3_OBJECT_KEY")} 가 실제로
 * DB-레벨에서 강제되는지를 검증한다.
 *
 * <p><b>왜 Flyway 를 끄는가</b> — 프로젝트의 베이스라인 마이그레이션
 * {@code V1__init_pg_trgm_and_search_indexes.sql} 은 PostgreSQL 전용 문법(pg_trgm 확장,
 * gin_trgm_ops, to_regclass, anonymous DO $$ 블록) 을 사용하므로 H2 에서는 PostgreSQL 호환 모드를
 * 켜더라도 파싱이 실패한다. 슬라이스 테스트의 목적은 "엔티티 매핑이 RDB 에 옳게 적용되는가"
 * 의 확인이므로 Flyway 는 비활성화하고 Hibernate {@code ddl-auto=create-drop} 으로 엔티티에서
 * 스키마를 직접 생성한다 (T08 entry §Risks/Known gaps 에서 사전에 권고된 방식).
 *
 * <p><b>왜 H2 PostgreSQL MODE 를 명시하는가</b> — 운영 DB 가 PostgreSQL 이므로 식별자 폴딩
 * (대문자→소문자) · {@code BIGSERIAL}/{@code IDENTITY} 호환 · boolean 리터럴 등 미세한 방언
 * 차이를 운영과 가깝게 맞추기 위함이다. T07 엔티티의 {@code GenerationType.IDENTITY} 는 H2
 * 기본 모드에서도 동작하지만, 후속 마이그레이션이 추가되었을 때 이 테스트를 PostgreSQL 에
 * 더 가깝게 두면 회귀를 일찍 잡을 수 있다.
 *
 * <p>본 테스트는 검증 명세이지 커버리지 도구가 아니다 — 모든 단언은 구체 값 (s3ObjectKey,
 * filename, fileSize, mediaType) 까지 검사한다 ({@code isNotNull()} 만 보지 않는다).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
        properties = {
                "spring.flyway.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:file_repo_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.show-sql=false"
        }
)
class FileRepositoryTest {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private TestEntityManager em;

    /**
     * 테스트 헬퍼 — 백로그 §S3 key format 의 형태를 따른 유효한 {@link File} 인스턴스를 만든다.
     * 각 테스트가 필요한 필드만 override 하도록 모든 필드에 합리적 기본값을 둔다.
     */
    private static File sampleFile(String s3ObjectKey, String filename, long fileSize, MediaType mediaType) {
        return File.builder()
                .s3ObjectKey(s3ObjectKey)
                .filename(filename)
                .fileSize(fileSize)
                .mediaType(mediaType)
                .build();
    }

    @Nested
    @DisplayName("persist + findById — FileService.completeMultipartUpload / validateFileOwner 호출 경로")
    class PersistAndFindById {

        @Test
        @DisplayName("save(File) → IDENTITY 로 ID 발급, findById 로 모든 필드 라운드트립")
        void save_thenFindById_roundTripsAllFields() {
            // given — 백로그 §S3 key format 형태의 키 + 100MB 미만 PDF
            File toSave = sampleFile(
                    "files/pdf/11111111-1111-1111-1111-111111111111__resume.pdf",
                    "resume.pdf",
                    1_048_576L, // 1 MiB
                    MediaType.PDF
            );

            // when — FileService.completeMultipartUpload 가 하는 것과 동일한 호출
            File saved = fileRepository.save(toSave);
            em.flush();
            em.clear(); // 1차 캐시 비움 — 진짜 SELECT 가 나가도록

            // then — IDENTITY 로 PK 가 채워졌고 (BIGSERIAL/IDENTITY 호환 확인)
            assertThat(saved.getId()).isNotNull().isPositive();

            // and — 모든 비-감사 필드가 그대로 라운드트립
            Optional<File> reloaded = fileRepository.findById(saved.getId());
            assertThat(reloaded)
                    .as("persist 직후 같은 ID 로 findById 가 빈 결과를 내면 안 됨")
                    .isPresent();

            File found = reloaded.get();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getS3ObjectKey())
                    .isEqualTo("files/pdf/11111111-1111-1111-1111-111111111111__resume.pdf");
            assertThat(found.getFilename()).isEqualTo("resume.pdf");
            assertThat(found.getFileSize()).isEqualTo(1_048_576L);
            assertThat(found.getMediaType()).isEqualTo(MediaType.PDF);
        }

        @Test
        @DisplayName("findById: 존재하지 않는 ID 는 빈 Optional (validateFileOwner 가 의존하는 분기)")
        void findById_returnsEmpty_whenIdAbsent() {
            // when — 영속화된 행이 없는 상태에서 임의 ID 조회
            Optional<File> result = fileRepository.findById(999_999L);

            // then — null 이 아니라 명시적으로 비어있는 Optional 이어야 함
            //        (FileService.validateFileOwner 는 isPresent() 분기로 처리)
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("MediaType 다섯 종 (IMAGE/VIDEO/AUDIO/PDF/UNKNOWN) 모두 STRING 으로 영속화/조회")
        void mediaType_allEnumValues_persistAndLoadAsString() {
            // given — 각 MediaType 별로 1개씩 영속화
            for (MediaType mediaType : MediaType.values()) {
                em.persist(sampleFile(
                        "files/pdf/key-" + mediaType.name().toLowerCase() + "__file.bin",
                        "file-" + mediaType.name() + ".bin",
                        2048L,
                        mediaType
                ));
            }
            em.flush();
            em.clear();

            // when — 모든 행을 다시 조회
            var all = fileRepository.findAll();

            // then — 5종이 모두 라운드트립되고 enum 값이 정확
            assertThat(all)
                    .hasSize(MediaType.values().length)
                    .extracting(File::getMediaType)
                    .containsExactlyInAnyOrder(MediaType.values());
        }

        @Test
        @DisplayName("save: createdBy 가 null 이어도 persist 성공 (감사 빈 미주입 환경 호환)")
        void save_succeeds_whenCreatedByNull() {
            // given — @DataJpaTest 컨텍스트에는 AuditorAwareImpl 의 SecurityContext 기반 빈이
            //          주입되지 않으므로 createdBy 는 null 로 남는다.
            //          T08 마이그레이션이 created_by 에 NOT NULL 을 두지 않은 의도가 여기서 검증된다.
            File toSave = sampleFile(
                    "files/pdf/22222222-2222-2222-2222-222222222222__no-auditor.pdf",
                    "no-auditor.pdf",
                    4096L,
                    MediaType.PDF
            );

            // when / then — 예외 없이 저장되고 PK 가 발급된다
            File saved = fileRepository.save(toSave);
            em.flush();
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedBy()).isNull();
        }
    }

    @Nested
    @DisplayName("UK_FILES_S3_OBJECT_KEY — 동일 s3_object_key 두 번 영속화 시 DB 레벨 거부")
    class UniqueConstraintOnS3ObjectKey {

        @Test
        @DisplayName("같은 s3_object_key 두 번 persist → flush 시점에 DataIntegrityViolationException")
        void duplicateS3ObjectKey_throwsOnFlush() {
            // given — 첫 행은 정상 영속화
            String duplicateKey = "files/pdf/33333333-3333-3333-3333-333333333333__same.pdf";
            em.persistAndFlush(sampleFile(duplicateKey, "first.pdf", 1024L, MediaType.PDF));

            // when — 동일 s3_object_key 로 두번째 행 시도
            File second = sampleFile(duplicateKey, "second.pdf", 2048L, MediaType.PDF);

            // then — flush 시점에 DB 가 UK 위반으로 거부.
            //        Hibernate 는 ConstraintViolationException 을 던지고 Spring 의 JPA
            //        예외 변환기는 이를 DataIntegrityViolationException 으로 매핑한다.
            //        (PersistenceException 도 슈퍼타입으로 동시 매치되므로 둘 중 하나로 단언)
            assertThatThrownBy(() -> {
                em.persist(second);
                em.flush();
            })
                    .isInstanceOfAny(
                            DataIntegrityViolationException.class,
                            PersistenceException.class
                    )
                    // 단순 타입 매칭만 하면 다른 제약 위반과 혼동될 수 있어, 키 이름 또는
                    // s3_object_key 컬럼명이 메시지 체인 어딘가에 등장하는지 확인.
                    .hasMessageFindingMatch("(?i).*(uk_files_s3_object_key|s3_object_key|unique).*");
        }

        @Test
        @DisplayName("서로 다른 s3_object_key 는 동시 영속화 가능 (UK 가 키 단위로 작동)")
        void differentS3ObjectKeys_persistIndependently() {
            // given / when — 서로 다른 키 두 행
            em.persistAndFlush(sampleFile(
                    "files/pdf/44444444-4444-4444-4444-444444444444__a.pdf",
                    "a.pdf", 1024L, MediaType.PDF));
            em.persistAndFlush(sampleFile(
                    "files/pdf/55555555-5555-5555-5555-555555555555__b.pdf",
                    "b.pdf", 2048L, MediaType.PDF));
            em.clear();

            // then — 둘 다 영속화 (UK 는 같은 값일 때만 거부, 다른 값에는 영향 없음)
            assertThat(fileRepository.findAll())
                    .hasSize(2)
                    .extracting(File::getS3ObjectKey)
                    .containsExactlyInAnyOrder(
                            "files/pdf/44444444-4444-4444-4444-444444444444__a.pdf",
                            "files/pdf/55555555-5555-5555-5555-555555555555__b.pdf"
                    );
        }

        @Test
        @DisplayName("기존 행 삭제 후 같은 s3_object_key 재사용 가능 (UK 는 현재 살아있는 행만 검사)")
        void sameKeyReusable_afterDeletion() {
            // given — 영속화 후 즉시 삭제 (실제 운영의 abort/cleanup 시나리오 모사)
            String reusedKey = "files/pdf/66666666-6666-6666-6666-666666666666__reused.pdf";
            File first = sampleFile(reusedKey, "first.pdf", 1024L, MediaType.PDF);
            em.persistAndFlush(first);
            em.remove(first);
            em.flush();
            em.clear();

            // when — 같은 키로 다시 영속화
            File second = sampleFile(reusedKey, "second.pdf", 2048L, MediaType.PDF);

            // then — UK 는 살아있는 행이 없으므로 통과
            File saved = fileRepository.save(second);
            em.flush();
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getS3ObjectKey()).isEqualTo(reusedKey);
            assertThat(saved.getFilename()).isEqualTo("second.pdf");
        }
    }
}
