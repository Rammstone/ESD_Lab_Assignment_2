# REVIEW.md — AI Output Review Log

Complete **one review entry per slice** before moving to the next.  


---

## Slice 1 — Signup (S1)

### A. Bucket Check
List every class and method the AI produced. Tag each one:  
`Controller` / `Service` / `Repository` / `DTO` / `Security` / `Config` / `Exception`

| File / Method | Tag | In Allowed List? |
|---|---|---|
| AuthController | Controller | Yes |
| AuthService | Service | Yes |
| SignupRequest | DTO | Yes |
| UserResponse | DTO | Yes |
| UserRepository | Repository | Yes |
| DuplicateResourceException | Exception | Yes |
| Existing global exception handler | Exception | Yes |


> **Important:** Anything **not** in the allowed list → **reject, re-prompt**. Don't keep it because it works.

### B. Contract Check
Compare against API contract row A1 line by line.

| Check | Pass / Fail |
|---|---|
| Path is `POST /api/auth/signup` | Pass |
| Returns **201** | Pass |
| Response shape: `{ id, email, fullName, role, createdAt }` | Pass |
| No `passwordHash` field in response | Pass |
| Role in response is always `STUDENT` | Pass |

### C. Validation & Errors

| Check | Pass / Fail |
|---|---|
| `@Valid` on controller parameter | Pass |
| `@Email`, `@NotBlank`, `@Size` on DTO fields | Pass |
| Duplicate email throws `DuplicateResourceException` | Pass |
| No new error/response wrapper class invented | Pass |
| No stack trace or exception message leaked to client | Pass |

### D. Security Specifics

| Check | Pass / Fail |
|---|---|
| Password hashed via injected `PasswordEncoder` (not `new BCryptPasswordEncoder()`) | Pass |
| `SignupRequest` has no `role` field, or it is ignored | Pass |
| Duplicate check uses normalised (lowercased/trimmed) email | Pass |
| AI did **not** touch `SecurityConfig`, add JWT class, or add `UserDetailsService` | Pass |

### E. Understanding Questions
*Answer in your own words (1–2 lines each)*

1. Where exactly is the password hashed, and why is the same password stored as a different string for two users?

   The password is hashed in AuthService using the injected PasswordEncoder before the user is saved. BCrypt uses a random salt, so the same password can produce different stored hashes.

2. What would happen if `SignupRequest` accepted a `role` field and the AI used it?

   A malicious user could submit ADMIN as their role and potentially gain unauthorized privileges. The server must assign the role itself rather than trust user input.

3. Which class turns `DuplicateResourceException` into a 409 response?

   The existing global exception handler, usually named something like GlobalExceptionHandler, handles DuplicateResourceException and maps it to HTTP 409 Conflict.

### F. Decision

| File | Decision | Reason (if modified/rejected) |
|---|---|---|
| AuthController.java | Accepted | Correct signup endpoint and HTTP status |
| AuthService.java | Accepted | Normalizes email, checks duplicates, hashes password, assigns role |
| SignupRequest.java | Accepted | Request validation is present |
| UserResponse.java | Accepted | Returns required fields without password hash |
| DuplicateResourceException.java | Accepted | Used for duplicate email errors |
| Existing exception handler | Accepted | Maps the duplicate exception to HTTP 409 |

---

## Slice 2 — Login & JWT (S2)

### A. Bucket Check
File / Method	               Tag	     In Allowed List?
LoginRequest.java	            DTO	      Yes
LoginResponse.java	         DTO	      Yes
JwtService.java	            Security	   Yes
AuthService.java — login()	   Service	   Yes
AuthController.java —login()	Controller	Yes

### B. Contract Check

Check	                                 Result
Path is POST /api/auth/login	         Pass
Returns HTTP 200	                     Pass
Response contains accessToken,         Pass
tokenType: "Bearer", and expiresIn
expiresIn matches configured value     Pass

### C. Validation & Errors

| Check | Pass / Fail |
|---|---|
| Same 401 message for unknown email and wrong password | Pass |
| No `try/catch` swallowing exceptions silently | Pass |

### D. Security Specifics

| Check | Pass / Fail |
|---|---|
| JWT secret comes from `@Value`, no string literal secret | Pass |
| Uses jjwt 0.12 API: `.subject()`, `.claim()`, `.id()`, `.issuedAt()`, `.expiration()`, `.signWith(key)` | Pass |
| Rejects deprecated: `setSubject`, `setExpiration`, `signWith(SignatureAlgorithm.HS256, secret)`, `Jwts.parserBuilder()` | Pass |
| `jti` is a random UUID (not the user id) | Pass |
| No password or hash in token claims | Pass |
| Parse/validate method **not** written yet (that is S3) | Pass |

