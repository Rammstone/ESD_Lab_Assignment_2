# PROMPTS.md — AI Prompt Log

Record every prompt you send to the AI **starting from Slice 2**.  
Slice 1 is done manually — no AI. See `README.md` for the prompt template structure.

> **Rules:**
> - One prompt (+ follow-ups) per slice
> - Use the structured template from the README — free-form prompts are not allowed
> - Do not back-fill this file after the lab

---

## Example — How a good S1 prompt looks (for reference only, S1 is done manually)

```
CONTEXT
Spring Boot 3.x, Spring Security 6, Java 17+, jjwt 0.12.x. No other libraries.
Package layout:
  com.esd.lab2
  ├── config/       SecurityConfig.java, AdminSeeder.java
  ├── controller/   CourseController.java
  ├── service/      CourseService.java
  ├── dto/          CourseRequest.java, CourseResponse.java
  ├── entity/       User.java, Role.java (enum), Course.java
  ├── repository/   UserRepository.java, CourseRepository.java
  ├── exception/    ApiError.java, GlobalExceptionHandler.java,
  │                 DuplicateResourceException.java, InvalidCredentialsException.java,
  │                 ResourceNotFoundException.java
  └── security/     JsonAuthenticationEntryPoint.java, JsonAccessDeniedHandler.java


TASK
Implement only POST /api/auth/signup.
Request: SignupRequest { email (valid email, required), password (required, 8–64 chars),
         fullName (required, not blank, max 100) }.
Response: 201 with UserResponse { id, email, fullName, role, createdAt }.
The new user's role is always STUDENT, even if the request contains a role field.
Store email trimmed and lowercased. Hash the password with the existing PasswordEncoder bean.
If the email already exists, throw DuplicateResourceException.

ALLOWED TO CREATE OR CHANGE
dto/SignupRequest.java, dto/UserResponse.java,
service/AuthService.java (signup method only),
controller/AuthController.java (signup endpoint only)

RULES
- Do not create or modify any file not listed above.
- Use the existing exceptions in com.esd.lab2.exception. Do not create new error classes.
- Use DTOs for request and response. Never return an entity from a controller.
- Use jakarta.validation annotations for input validation.
- Use only APIs that exist in Spring Security 6 and jjwt 0.12. No deprecated methods.
- If something is unclear, add a TODO comment instead of guessing.

OUTPUT
Each file in full, with its path. Then a list of anything you assumed.
```

---

## Slice 2 — Login & JWT (S2)

CONTEXT

Spring Boot 3.2.5, Spring Security 6, Java 17 target, JJWT 0.12.6, PostgreSQL. Use only the dependencies already present in pom.xml.

Package layout:
- `com.esd.lab2.controller`: AuthController
- `com.esd.lab2.service`: AuthService
- `com.esd.lab2.dto`: SignupRequest, UserResponse, LoginRequest, LoginResponse
- `com.esd.lab2.entity`: User, Role
- `com.esd.lab2.repository`: UserRepository
- `com.esd.lab2.exception`: DuplicateResourceException, InvalidCredentialsException, GlobalExceptionHandler
- `com.esd.lab2.security`: JsonAuthenticationEntryPoint, JsonAccessDeniedHandler, JwtService

Existing code that must be preserved:
- S1 signup implementation in AuthService
- S1 signup endpoint in AuthController
- Existing User entity, Role enum, UserRepository, PasswordEncoder bean, and exception classes
- Existing JWT configuration in application.yml

TASK

Implement only Slice 2: POST /api/auth/login and JWT generation.

Contract:
- Request: LoginRequest { email, password }
- Email must be valid and both fields must be nonblank.
- Normalize email by trimming whitespace and converting it to lowercase.
- Find the user by email and verify the submitted password using the injected PasswordEncoder.matches().
- Unknown email and incorrect password must both throw InvalidCredentialsException with exactly "Invalid email or password".
- Generate a signed JWT containing:
  - sub = normalized email
  - role = user's role
  - jti = random UUID
  - iat = issued-at timestamp
  - exp = expiry timestamp
- Read the JWT secret and expiration from application configuration; do not hardcode secrets or expiry values.
- Use JJWT 0.12 APIs and HMAC-SHA256 signing.
- Response: LoginResponse { accessToken, tokenType: "Bearer", expiresIn: configured expiry in seconds }.
- Return HTTP 200 for successful login, 400 for invalid request data, and 401 for invalid credentials.

