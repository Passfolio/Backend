package com.capstone.passfolio.domain.user.controller;

import com.capstone.passfolio.domain.user.dto.UserDto;
import com.capstone.passfolio.domain.user.service.UserService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController implements UserApiSpecification {

    private final UserService userService;

    @Override
    @GetMapping("/me")
    public UserDto.UserResponse retrieve(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return userService.retrieveMe(userPrincipal);
    }
}
