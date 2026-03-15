package com.example.request_bot.telegram;

import com.example.request_bot.config.BotProperties;
import com.example.request_bot.controller.BotUpdateController;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class RequestTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

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
        botUpdateController.handle(update);
    }

    public void sendText(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException exception) {
            throw new RuntimeException("Failed to send telegram message", exception);
        }
    }
}