ALLOWED TO CREATE OR CHANGE

1. dto/LoginRequest.java
2. dto/LoginResponse.java
3. security/JwtService.java
4. service/AuthService.java — add login functionality while preserving signup
5. controller/AuthController.java — add the login endpoint while preserving signup

RULES

- Do not create or modify any file outside the allowed list.
- Reuse existing exceptions; do not create new error classes.
- Never return a User entity from a controller.
- Never put passwords or password hashes in JWT claims.
- Use the configured JWT secret via @Value.
- Use JJWT 0.12 methods: subject(), claim(), id(), issuedAt(), expiration(), signWith(key).
- Do not use deprecated JJWT methods such as setSubject(), setExpiration(), or parserBuilder().
- Do not implement token parsing, JWT filters, protected routes, or SecurityConfig changes; those belong to S3.
- If anything is unclear, add a TODO rather than guessing.

OUTPUT

Provide each allowed file in full with its path, then list assumptions made.

---

## Slice 3 — JWT Filter & Protected API (S3)

### Prompt 1 — JWT Validation and Protected Endpoints

**Context**

The project uses Spring Boot 3.2.5, Spring Security 6, Java 17, JJWT 0.12.6, and PostgreSQL. S1 (signup) and S2 (login and JWT generation) have been implemented and tested successfully.

The application already contains a `JwtService` for token generation, a `UserRepository`, a `UserResponse` DTO, JSON authentication and access-denied handlers, and a `SecurityConfig` with temporary permissive access rules.

**Task**

Implement S3 by adding JWT validation, request authentication, and a protected current-user endpoint.

Requirements:

1. Extend `JwtService` to parse and validate signed JWTs using the configured signing key. Reject tokens with invalid signatures or expired timestamps.
2. Create `JwtAuthenticationFilter` extending `OncePerRequestFilter`. Extract Bearer tokens from the `Authorization` header, validate them, read the subject and role claims, and populate the Spring Security context for valid tokens.
3. If a token is invalid, malformed, or expired, clear the security context and continue the filter chain without treating the request as authenticated. Do not write a custom error response directly from the filter.
4. Update `SecurityConfig` using the Spring Security 6 lambda DSL. Use stateless session management and register the JWT filter before `UsernamePasswordAuthenticationFilter`.
5. Permit only these public endpoints:
   - `POST /api/auth/signup`
   - `POST /api/auth/login`
   - `GET /api/courses`
6. Require authentication for all other requests. Do not make all HTTP methods on `/api/courses` public.
7. Wire the existing `JsonAuthenticationEntryPoint` and `JsonAccessDeniedHandler` through `exceptionHandling(...)`.
8. Create `UserController` with `GET /api/users/me`. Obtain the current user's identity from the authenticated security context, look up the user using `UserRepository`, and return `UserResponse` instead of the entity.
9. Preserve the existing S1 signup and S2 login behavior.

**Constraints**

- Do not add dependencies unless necessary.
- Do not use `WebSecurityConfigurerAdapter`, `antMatchers`, `authorizeRequests`, or the older chained `.and()` configuration style.
- Do not add `UserDetailsService`, `AuthenticationManager`, server-side sessions, or new CORS configuration.
- Do not modify files outside the S3 allowed list.
- Do not implement the ADMIN-only course creation rule; that belongs to S4.

**Testing requirements**

Verify that:
- `GET /api/courses` succeeds without a token.
- `GET /api/users/me` returns HTTP 401 without a token.
- `GET /api/users/me` returns HTTP 200 with a valid token and the correct `UserResponse`.
- Tampered and expired tokens cannot authenticate protected requests.
- Authentication errors use the expected JSON `ApiError` response.
- Signup and login continue to work.

Provide the full contents of each created or modified file and explain the purpose of the JWT service, authentication filter, security configuration, and current-user controller.

### Outcome

The S3 implementation was completed, and the required tests passed. The implementation uses stateless JWT authentication, protects `/api/users/me`, and preserves the specified public endpoints.

---

## Slice 4 — Role Restriction (S4)

### S4 — Restrict Course Creation to ADMIN

**CONTEXT**

