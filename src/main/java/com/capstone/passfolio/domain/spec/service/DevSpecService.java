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

        // SEQ 5. Define DevSpec
        DevSpec devSpec = devSpecRepository.findById(foundUser.getId())
                .orElseGet(() -> DevSpec.createFor(foundUser));

        // SEQ 6. Update DevSpec a 'experience' field
        devSpec.updateExperience(request.getExperience());

        // SEQ 7. Fill data to DevSpecEducation Bridge
        devSpec.getDevSpecEducations().clear();
        for (int i = 0; i < departmentIds.size(); i++) {
            Long deptId = departmentIds.get(i);
            UniversityDepartment ud = departmentsById.get(deptId);
            devSpec.getDevSpecEducations().add(DevSpecEducation.of(devSpec, ud, i));
        }

        // SEQ 8. Fill data to DevSpecCareer Bridge
        devSpec.getDevSpecCareers().clear();
        for (Career career : careers) {
            devSpec.getDevSpecCareers().add(DevSpecCareer.of(devSpec, career));
        }

        // SEQ 9. Save DevSpec
        devSpecRepository.save(devSpec);

        // SEQ 10. Return UpdateResponse
        return DevSpecDto.UpdateResponse.from(devSpec, devSpec.getDevSpecEducations(), careers);
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