### E. Understanding Questions

1. Why must the error message be identical for "no such email" and "wrong password"?

   If the server returns different messages, an attacker can discover which email addresses are registered. Returning the same message makes account enumeration more difficult.

2. Who can read the claims inside your JWT? Who can change them?

   Anyone who obtains the token can decode and read its payload because a normal signed JWT is not encrypted. A client can create a modified payload, but cannot make it pass signature verification without the signing key.

3. What does `jti` give us that `sub` doesn't? (Think about S5.)

   sub identifies the user, whereas jti identifies an individual token. This lets a future logout implementation invalidate one particular token without automatically invalidating every token belonging to that user.

### F. Decision

      File	            Decision	               Reason
LoginRequest.java	      Accepted    	Defines validated login input
LoginResponse.java	   Accepted	      Returns the required token response fields
JwtService.java	      Accepted	      Generates a signed JWT using configured settings
AuthService.java	      Accepted	      Adds credential verification while preserving signup
AuthController.java	   Accepted	      Exposes the login endpoint and preserves signup

---

## Slice 3 — JWT Filter & Protected API (S3)

### A. Bucket Check

      File / Method	                           Tag	   In Allowed List?
JwtService.parseToken()	                     Security	         Yes
JwtAuthenticationFilter.doFilterInternal()	Security	         Yes
SecurityConfig.filterChain()	               Config	         Yes
UserController.getCurrentUser()	            Controller	      Yes

The existing JwtService.generateToken() and PasswordEncoder bean were preserved.

### B. Contract Check

| Check | Pass / Fail |
|---|---|
| `GET /api/courses` works **without** token | Pass |
| `GET /api/users/me` returns **401** with no token | Pass |
| `GET /api/users/me` returns **200** with valid token | Pass |
| 401 body is an `ApiError` JSON object | Pass |

### C. Validation & Errors

| Check | Pass / Fail |
|---|---|
| Filter does not write a response on error — continues chain | Pass |
| Filter clears SecurityContext on bad token | Pass |

### D. Security Specifics

| Check | Pass / Fail |
|---|---|
| Spring Security 6 style: lambda DSL, `requestMatchers`, `authorizeHttpRequests` | Pass |
| Rejected: `WebSecurityConfigurerAdapter`, `antMatchers`, `authorizeRequests`, `.and()` | Pass |
| `permitAll` covers only the three specific public endpoints | Pass |
| `GET /api/courses` is public but `POST /api/courses` is NOT (watch for broad matcher) | Pass |
| Signature + expiry verified with `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` | Pass |
| Authorities use `ROLE_` prefix | Pass |
| Entry point and access-denied handler wired via `exceptionHandling(...)` | Pass |
| No `UserDetailsService`, `AuthenticationManager`, sessions or CORS added | Pass |
| `GET /api/users/me` returns `UserResponse`, not the entity | Pass |

### E. Understanding Questions

1. Why is CSRF protection disabled here, and when would disabling it be wrong?

   CSRF protection is disabled because this API uses stateless authentication with JWTs supplied explicitly in the Authorization: Bearer header.Disabling CSRF can be inappropriate if authentication relies on automatically sent cookies or sessions, because a malicious site could potentially cause an authenticated browser to send unwanted requests.

2. Walk through what happens to a request with an expired token, step by step, until the client sees 401.

   - The client sends the expired JWT in the Authorization header.
   - The JWT filter extracts the token and passes it to JwtService.parseToken().
   - The JJWT parser checks the signature and expiry and throws a JWT exception because the token has expired.
   - The filter clears the security context and continues the filter chain without authenticating the request.
   - When Spring Security reaches a protected endpoint, it finds no authenticated principal and invokes the configured authentication entry point, which returns HTTP 401 with the JSON error body.

3. Why don't 401/403 errors from the filter chain reach `GlobalExceptionHandler`?

   GlobalExceptionHandler handles exceptions raised during Spring MVC controller processing. Authentication and authorization failures are handled earlier in the Spring Security filter chain, before the request reaches the controller. Therefore, Spring Security uses its configured AuthenticationEntryPoint for 401 errors and AccessDeniedHandler for 403 errors instead of relying on the MVC exception handler.

### F. Decision

      File	                     Decision	                                       Reason
JwtService.java	               Accepted	      Validates signed tokens and expiry while preserving token generation
JwtAuthenticationFilter.java	   Accepted	      Extracts Bearer tokens and establishes authentication only for valid tokens
SecurityConfig.java	            Accepted	      Enforces the required public and protected endpoint rules using the Spring Security 6 DSL
UserController.java	            Accepted	      Returns the authenticated user's UserResponse without exposing the entity
---