Spring Boot 3.x, Spring Security 6, Java 17+, and JJWT 0.12.x. The S1–S3 implementations are complete and their tests pass.

The JWT authentication filter creates authorities using `"ROLE_" + role.name()`. The existing `SecurityConfig` uses the Spring Security 6 `SecurityFilterChain` bean and lambda DSL. The existing `CourseController` already provides `GET /api/courses` and `POST /api/courses`.

**TASK**

Restrict `POST /api/courses` to users with the `ADMIN` role.

Add the following matcher immediately before `.anyRequest().authenticated()`:

```java
.requestMatchers(HttpMethod.POST, "/api/courses").hasRole("ADMIN")
```

**ALLOWED TO CREATE OR CHANGE**

- `src/main/java/com/esd/lab2/config/SecurityConfig.java`
- Only the authorization rules within the existing `filterChain` method need to change.

**RULES**

- Keep the change minimal; do not rewrite the configuration.
- Preserve public access to `POST /api/auth/signup`, `POST /api/auth/login`, and `GET /api/courses`.
- All other endpoints must retain their existing authentication requirements.
- Use Spring Security 6 APIs and the existing JSON authentication and access-denied handlers.
- Do not add dependencies or create new files.
- Ensure the `ADMIN` role matches the `ROLE_ADMIN` authority created by the JWT filter.

**OUTPUT**

Show the minimal code change and explain how `hasRole("ADMIN")` maps to `ROLE_ADMIN`. List the tests needed to verify ADMIN, STUDENT, and unauthenticated access.

---

## Slice 5 — Logout *(stretch, S5)*

**CONTEXT**

Spring Boot 3.x, Spring Security 6, Java 17+, and JJWT 0.12.x. S1–S4 are implemented and tested successfully. The application uses JWT-based authentication with a `JwtService` that generates and validates signed tokens. Each JWT contains a `jti` claim and an expiration (`exp`) claim.

The existing `AuthController` exposes signup and login endpoints. The existing `JwtAuthenticationFilter` validates JWTs and establishes authentication. The security configuration uses a stateless session policy and existing JSON authentication handlers.

**TASK**

Implement server-side JWT invalidation through a logout endpoint.

1. Create `TokenDenylist.java` as a Spring component using a thread-safe `ConcurrentHashMap<String, Instant>`.
2. Implement `revoke(jti, expiry)`, `isRevoked(jti)`, and `cleanExpired()` to store revoked token IDs, check revocation, and remove expired entries.
3. Add `POST /api/auth/logout` to `AuthController`. Extract the Bearer token, validate and parse it using `JwtService`, obtain the JWT ID (`jti`) and expiration time, revoke the token, and return HTTP `204 No Content`.
4. Update `JwtAuthenticationFilter` to check the denylist after parsing the JWT. A revoked token must not authenticate a request; protected endpoints must return HTTP `401 Unauthorized`.

**ALLOWED TO CREATE OR CHANGE**

- `src/main/java/com/esd/lab2/security/TokenDenylist.java`
- `src/main/java/com/esd/lab2/controller/AuthController.java`
- `src/main/java/com/esd/lab2/security/JwtAuthenticationFilter.java`

**RULES**

- Do not modify files outside the allowed list.
- Preserve all existing S1–S4 behavior.
- Do not add dependencies.
- Use `ConcurrentHashMap` for thread-safe access.
- Key the denylist by JWT `jti`, not by email or user ID.
- Validate JWT signatures and expiration using the existing `JwtService`.
- Do not treat client-side token deletion as server-side logout.
- Keep `/api/auth/logout` authenticated; do not add it to `permitAll`.
- Continue using the existing authentication entry point and JSON error handling.
- Remove expired denylist entries when appropriate.
- Use existing classes and APIs rather than duplicating JWT validation logic.

**EXPECTED CONTRACT**

- `POST /api/auth/logout` with a valid Bearer token returns `204 No Content`.
- Reusing that same token on a protected endpoint returns `401 Unauthorized`.
- A newly issued token after login remains usable.
- Public `GET /api/courses` remains accessible without a token.

**TESTING**

Verify logout status, rejection of the revoked token, successful use of a fresh token, and continued access to public endpoints. Run the project tests and check that existing S1–S4 behavior remains intact.

**OUTCOME**

S5 implemented and tests passed. The denylist is stored in application memory and is cleared when the application restarts.
