package com.rbac.service;

import com.rbac.model.Role;

import java.util.List;
import java.util.Optional;

public interface RoleService {

    List<Role> getAllRoles();

    Optional<Role> findById(Integer id);

    Optional<Role> findByName(String roleName);

    Role save(Role role);
}
