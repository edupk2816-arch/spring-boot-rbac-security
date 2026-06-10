package com.rbac.repository;

import com.rbac.model.RolePageMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePageMapRepository extends JpaRepository<RolePageMap, Integer> {

    List<RolePageMap> findByRoleId(Integer roleId);
}
