# Spring Boot RBAC Security with OAuth2 + JWT

A production-grade Role-Based Access Control (RBAC) system built with Spring Boot 3, Spring Security, OAuth2 (Google), and JWT. Implements a dynamic API-level access control pattern where roles are mapped to allowed API endpoints stored in a database — no hardcoded role checks in the code.

---

## Architecture

```
Client
  │
  ├── GET /oauth2/authorization/google
  │         │
  │    [Google OAuth2]
  │         │
  │    OAuth2LoginSuccessHandler
  │         │── Validates user exists in DB
  │         │── Generates JWT (userId + email + roleId)
  │         └── Redirects to frontend with ?token=JWT
  │
  ├── All subsequent requests: Authorization: Bearer <JWT>
  │         │
  │    UserAccessFilter (extends BasicAuthenticationFilter)
  │         │── Validates JWT signature + expiry
  │         │── Extracts email from JWT claims
  │         │── Looks up user role from DB
  │         │── Fetches allowed API URLs for that role (pages table)
  │         │── AntPathMatcher checks request URI against allowed URLs
  │         └── 403 Forbidden if no match, else continues filter chain
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Security | Spring Security 6, OAuth2 Client |
| Authentication | Google OAuth2 → JWT (JJWT 0.11.5) |
| Authorization | Dynamic RBAC via database role-page mapping |
| ORM | Spring Data JPA + Hibernate |
| Database | MySQL 8 |
| Build | Maven |
| Java | Java 17 |

---

## Database Schema

```sql
-- Roles
CREATE TABLE roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE
);

-- Users (populated via OAuth2 first login or admin pre-registration)
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    role_id INT,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- Pages — each row represents a feature/module and its allowed API URLs
CREATE TABLE pages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    page_name VARCHAR(100),
    url VARCHAR(200),
    api_urls TEXT  -- comma-separated e.g. "/api/users/**,/api/reports/**"
);

-- Maps which roles can access which pages
CREATE TABLE role_page_map (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_id INT NOT NULL,
    page_id INT NOT NULL,
    FOREIGN KEY (role_id) REFERENCES roles(id),
    FOREIGN KEY (page_id) REFERENCES pages(id)
);

-- Sample data
INSERT INTO roles (role_name) VALUES ('ADMIN'), ('MANAGER'), ('VIEWER');

INSERT INTO pages (page_name, url, api_urls) VALUES
    ('User Management', '/users', '/api/users/**'),
    ('Role Management', '/roles', '/api/roles/**'),
    ('Dashboard', '/dashboard', '/api/dashboard/**');

-- ADMIN gets all pages
INSERT INTO role_page_map (role_id, page_id) VALUES (1, 1), (1, 2), (1, 3);
-- MANAGER gets dashboard only
INSERT INTO role_page_map (role_id, page_id) VALUES (2, 3);
-- VIEWER gets dashboard only
INSERT INTO role_page_map (role_id, page_id) VALUES (3, 3);
```

---

## Project Structure

```
src/main/java/com/rbac/
├── RbacSecurityApplication.java
├── config/
│   └── SecurityConfig.java          # Spring Security filter chain + OAuth2 + CORS
├── controller/
│   ├── AuthController.java          # /api/auth/validate, /api/auth/logout
│   ├── UserController.java          # /api/users
│   └── RoleController.java          # /api/roles
├── dto/
│   ├── ApiResponse.java             # Generic API response wrapper
│   └── UserProfileDTO.java
├── model/
│   ├── User.java
│   ├── Role.java
│   ├── Page.java                    # Stores allowed API URLs per feature
│   └── RolePageMap.java             # Role ↔ Page many-to-many
├── repository/
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── PageRepository.java          # findPagesByRoleIds() JPQL query
│   └── RolePageMapRepository.java
├── security/
│   ├── JwtTokenProvider.java        # Generate, validate, parse JWT
│   ├── UserAccessFilter.java        # RBAC filter — checks JWT + DB role-page access
│   └── OAuth2LoginSuccessHandler.java  # Post-OAuth2 JWT generation + redirect
└── service/
    ├── UserService.java
    ├── RoleService.java
    └── impl/
        ├── UserServiceImpl.java
        └── RoleServiceImpl.java
```

---

## Setup & Run

### 1. Google OAuth2 Credentials
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project → APIs & Services → Credentials → OAuth 2.0 Client ID
3. Add `http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI

### 2. Configure application.properties
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/rbac_db
spring.datasource.username=root
spring.datasource.password=your_password

spring.security.oauth2.client.registration.google.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_CLIENT_SECRET

cors.origin.pattern=http://localhost:3000
```

### 3. Run
```bash
mvn clean install
mvn spring-boot:run
```

---

## OAuth2 + JWT Flow

```
1. Frontend redirects to:
   GET http://localhost:8080/oauth2/authorization/google

2. User logs in with Google

3. Spring redirects to OAuth2LoginSuccessHandler:
   - Validates user email exists in users table
   - Generates JWT with: userId, email, roleId
   - Redirects to: http://localhost:3000/login-success?token=<JWT>

4. Frontend stores JWT, sends with every request:
   Authorization: Bearer <JWT>

5. UserAccessFilter on every protected request:
   - Validates JWT
   - Extracts email → fetches user → fetches role
   - Fetches allowed API URLs for role from pages table
   - AntPathMatcher checks if request URI is allowed
   - 403 if not allowed, else passes to controller
```

---

## Key Design Decisions

**Why database-driven RBAC instead of `@PreAuthorize`?**
Hardcoding roles in annotations (`@PreAuthorize("hasRole('ADMIN')")`) requires a redeployment every time access rules change. Storing API URLs in a `pages` table means access rules can be updated at runtime without touching code.

**Why extend `BasicAuthenticationFilter` for `UserAccessFilter`?**
It integrates cleanly into Spring Security's filter chain and has access to the `AuthenticationManager` — allowing the filter to both validate tokens and set the `SecurityContext` in one place.

---

## API Endpoints

| Method | Endpoint | Access |
|--------|----------|--------|
| GET | `/oauth2/authorization/google` | Public — initiates OAuth2 |
| GET | `/api/auth/validate` | Authenticated — validates JWT |
| POST | `/api/auth/logout` | Authenticated |
| GET | `/api/users/me` | Authenticated — own profile |
| GET | `/api/users` | Role-controlled via DB |
| GET | `/api/roles` | Role-controlled via DB |
| POST | `/api/roles` | Role-controlled via DB |
