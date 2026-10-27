package com.esd.lab2.security;

import com.esd.lab2.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenDenylist tokenDenylist;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        TokenDenylist tokenDenylist) {
        this.jwtService = jwtService;
        this.tokenDenylist = tokenDenylist;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7).trim();

        try {
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Empty bearer token");
            }

            Claims claims = jwtService.parseToken(token);

            String jti = claims.getId();

            if (tokenDenylist.isRevoked(jti)) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            String email = claims.getSubject();
            String roleClaim = claims.get("role", String.class);

            if (email == null || email.isBlank()
                    || roleClaim == null || roleClaim.isBlank()) {
                throw new IllegalArgumentException("Required JWT claims are missing");
            }

            Role role = Role.valueOf(roleClaim);

            var authentication = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
            );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException ex) {
            // Do not trust an invalid token. Let Spring Security handle
            // access to protected endpoints through the configured entry point.
            SecurityContextHolder.clearContext();
        }

        // The filter must not write its own error response.
        // Protected requests without valid authentication will be rejected
        // by Spring Security; public requests can continue normally.
        filterChain.doFilter(request, response);
    }
}