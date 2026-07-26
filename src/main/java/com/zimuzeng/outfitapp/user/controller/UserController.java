package com.zimuzeng.outfitapp.user.controller;

import com.zimuzeng.outfitapp.user.dto.CreateUserRequest;
import com.zimuzeng.outfitapp.user.dto.CreateUserResult;
import com.zimuzeng.outfitapp.user.dto.UserResponse;
import com.zimuzeng.outfitapp.user.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        CreateUserResult result = userService.createUser(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/users/" + result.user().id()))
                    .body(result.user());
        }
        return ResponseEntity.ok(result.user());
    }
}
