package com.esd.lab2.controller;

import com.esd.lab2.dto.LoginRequest;
import com.esd.lab2.dto.LoginResponse;
import com.esd.lab2.dto.SignupRequest;
import com.esd.lab2.dto.UserResponse;
import com.esd.lab2.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.esd.lab2.security.JwtService;
import com.esd.lab2.security.TokenDenylist;
import io.jsonwebtoken.Claims;
import org.springframework.web.bind.annotation.RequestHeader;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenDenylist tokenDenylist;

    public AuthController(
        AuthService authService,
        JwtService jwtService,
        TokenDenylist tokenDenylist) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.tokenDenylist = tokenDenylist;
    }

    /**
     * S1: POST /api/auth/signup
     * Returns 201 Created on success.
     */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(
            @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.signup(request));
    }

    /**
     * S2: POST /api/auth/login
     * Returns 200 OK with a JWT on success.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authorization) {

        String token = authorization.substring(7).trim();
        Claims claims = jwtService.parseToken(token);

        tokenDenylist.revoke(
                claims.getId(),
                claims.getExpiration().toInstant()
        );

        return ResponseEntity.noContent().build();
    }
}