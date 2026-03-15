package com.example.request_bot.service;

import com.example.request_bot.dto.RequestDraft;
import com.example.request_bot.model.Request;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestType;
import com.example.request_bot.session.SessionStorage;
import com.example.request_bot.session.UserSession;
import com.example.request_bot.session.UserState;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
public class ConversationService {

    private final SessionStorage sessionStorage;
    private final UserService userService;
    private final RequestService requestService;
    private final NotificationService notificationService;

    public ConversationService(SessionStorage sessionStorage,
                               UserService userService,
                               RequestService requestService,
                               NotificationService notificationService) {
        this.sessionStorage = sessionStorage;
        this.userService = userService;
        this.requestService = requestService;
        this.notificationService = notificationService;
    }

    public void onTextMessage(Message message) {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String text = message.getText();
        UserSession session = sessionStorage.getSession(telegramId);

        if ("/start".equals(text)) {
            handleStart(chatId, telegramId, session);
            return;
        }

        if ("/help".equals(text)) {
            notificationService.sendText(chatId, """
/start - registration
/new_request - create request
/my_requests - my requests
/help - help
""".trim());
            return;
        }

        if ("/new_request".equals(text)) {
            handleNewRequest(chatId, telegramId, session);
            return;
        }

        if ("/my_requests".equals(text)) {
            handleMyRequests(chatId, telegramId);
            return;
        }

        switch (session.getState()) {
            case WAITING_FOR_NAME -> {
                session.setTempName(text);
                session.setState(UserState.WAITING_FOR_DEPARTMENT);
                notificationService.sendText(chatId, "Enter department:");
            }
            case WAITING_FOR_DEPARTMENT -> {
                session.setTempDepartment(text);
                session.setState(UserState.WAITING_FOR_POSITION);
                notificationService.sendText(chatId, "Enter position:");
            }
            case WAITING_FOR_POSITION -> {
                userService.register(telegramId, session.getTempName(), session.getTempDepartment(), text);
                session.setState(UserState.IDLE);
                notificationService.sendText(chatId, "Registration completed. Now you can use /new_request");
            }
            case WAITING_FOR_REQUEST_DESCRIPTION -> {
                session.getRequestDraft().setDescription(text);
                session.setState(UserState.WAITING_FOR_REQUEST_PRIORITY);
                notificationService.sendText(chatId, "Enter priority: LOW, MEDIUM or HIGH");
            }
            case WAITING_FOR_REQUEST_PRIORITY -> {
                try {
                    RequestPriority priority = RequestPriority.valueOf(text.toUpperCase());
                    session.getRequestDraft().setPriority(priority);
                    session.setState(UserState.WAITING_FOR_REQUEST_CONFIRM);
                    notificationService.sendText(chatId, "Confirm sending: type SEND or CANCEL");
                } catch (IllegalArgumentException exception) {
                    notificationService.sendText(chatId, "Unknown priority. Use LOW, MEDIUM or HIGH");
                }
            }
            case WAITING_FOR_REQUEST_CONFIRM -> {
                if ("SEND".equalsIgnoreCase(text)) {
                    User user = userService.getByTelegramId(telegramId);
                    Request request = requestService.create(user, session.getRequestDraft());
                    requestService.moveToReview(request);
                    notificationService.sendText(chatId, "Request sent. ID: " + request.getId());
                    notificationService.notifyManager(buildManagerNotification(user, request));
                    session.setState(UserState.IDLE);
                    sessionStorage.clearRequestDraft(telegramId);
                } else if ("CANCEL".equalsIgnoreCase(text)) {
                    session.setState(UserState.IDLE);
                    sessionStorage.clearRequestDraft(telegramId);
                    notificationService.sendText(chatId, "Request creation cancelled");
                } else {
                    notificationService.sendText(chatId, "Type SEND to submit or CANCEL to stop");
                }
            }
            default -> notificationService.sendText(chatId, "Unknown command. Use /help");
        }
    }

    public void onCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        notificationService.sendText(chatId, "Inline buttons will be added in the next iteration.");
    }

    private void handleStart(Long chatId, Long telegramId, UserSession session) {
        if (userService.isRegistered(telegramId)) {
            notificationService.sendText(chatId, "You are already registered.");
            session.setState(UserState.IDLE);
            return;
        }
        session.setState(UserState.WAITING_FOR_NAME);
        notificationService.sendText(chatId, "Welcome. Enter your full name:");
    }

    private void handleNewRequest(Long chatId, Long telegramId, UserSession session) {
        if (!userService.isRegistered(telegramId)) {
            notificationService.sendText(chatId, "Please register first using /start");
            return;
        }
        RequestDraft draft = new RequestDraft();
        draft.setType(RequestType.OTHER);
        session.setRequestDraft(draft);
        session.setState(UserState.WAITING_FOR_REQUEST_DESCRIPTION);
        notificationService.sendText(chatId,
                "Starting with the text flow for now. Request type is temporarily OTHER. Enter description:");
    }

    private void handleMyRequests(Long chatId, Long telegramId) {
        if (!userService.isRegistered(telegramId)) {
            notificationService.sendText(chatId, "Please register first using /start");
            return;
        }
        User user = userService.getByTelegramId(telegramId);
        var requests = requestService.getUserRequests(user);
        if (requests.isEmpty()) {
            notificationService.sendText(chatId, "You do not have any requests yet.");
            return;
        }

        StringBuilder builder = new StringBuilder("Your requests:\n\n");
        for (Request request : requests) {
            builder.append("#")
                    .append(request.getId())
                    .append(" | ")
                    .append(request.getType())
                    .append(" | ")
                    .append(request.getStatus())
                    .append('\n');
        }
        notificationService.sendText(chatId, builder.toString());
    }

    private String buildManagerNotification(User user, Request request) {
        return """
New request submitted

Employee: %s
Department: %s
Position: %s
Type: %s
Priority: %s
Description: %s
Request ID: %d
Status: %s
""".formatted(
                user.getName(),
                user.getDepartment(),
                user.getPosition(),
                request.getType(),
                request.getPriority(),
                request.getDescription(),
                request.getId(),
                request.getStatus()
        ).trim();
    }
}
