package com.capstone.passfolio.domain.file.repository;

import com.capstone.passfolio.domain.file.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link File} (post-upload metadata of S3 multipart uploads).
 *
 * <p>이 백로그(BE-file-aws-s3-multipart-upload-presigned-url) 의 스코프는 {@code initiate / part-presigned-url /
 * list-parts / complete / abort} 5개 엔드포인트뿐이며, 그 흐름에서 본 리포지토리에 필요한 호출은
 * 다음 두 가지로 충분하다.
 *
 * <ul>
 *   <li>{@code save(File)} — {@link com.capstone.passfolio.domain.file.service.FileService#completeMultipartUpload}
 *       에서 post-upload {@code HeadObject} 검증을 통과한 직후 호출.</li>
 *   <li>{@code findById(Long)} — 같은 서비스의 {@code validateFileOwner(fileId, userId)} IDOR 가드가 사용.</li>
 * </ul>
 *
 * <p>두 메서드 모두 {@link JpaRepository} 가 기본 제공하므로 별도 선언이 없다. 향후 cross-domain
 * attachment(예: {@code findAllByCreatedBy} 페이지네이션) 가 추가될 때 시그니처가 더 늘어날 수 있다.
 *
 * <p>{@code @Repository} 어노테이션은 부여하지 않는다 — 프로젝트 컨벤션상
 * {@link com.capstone.passfolio.domain.user.repository.UserRepository} 도 미부여 상태이며,
 * Spring Data 는 {@link JpaRepository} 인터페이스 자체로 빈 후보를 자동 등록하므로 기능상 영향이 없다.
 */
public interface FileRepository extends JpaRepository<File, Long> {

    /**
     * 특정 사용자가 업로드한 파일 전체를 최신순으로 조회한다.
     *
     * <p>{@link com.capstone.passfolio.common.auditor.UserBaseEntity#getCreatedBy()} 가 파라미터 {@code userId}
     * 와 일치하는 행을 모두 반환. {@code GET /api/v1/files/me} 가 사용자의 업로드 CDN URL 목록을
     * 반환할 때 호출된다.
     */
    List<File> findAllByCreatedByOrderByCreatedAtDesc(Long userId);
}
