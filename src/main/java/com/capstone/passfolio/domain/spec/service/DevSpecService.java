package com.capstone.passfolio.domain.spec.service;

import com.capstone.passfolio.domain.spec.dto.DevSpecDto;
import com.capstone.passfolio.domain.spec.repository.CareerRepository;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DevSpecService {
    private final UserRepository userRepository;
    private final CareerRepository careerRepository;

    @Transactional
    public DevSpecDto.UpdateResponse updateDevSpec(
            DevSpecDto.UpdateRequest request,
            UserPrincipal userPrincipal) {

        /*
        UpdateRequest
        ├ int experience;
        ├ private List<Long> educationDepartmentIds;
        └ private List<String> careerIds;
        */

        /*
        UpdateResponse
        ├ private List<EducationHistoryItem> educationHistory;
        └ private List<CareerInfo> careers;
        */

        // SEQ1. Retrieve User
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        // SEQ2. Save DevSpecEducation
        // NOTICE: N+1 이슈 대응




        // 이걸 Bulk로 한번에 처리할 수는 없을까? 어차피 모든게 jobCode로 분류되고,

        // SEQ3. Save DevSpecJob
        // SEQ4. Save DevSpec
        // SEQ5. Return DevSpecResponse

        throw new UnsupportedOperationException("updateDevSpec not implemented");
    }

    // ----- Helper Methods ------ //
}
