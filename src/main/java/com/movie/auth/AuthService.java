package com.movie.auth;

import com.movie.common.util.JwtUtil;
import com.movie.module.user.Role;
import com.movie.module.user.UserRepository;
import com.movie.module.user.dto.AuthResponse;
import com.movie.module.user.entities.User;
import com.movie.theatreowner.model.Owner;
import com.movie.theatreowner.repository.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OwnerRepository ownerRepository;
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder, OwnerRepository ownerRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.ownerRepository = ownerRepository;
    }

    public String signup(String name, String email, String password, String roleInput, String businessName) {

        // 🔐 1. Check existing user
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        // 🔐 2. Default role
        Role role = Role.CUSTOMER;

        if (roleInput != null && roleInput.equalsIgnoreCase("THEATRE_OWNER")) {
            role = Role.THEATRE_OWNER;
        }

        if (roleInput != null && roleInput.equalsIgnoreCase("ADMIN")) {
            throw new RuntimeException("Cannot assign ADMIN role");
        }

        // 👤 3. Create User FIRST
        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .isVerified(true)
                .build();

        userRepository.save(user);

        if (role == Role.THEATRE_OWNER) {

            if (businessName == null || businessName.isBlank()) {
                throw new RuntimeException("Business name is required for theatre owner");
            }

            Owner owner = new Owner();
            owner.setUser(user);
            owner.setBusinessName(businessName);
            owner.setIsVerified(false);

            ownerRepository.save(owner);
        }

        return "User registered successfully";
    }

    public AuthResponse login(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = JwtUtil.generateToken(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .build();
    }
}