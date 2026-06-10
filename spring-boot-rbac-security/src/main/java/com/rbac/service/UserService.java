package com.rbac.service;

import com.rbac.dto.UserProfileDTO;
import com.rbac.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<User> findByEmail(String email);

    UserProfileDTO getUserProfile(String email);

    List<UserProfileDTO> getAllUsers();

    User save(User user);
}
