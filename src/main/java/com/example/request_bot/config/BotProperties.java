package com.example.request_bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "telegram.bot")
public record BotProperties(
        String username,
        String token,
        Long managerChatId,
        List<Long> managerChatIds
) {
}
