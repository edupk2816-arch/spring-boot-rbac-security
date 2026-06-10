package com.rbac.repository;

import com.rbac.model.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    // Same query approach as your original findPagesByRoleIds
    @Query("SELECT p FROM Page p JOIN RolePageMap rpm ON rpm.page.id = p.id WHERE rpm.role.id IN :roleIds")
    List<Page> findPagesByRoleIds(@Param("roleIds") List<Integer> roleIds);
}
