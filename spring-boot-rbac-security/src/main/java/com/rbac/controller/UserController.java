package com.rbac.controller;

import com.rbac.dto.ApiResponse;
import com.rbac.dto.UserProfileDTO;
import com.rbac.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * Get current logged-in user's profile
     * Principal is set by JwtTokenProvider.getAuthentication() in UserAccessFilter
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDTO>> getMyProfile(Principal principal) {
        UserProfileDTO profile = userService.getUserProfile(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    /**
     * Admin only — get all users
     * Access controlled by RBAC via UserAccessFilter + page/role mapping
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserProfileDTO>>> getAllUsers() {
        List<UserProfileDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }
}
