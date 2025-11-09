package com.choo.hhbackendlab.repository;

import com.choo.hhbackendlab.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