## Slice 4 — Role Restriction (S4)

### A. Bucket Check

| File / Method | Tag | In Allowed List? |
|SecurityConfig.filterChain()|Config|Yes|
|JwtAuthenticationFilter.doFilterInternal()|Security|Yes|
|CourseController course-creation endpoint|Controller|Yes|
| | | |

### B. Contract Check

| Check | Pass / Fail |
|---|---|
| `POST /api/courses` with no token → **401** | Pass |
| `POST /api/courses` with STUDENT token → **403** | Pass |
| `POST /api/courses` with ADMIN token → **201** | Pass |
| 403 body is an `ApiError` JSON object | Pass |

### D. Security Specifics

| Check | Pass / Fail |
|---|---|
| Role naming consistent: `hasRole("ADMIN")` ↔ authority `ROLE_ADMIN` | Pass |
| If `@PreAuthorize` used: `@EnableMethodSecurity` is present | Pass |
| Rule placed **before** `anyRequest().authenticated()` if in SecurityConfig | Pass |
| Diff is small — AI did not rewrite the whole config | Pass |

### E. Understanding Questions

1. A STUDENT calls `POST /api/courses`. Why does the server return 403 and not 401?

   The security rule hasRole("ADMIN") requires ROLE_ADMIN. Since the user is authenticated but lacks the required authority, Spring Security returns 403 Forbidden. A 401 would indicate that the request is unauthenticated or the token is missing or invalid.

2. Where does the role in the JWT come from, and can the client change it?

   The role comes from the user's role stored in the database. During login, JwtService.generateToken() adds the role as a JWT claim using user.getRole().name(). The server signs the token with its secret key. A client can decode a JWT and modify its contents locally, but cannot produce a valid signature for the modified token without the signing key.

### F. Decision

| File | Decision | Reason |
|SecurityConfig.java|Accept|Added a specific ADMIN-only rule for POST /api/courses, preserving public access to the existing permitted endpoints and authentication requirements for other endpoints.|
| | | |

---

## Slice 5 — Logout (S5) *(stretch)*

### A. Bucket Check

| File / Method | Tag | In Allowed List? |
|---|---|---|
|TokenDenylist.revoke() | Security | Yes |
|TokenDenylist.isRevoked() | Security | Yes |
|TokenDenylist.cleanExpired() | Security | Yes |
|AuthController.logout() | Controller | Yes |
|JwtAuthenticationFilter.doFilterInternal() | Security | Yes |

### B. Contract Check

| Check | Pass / Fail |
|---|---|
| `POST /api/auth/logout` returns **204** | Pass |
| Same token returns **401** after logout | Pass |
| Fresh login after logout returns a new working token | Pass |

### D. Security Specifics

| Check | Pass / Fail |
|---|---|
| Logout adds entry to server-side denylist (not just tells client to delete token) | Pass |
| Denylist keyed by **jti**, not email | Pass |
| Map is `ConcurrentHashMap` (thread-safe) | Pass |
| Logout endpoint is NOT under `permitAll` | Pass |
| Filter checks the denylist and treats denylisted jti as invalid | Pass |
| (Bonus) Expired entries are removed from the map | Pass |

### E. Understanding Questions

1. Why does logout need server-side state if JWT is "stateless"?

   A JWT is normally self-contained, so the server can validate it without storing a session. Deleting the token on the client does not invalidate a copied token. A server-side denylist records revoked token IDs (jti) so the server can reject a token before its expiration.

2. What happens to the denylist when the server restarts? What if there are two server instances?

   The denylist is stored in memory, so it is cleared when the application restarts. With two server instances, each instance would have its own separate denylist. A token revoked on one instance might still be accepted by the other

3. Why store the expiry time with each jti?

   A revoked token only needs to remain in the denylist until its natural expiration. Storing the expiry time allows expired entries to be removed once it's crossed it's expiry time.

### F. Decision

| File | Decision | Reason |
|---|---|---|
|TokenDenylist.java | Accept | Uses a thread-safe ConcurrentHashMap keyed by JWT ID (jti), stores expiry times, checks revocation, and supports expired-entry cleanup. |
|AuthController.java | Accept | Adds an authenticated POST /api/auth/logout endpoint that parses the token, revokes its ID, and returns 204 No Content. |
|JwtAuthenticationFilter.java | Accept | Checks the denylist after validating the JWT and prevents revoked tokens from authenticating protected requests. |

---

## No-AI Change Tasks



**Files changed:**

| File | What changed and why |
|---|---|
| | |

**Explanation (your own words):**
> *Describe what you changed and why it works.*
