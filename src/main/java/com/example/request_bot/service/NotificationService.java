package com.example.request_bot.service;

import com.example.request_bot.config.BotProperties;
import com.example.request_bot.telegram.RequestTelegramBot;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    public Integer sendTextAndReturnMessageId(Long chatId, String text, InlineKeyboardMarkup keyboardMarkup) {
        return bot.sendTextAndReturnMessageId(chatId, text, keyboardMarkup);
    }

    public void editText(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboardMarkup) {
        bot.editText(chatId, messageId, text, keyboardMarkup);
    }

    public void notifyManager(String text) {
        for (Long chatId : getManagerChatIds()) {
            bot.sendText(chatId, text);
        }
    }

    public void notifyManager(String text, InlineKeyboardMarkup keyboardMarkup) {
        for (Long chatId : getManagerChatIds()) {
            bot.sendText(chatId, text, keyboardMarkup);
        }
    }

    public Long getManagerChatId() {
        return botProperties.managerChatId();
    }

    public List<Long> getManagerChatIds() {
        Set<Long> ids = new LinkedHashSet<>();
        if (botProperties.managerChatId() != null) {
            ids.add(botProperties.managerChatId());
        }
        if (botProperties.managerChatIds() != null) {
            ids.addAll(botProperties.managerChatIds());
        }
        return new ArrayList<>(ids);
    }

    public void notifyChats(List<Long> chatIds, String text, InlineKeyboardMarkup keyboardMarkup) {
        for (Long chatId : chatIds) {
            bot.sendText(chatId, text, keyboardMarkup);
        }
    }
}
