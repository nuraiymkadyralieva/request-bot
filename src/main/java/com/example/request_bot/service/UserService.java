package com.example.request_bot.service;

import com.example.request_bot.config.BotProperties;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestType;
import com.example.request_bot.model.enums.UserRole;
import com.example.request_bot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BotProperties botProperties;

    public UserService(UserRepository userRepository, BotProperties botProperties) {
        this.userRepository = userRepository;
        this.botProperties = botProperties;
    }

    public boolean isRegistered(Long telegramId) {
        return userRepository.existsByTelegramId(telegramId);
    }

    public User getByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    public User register(Long telegramId, Long chatId, String name, String department, String position) {
        User user = new User();
        user.setTelegramId(telegramId);
        user.setChatId(chatId);
        user.setName(name);
        user.setDepartment(department);
        user.setPosition(position);
        user.setRole(UserRole.EMPLOYEE);
        return userRepository.save(user);
    }

    public void updateChatId(Long telegramId, Long chatId) {
        userRepository.findByTelegramId(telegramId).ifPresent(user -> {
            if (!chatId.equals(user.getChatId())) {
                user.setChatId(chatId);
                userRepository.save(user);
            }
        });
    }

    public boolean hasManagerAccess(Long chatId, Long telegramId) {
        if (botProperties.managerChatId() != null
                && (botProperties.managerChatId().equals(chatId) || botProperties.managerChatId().equals(telegramId))) {
            return true;
        }
        if (botProperties.managerChatIds() != null && botProperties.managerChatIds().stream()
                .anyMatch(id -> id.equals(chatId) || id.equals(telegramId))) {
            return true;
        }
        return userRepository.findByTelegramId(telegramId)
                .map(user -> user.getRole() == UserRole.MANAGER || user.getRole() == UserRole.ADMIN)
                .orElseGet(() -> userRepository.findAllByRoleIn(List.of(UserRole.MANAGER, UserRole.ADMIN)).stream()
                        .anyMatch(user -> chatId.equals(user.getChatId())));
    }

    public List<User> findManagersFor(String department, RequestType requestType) {
        List<UserRole> managerRoles = List.of(UserRole.MANAGER, UserRole.ADMIN);
        Map<Long, User> recipients = new LinkedHashMap<>();

        if (department != null && !department.isBlank()) {
            for (User user : userRepository.findAllByRoleInAndManagedDepartmentIgnoreCase(managerRoles, department)) {
                recipients.put(user.getId(), user);
            }
        }
        if (requestType != null) {
            for (User user : userRepository.findAllByRoleInAndManagedRequestType(managerRoles, requestType)) {
                recipients.put(user.getId(), user);
            }
        }
        if (recipients.isEmpty()) {
            for (User user : userRepository.findAllByRoleIn(managerRoles)) {
                recipients.put(user.getId(), user);
            }
        }
        return new ArrayList<>(recipients.values());
    }

    public User updateRole(User user, UserRole role, String managedDepartment, RequestType managedRequestType) {
        user.setRole(role);
        user.setManagedDepartment(managedDepartment);
        user.setManagedRequestType(managedRequestType);
        return userRepository.save(user);
    }
}
