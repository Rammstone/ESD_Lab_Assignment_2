package com.esd.lab2.service;

import com.esd.lab2.dto.LoginRequest;
import com.esd.lab2.dto.LoginResponse;
import com.esd.lab2.dto.SignupRequest;
import com.esd.lab2.dto.UserResponse;
import com.esd.lab2.entity.Role;
import com.esd.lab2.entity.User;
import com.esd.lab2.exception.DuplicateResourceException;
import com.esd.lab2.exception.InvalidCredentialsException;
import com.esd.lab2.repository.UserRepository;
import com.esd.lab2.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * S1: Registers a new user. Role is always STUDENT.
     */
    public UserResponse signup(SignupRequest request) {
        String normalisedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalisedEmail)) {
            throw new DuplicateResourceException(
                    "This email is already in use, please use another email");
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                normalisedEmail,
                hashedPassword,
                request.fullName(),
                Role.STUDENT
        );

        User savedUser = userRepository.save(user);
        return toResponse(savedUser);
    }

    /**
     * S2: Verifies credentials and returns a signed JWT.
     */
    public LoginResponse login(LoginRequest request) {
        String normalisedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalisedEmail)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Invalid email or password"));

        if (!passwordEncoder.matches(
                request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(
                    "Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                jwtService.getExpirationSeconds()
        );
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}