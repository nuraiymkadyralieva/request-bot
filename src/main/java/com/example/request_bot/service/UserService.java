package com.example.request_bot.service;

import com.example.request_bot.model.User;
import com.example.request_bot.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}
