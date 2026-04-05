package com.doan.WEB_TMDT.module.auth.repository;

import com.doan.WEB_TMDT.module.auth.entity.Role;
import com.doan.WEB_TMDT.module.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Kiểm tra email đã tồn tại chưa
    boolean existsByEmail(String email);

    // Tìm user theo email
    Optional<User> findByEmail(String email);
    
    // Đếm số lượng user theo role
    Long countByRole(Role role);
}
