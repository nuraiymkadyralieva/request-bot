package com.example.request_bot.repository;

import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestType;
import com.example.request_bot.model.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);

    List<User> findAllByRoleIn(List<UserRole> roles);

    List<User> findAllByRoleInAndManagedDepartmentIgnoreCase(List<UserRole> roles, String managedDepartment);

    List<User> findAllByRoleInAndManagedRequestType(List<UserRole> roles, RequestType managedRequestType);
}
