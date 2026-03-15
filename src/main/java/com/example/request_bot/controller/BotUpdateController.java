package com.example.request_bot.controller;

import com.example.request_bot.service.ConversationService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class BotUpdateController {

    private final ConversationService conversationService;

    public BotUpdateController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public void handle(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            conversationService.onTextMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            conversationService.onCallback(update.getCallbackQuery());
        }
    }
}
