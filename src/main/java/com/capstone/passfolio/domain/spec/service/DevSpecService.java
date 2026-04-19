package com.capstone.passfolio.domain.spec.service;

import com.capstone.passfolio.domain.spec.dto.DevSpecDto;
import com.capstone.passfolio.domain.spec.entity.Career;
import com.capstone.passfolio.domain.spec.entity.DevSpec;
import com.capstone.passfolio.domain.spec.entity.DevSpecCareer;
import com.capstone.passfolio.domain.spec.entity.DevSpecEducation;
import com.capstone.passfolio.domain.spec.entity.UniversityDepartment;
import com.capstone.passfolio.domain.spec.repository.CareerRepository;
import com.capstone.passfolio.domain.spec.repository.DevSpecCareerRepository;
import com.capstone.passfolio.domain.spec.repository.DevSpecEducationRepository;
import com.capstone.passfolio.domain.spec.repository.DevSpecRepository;
import com.capstone.passfolio.domain.spec.repository.UniversityDepartmentRepository;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DevSpecService {
    private final UserRepository userRepository;
    private final DevSpecRepository devSpecRepository;
    private final DevSpecEducationRepository devSpecEducationRepository;
    private final DevSpecCareerRepository devSpecCareerRepository;
    private final UniversityDepartmentRepository universityDepartmentRepository;
    private final CareerRepository careerRepository;

    @Transactional
    public DevSpecDto.UpdateResponse updateDevSpec(
            DevSpecDto.UpdateRequest request,
            UserPrincipal userPrincipal) {

        // SEQ 1. Find User
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        // SEQ 2. Request values (Bean Validation on UpdateRequest: null/blank/duplicate)
        List<Long> departmentIds = request.getUniversityDepartmentIds();
        List<String> careerIds = request.getCareerIds();

        // SEQ 3. Load referenced entities
        Map<Long, UniversityDepartment> departmentsById = loadDepartments(departmentIds);
        List<Career> careers = loadCareers(careerIds);

        // SEQ 5. Define DevSpec (upsert)
        DevSpec devSpec = devSpecRepository.findById(foundUser.getId())
                .orElseGet(() -> DevSpec.createFor(foundUser));

        // SEQ 6. Update DevSpec 'experience' field
        devSpec.updateExperience(request.getExperience());

        boolean isNew = devSpec.getId() == null;
        if (isNew) {
            // 신규: 자식 INSERT 전 부모 PK 확정 (@MapsId FK 무결성)
            devSpecRepository.saveAndFlush(devSpec);
        } else {
            // 기존: 벌크 DELETE로 자식 행 선제 제거 (flush ordering 문제 방지, N+1 제거)
            // 컬렉션 필드(getDevSpecEducations/Careers)는 접근하지 않아 lazy load 없음
            devSpecEducationRepository.deleteAllByDevSpecId(devSpec.getId());
            devSpecCareerRepository.deleteAllByDevSpecId(devSpec.getId());
        }

        // SEQ 7. Insert new DevSpecEducation rows
        List<DevSpecEducation> newEducations = new ArrayList<>();
        for (int i = 0; i < departmentIds.size(); i++) {
            newEducations.add(DevSpecEducation.of(devSpec, departmentsById.get(departmentIds.get(i)), i));
        }
        devSpecEducationRepository.saveAll(newEducations);

        // SEQ 8. Insert new DevSpecCareer rows
        List<DevSpecCareer> newCareers = careers.stream()
                .map(c -> DevSpecCareer.of(devSpec, c))
                .toList();
        devSpecCareerRepository.saveAll(newCareers);

        // SEQ 9. (experience 변경은 트랜잭션 커밋 시 dirty checking으로 반영)

        // SEQ 10. Return UpdateResponse — 로컬 리스트 사용 (stale 컬렉션 접근 방지)
        return DevSpecDto.UpdateResponse.from(devSpec, newEducations, careers);
    }

    @Transactional(readOnly = true)
    public DevSpecDto.UpdateResponse getMyDevSpec(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));
        Optional<DevSpec> specOpt = devSpecRepository.findById(user.getId());
        if (specOpt.isEmpty()) {
            return DevSpecDto.UpdateResponse.empty();
        }
        DevSpec devSpec = specOpt.get();
        List<DevSpecEducation> educationRows =
                devSpecEducationRepository.findAllWithUniversityByDevSpecId(user.getId());
        List<Career> careers = devSpecCareerRepository.findAllWithCareerByDevSpecId(user.getId()).stream()
                .map(DevSpecCareer::getCareer)
                .toList();
        return DevSpecDto.UpdateResponse.from(devSpec, educationRows, careers);
    }

    @Transactional(readOnly = true)
    public List<DevSpecDto.EducationHistoryItem> getMyEducationHistory(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));
        if (!devSpecRepository.existsById(user.getId())) {
            return List.of();
        }
        return devSpecEducationRepository.findAllWithUniversityByDevSpecId(user.getId()).stream()
                .map(e -> DevSpecDto.EducationHistoryItem.from(e.getUniversityDepartment()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DevSpecDto.CareerReadResponse getMyCareer(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));
        Optional<DevSpec> specOpt = devSpecRepository.findById(user.getId());
        if (specOpt.isEmpty()) {
            return DevSpecDto.CareerReadResponse.empty();
        }
        DevSpec devSpec = specOpt.get();
        List<Career> careers = devSpecCareerRepository.findAllWithCareerByDevSpecId(user.getId()).stream()
                .map(DevSpecCareer::getCareer)
                .toList();
        return DevSpecDto.CareerReadResponse.from(devSpec, careers);
    }

    private Map<Long, UniversityDepartment> loadDepartments(List<Long> departmentIds) {
        if (departmentIds.isEmpty()) {
            return Map.of();
        }

        List<UniversityDepartment> founds = universityDepartmentRepository.findAllById(departmentIds);
        Map<Long, UniversityDepartment> byId = founds.stream()
                .collect(Collectors.toMap(UniversityDepartment::getId, Function.identity()));
        for (Long id : departmentIds) {
            if (!byId.containsKey(id)) {
                throw new RestException(
                        ErrorCode.GLOBAL_BAD_REQUEST,
                        "존재하지 않는 university_department id 입니다: " + id);
            }
        }
        return byId;
    }

    private List<Career> loadCareers(List<String> careerIds) {
        if (careerIds.isEmpty()) {
            return List.of();
        }
        List<Career> found = careerRepository.findAllById(careerIds);
        Map<String, Career> byId = found.stream().collect(Collectors.toMap(Career::getId, Function.identity()));
        for (String id : careerIds) {
            if (!byId.containsKey(id)) {
                throw new RestException(
                        ErrorCode.GLOBAL_BAD_REQUEST,
                        "존재하지 않는 career id 입니다: " + id);
            }
        }
        return careerIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
