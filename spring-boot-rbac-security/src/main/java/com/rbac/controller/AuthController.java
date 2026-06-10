package com.rbac.controller;

import com.rbac.dto.ApiResponse;
import com.rbac.dto.UserProfileDTO;
import com.rbac.security.JwtTokenProvider;
import com.rbac.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserService userService;

    /**
     * Called by frontend after OAuth2 redirect with ?token=
     * Validates the JWT and returns user profile
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<UserProfileDTO>> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid Authorization header"));
        }

        String token = authHeader.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Invalid or expired token"));
        }

        String email = jwtTokenProvider.getEmailFromToken(token);
        UserProfileDTO profile = userService.getUserProfile(email);

        return ResponseEntity.ok(ApiResponse.success("Token is valid", profile));
    }

    /**
     * Logout endpoint — frontend clears token on client side
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // JWT is stateless — client just discards the token
        // If you add a token blacklist later, handle it here
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
}
