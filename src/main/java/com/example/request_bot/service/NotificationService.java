package com.example.request_bot.service;

import com.example.request_bot.config.BotProperties;
import com.example.request_bot.telegram.RequestTelegramBot;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Service
public class NotificationService {

    private final RequestTelegramBot bot;
    private final BotProperties botProperties;

    public NotificationService(@Lazy RequestTelegramBot bot, BotProperties botProperties) {
        this.bot = bot;
        this.botProperties = botProperties;
    }

    public void sendText(Long chatId, String text) {
        bot.sendText(chatId, text);
    }

    public void sendText(Long chatId, String text, InlineKeyboardMarkup keyboardMarkup) {
        bot.sendText(chatId, text, keyboardMarkup);
    }

    public void notifyManager(String text) {
        if (botProperties.managerChatId() != null) {
            bot.sendText(botProperties.managerChatId(), text);
        }
    }

    public void notifyManager(String text, InlineKeyboardMarkup keyboardMarkup) {
        if (botProperties.managerChatId() != null) {
            bot.sendText(botProperties.managerChatId(), text, keyboardMarkup);
        }
    }

    public Long getManagerChatId() {
        return botProperties.managerChatId();
    }
}
