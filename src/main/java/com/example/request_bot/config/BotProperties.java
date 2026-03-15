package com.example.request_bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram.bot")
public record BotProperties(
        String username,
        String token,
        Long managerChatId
) {
}
