package com.zimuzeng.outfitapp.user.service;

import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.user.dto.CreateUserRequest;
import com.zimuzeng.outfitapp.user.dto.CreateUserResult;
import com.zimuzeng.outfitapp.user.dto.UserResponse;
import com.zimuzeng.outfitapp.user.model.User;
import com.zimuzeng.outfitapp.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    @Transactional
    public CreateUserResult createUser(CreateUserRequest request) {
        return userRepository.findById(request.id())
                .map(existing -> new CreateUserResult(UserResponse.fromEntity(existing), false))
                .orElseGet(() -> createNewUser(request));
    }

    private CreateUserResult createNewUser(CreateUserRequest request) {
        if (request.email() != null && userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, request.email());
        }

        User user = User.builder()
                .id(request.id())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .build();

        User saved = userRepository.save(user);
        return new CreateUserResult(UserResponse.fromEntity(saved), true);
    }
}
