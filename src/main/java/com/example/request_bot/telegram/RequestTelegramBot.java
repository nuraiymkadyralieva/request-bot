package com.example.request_bot.telegram;

import com.example.request_bot.config.BotProperties;
import com.example.request_bot.controller.BotUpdateController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class RequestTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private static final Logger log = LoggerFactory.getLogger(RequestTelegramBot.class);

    private final BotProperties botProperties;
    private final BotUpdateController botUpdateController;
    private final TelegramClient telegramClient;

    public RequestTelegramBot(BotProperties botProperties,
                              BotUpdateController botUpdateController) {
        this.botProperties = botProperties;
        this.botUpdateController = botUpdateController;
        this.telegramClient = new OkHttpTelegramClient(botProperties.token());
    }

    @Override
    public String getBotToken() {
        return botProperties.token();
    }

    @Override
    public LongPollingSingleThreadUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            botUpdateController.handle(update);
        } catch (Exception exception) {
            log.error("Failed to handle Telegram update", exception);
            Long chatId = extractChatId(update);
            if (chatId != null) {
                safeSendText(chatId, "Произошла ошибка при обработке запроса. Попробуйте ещё раз.");
            }
        }
    }

    public void sendText(Long chatId, String text) {
        sendText(chatId, text, null);
    }

    public void sendText(Long chatId, String text, InlineKeyboardMarkup keyboardMarkup) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboardMarkup)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException exception) {
            log.error("Failed to send Telegram message to chat {}", chatId, exception);
            throw new RuntimeException("Failed to send telegram message", exception);
        }
    }

    public void editText(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboardMarkup) {
        EditMessageText message = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .replyMarkup(keyboardMarkup)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException exception) {
            log.error("Failed to edit Telegram message {} in chat {}", messageId, chatId, exception);
            throw new RuntimeException("Failed to edit telegram message", exception);
        }
    }

    private void safeSendText(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException exception) {
            log.error("Failed to send fallback Telegram message to chat {}", chatId, exception);
        }
    }

    private Long extractChatId(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            return update.getMessage().getChatId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }
}
