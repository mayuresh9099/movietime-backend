package com.movie.auth;

import com.movie.module.user.LoginRequest;
import com.movie.module.user.SignupRequest;
import com.movie.module.user.dto.AuthResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public String signup(@RequestBody SignupRequest request) {

        return authService.signup(
                request.getName(),
                request.getEmail(),
                request.getPassword(), String.valueOf(request.getRole()),request.getBusinessName()

        );
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(
                request.getEmail(),
                request.getPassword()
        );
    }
}