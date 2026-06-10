# 🔐 Spring Boot RBAC Security — OAuth2 + JWT

![Java](https://img.shields.io/badge/Java-17-orange?style=flat&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?style=flat&logo=springboot)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6-brightgreen?style=flat&logo=springsecurity)
![JWT](https://img.shields.io/badge/JWT-JJWT%200.11.5-blue?style=flat)
![MySQL](https://img.shields.io/badge/MySQL-8-blue?style=flat&logo=mysql)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat)

---

## About This Project

Built this to demonstrate the exact RBAC pattern I implemented in production at an enterprise finance client — where **hardcoding roles in annotations was not an option** because access rules needed to change without redeployment.

The core idea: instead of `@PreAuthorize("hasRole('ADMIN')")` scattered across controllers, every role is mapped to a list of allowed API URL patterns stored in a database. The `UserAccessFilter` checks every incoming request against that mapping dynamically.

**Real-world problem this solves:**
- A new role needs access to 3 APIs → just insert rows in `role_page_map`, no code change needed
- A role loses access to an endpoint → delete the row, takes effect immediately
- New API endpoint added → add it to the relevant page's `api_urls`, done

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
  │         └── 403 Forbidden if no match, else continues to controller
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

## Project Structure

```
src/main/java/com/rbac/
├── RbacSecurityApplication.java
├── config/
│   └── SecurityConfig.java              # Filter chain, OAuth2, CORS
├── controller/
│   ├── AuthController.java              # /api/auth/validate, /api/auth/logout
│   ├── UserController.java              # /api/users, /api/users/me
│   └── RoleController.java              # /api/roles
├── dto/
│   ├── ApiResponse.java                 # Generic response wrapper
│   └── UserProfileDTO.java
├── model/
│   ├── User.java
│   ├── Role.java
│   ├── Page.java                        # Stores allowed API URLs per feature
│   └── RolePageMap.java                 # Role ↔ Page mapping
├── repository/
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── PageRepository.java              # findPagesByRoleIds() JPQL query
│   └── RolePageMapRepository.java
├── security/
│   ├── JwtTokenProvider.java            # Generate, validate, parse JWT
│   ├── UserAccessFilter.java            # RBAC filter — JWT + DB role check
│   └── OAuth2LoginSuccessHandler.java   # Post-login JWT generation + redirect
└── service/
    ├── UserService.java
    ├── RoleService.java
    └── impl/
        ├── UserServiceImpl.java
        └── RoleServiceImpl.java
```

---

## Database Schema

```sql
CREATE TABLE roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    role_id INT,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- Each row = one feature/module + its allowed API URL patterns
CREATE TABLE pages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    page_name VARCHAR(100),
    url VARCHAR(200),
    api_urls TEXT   -- comma-separated, e.g. "/api/users/**,/api/reports/**"
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
    ('User Management', '/users',     '/api/users/**'),
    ('Role Management', '/roles',     '/api/roles/**'),
    ('Dashboard',       '/dashboard', '/api/dashboard/**');

-- ADMIN gets all pages
INSERT INTO role_page_map (role_id, page_id) VALUES (1,1),(1,2),(1,3);
-- MANAGER gets dashboard only
INSERT INTO role_page_map (role_id, page_id) VALUES (2,3);
-- VIEWER gets dashboard only
INSERT INTO role_page_map (role_id, page_id) VALUES (3,3);
```

---

## Setup & Run

### 1. Google OAuth2 Credentials
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project → **APIs & Services** → **Credentials** → **OAuth 2.0 Client ID**
3. Add this as an authorized redirect URI:
```
http://localhost:8080/login/oauth2/code/google
```

### 2. Configure application.properties
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/rbac_db
spring.datasource.username=root
spring.datasource.password=YOUR_DB_PASSWORD

spring.security.oauth2.client.registration.google.client-id=YOUR_GOOGLE_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_GOOGLE_CLIENT_SECRET

cors.origin.pattern=http://localhost:3000
```

### 3. Run
```bash
mvn clean install
mvn spring-boot:run
```

---

## How to Test It

### Step 1 — Seed the database
Run the SQL from the Database Schema section above.

### Step 2 — Start the app
```bash
mvn spring-boot:run
```

### Step 3 — Trigger OAuth2 login
Open this in your browser:
```
http://localhost:8080/oauth2/authorization/google
```
After Google login you'll be redirected to:
```
http://localhost:3000/login-success?token=<JWT>
```

### Step 4 — Use the JWT with curl or Postman
```bash
# Your own profile
curl -H "Authorization: Bearer <YOUR_JWT>" http://localhost:8080/api/users/me

# All users — works only if your role maps to /api/users/**
curl -H "Authorization: Bearer <YOUR_JWT>" http://localhost:8080/api/users

# Returns 403 if your role doesn't have access to this endpoint
curl -H "Authorization: Bearer <YOUR_JWT>" http://localhost:8080/api/roles
```

### Response codes
| Code | Meaning |
|---|---|
| **200** | Valid JWT + role has access |
| **401 Unauthorized** | JWT missing, invalid, or expired |
| **403 Forbidden** | JWT valid but role not mapped to this endpoint |

---

## API Endpoints

| Method | Endpoint | Access |
|---|---|---|
| GET | `/oauth2/authorization/google` | Public — initiates OAuth2 login |
| GET | `/api/auth/validate` | Authenticated — validates JWT, returns profile |
| POST | `/api/auth/logout` | Authenticated |
| GET | `/api/users/me` | Authenticated — returns own profile |
| GET | `/api/users` | Role-controlled via DB mapping |
| GET | `/api/roles` | Role-controlled via DB mapping |
| POST | `/api/roles` | Role-controlled via DB mapping |

---

## Key Design Decision

**Why database-driven RBAC instead of `@PreAuthorize`?**

Hardcoding roles in annotations requires a redeployment every time access rules change. Storing API URL patterns in a `pages` table means access rules update at runtime — insert or delete a row in `role_page_map` and the change is live instantly, with zero code changes.

---

## What I Would Add Next

| Feature | Reason |
|---|---|
| Token refresh endpoint | Current JWTs expire after 24h with no silent refresh |
| Token blacklist on logout | Logout is currently client-side only — revoked tokens stay valid until expiry |
| Audit log with `@Transactional` | Track who accessed what endpoint and when |
| Unit tests for `UserAccessFilter` | Critical security path deserves isolated test coverage |
| Docker Compose setup | One command to spin up app + MySQL |

---

## Author

**Prathamesh Kolhe**
Java Full Stack Developer — Spring Boot · AWS

[![LinkedIn](https://img.shields.io/badge/LinkedIn-kolhe-blue?style=flat&logo=linkedin)](https://linkedin.com/in/kolhe)
[![Email](https://img.shields.io/badge/Email-prathameshkolhe24@gmail.com-red?style=flat&logo=gmail)](mailto:prathameshkolhe24@gmail.com)

---

*This project reflects patterns from real production systems I have built — adapted and sanitized for public demonstration.*
