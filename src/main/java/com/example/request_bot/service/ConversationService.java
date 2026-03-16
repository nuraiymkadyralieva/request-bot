package com.example.request_bot.service;

import com.example.request_bot.dto.RequestDraft;
import com.example.request_bot.model.Request;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestStatus;
import com.example.request_bot.model.enums.RequestType;
import com.example.request_bot.session.SessionStorage;
import com.example.request_bot.session.UserSession;
import com.example.request_bot.session.UserState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Service
@Transactional
public class ConversationService {
    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String TYPE_PREFIX = "type:";
    private static final String PRIORITY_PREFIX = "priority:";
    private static final String CONFIRM_PREFIX = "confirm:";
    private static final String APPROVE_PREFIX = "approve:";
    private static final String REJECT_PREFIX = "reject:";
    private static final String COMMENT_PREFIX = "comment:";

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
        log.info("Text message from telegramId={} chatId={} state={} text={}", telegramId, chatId, session.getState(), text);

        if (userService.isRegistered(telegramId)) {
            userService.updateChatId(telegramId, chatId);
        }

        if ("/start".equals(text)) {
            handleStart(chatId, telegramId, session);
            return;
        }

        if ("/help".equals(text)) {
            notificationService.sendText(chatId, """
/start - запуск и регистрация
/new_request - создать заявку
/my_requests - мои заявки
/help - помощь
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
                if (!StringUtils.hasText(text)) {
                    notificationService.sendText(chatId, "ФИО не должно быть пустым. Введите ФИО:");
                    return;
                }
                session.setTempName(text);
                session.setState(UserState.WAITING_FOR_DEPARTMENT);
                notificationService.sendText(chatId, "Введите отдел:");
            }
            case WAITING_FOR_DEPARTMENT -> {
                if (!StringUtils.hasText(text)) {
                    notificationService.sendText(chatId, "Отдел не должен быть пустым. Введите отдел:");
                    return;
                }
                session.setTempDepartment(text);
                session.setState(UserState.WAITING_FOR_POSITION);
                notificationService.sendText(chatId, "Введите должность:");
            }
            case WAITING_FOR_POSITION -> {
                if (!StringUtils.hasText(text)) {
                    notificationService.sendText(chatId, "Должность не должна быть пустой. Введите должность:");
                    return;
                }
                userService.register(telegramId, chatId, session.getTempName(), session.getTempDepartment(), text);
                log.info("User {} registered successfully", telegramId);
                session.setState(UserState.IDLE);
                notificationService.sendText(chatId, "Регистрация завершена. Теперь можно использовать /new_request");
            }
            case WAITING_FOR_REQUEST_DESCRIPTION -> {
                if (!StringUtils.hasText(text)) {
                    notificationService.sendText(chatId, "Описание не должно быть пустым. Введите описание заявки:");
                    return;
                }
                session.getRequestDraft().setDescription(text);
                session.setState(UserState.WAITING_FOR_REQUEST_PRIORITY);
                notificationService.sendText(chatId, "Выберите срочность:", buildPriorityKeyboard());
            }
            case WAITING_FOR_MANAGER_COMMENT -> {
                handleManagerComment(chatId, telegramId, session, text);
            }
            case WAITING_FOR_REQUEST_TYPE, WAITING_FOR_REQUEST_PRIORITY, WAITING_FOR_REQUEST_CONFIRM -> {
                notificationService.sendText(chatId, "Пожалуйста, используйте кнопки ниже.");
            }
            default -> notificationService.sendText(chatId, "Неизвестная команда. Используйте /help");
        }
    }

    public void onCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Long telegramId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();
        UserSession session = sessionStorage.getSession(telegramId);
        log.info("Callback from telegramId={} chatId={} state={} data={}", telegramId, chatId, session.getState(), data);

        if (userService.isRegistered(telegramId)) {
            userService.updateChatId(telegramId, chatId);
        }

        if (data == null) {
            notificationService.sendText(chatId, "Получено пустое действие.");
            return;
        }

        if (data.startsWith(TYPE_PREFIX)) {
            handleTypeSelection(chatId, session, data.substring(TYPE_PREFIX.length()));
            return;
        }

        if (data.startsWith(PRIORITY_PREFIX)) {
            handlePrioritySelection(chatId, session, data.substring(PRIORITY_PREFIX.length()));
            return;
        }

        if (data.startsWith(CONFIRM_PREFIX)) {
            handleConfirmation(chatId, telegramId, session, data.substring(CONFIRM_PREFIX.length()));
            return;
        }

        if (data.startsWith(APPROVE_PREFIX)) {
            handleManagerDecision(chatId, telegramId, data.substring(APPROVE_PREFIX.length()), true);
            return;
        }

        if (data.startsWith(REJECT_PREFIX)) {
            handleManagerDecision(chatId, telegramId, data.substring(REJECT_PREFIX.length()), false);
            return;
        }

        if (data.startsWith(COMMENT_PREFIX)) {
            handleManagerCommentStart(chatId, telegramId, session, data.substring(COMMENT_PREFIX.length()));
            return;
        }

        notificationService.sendText(chatId, "Неизвестное действие.");
    }

    private void handleStart(Long chatId, Long telegramId, UserSession session) {
        if (userService.isRegistered(telegramId)) {
            notificationService.sendText(chatId, "Вы уже зарегистрированы.");
            session.setState(UserState.IDLE);
            return;
        }
        session.setState(UserState.WAITING_FOR_NAME);
        notificationService.sendText(chatId, "Добро пожаловать. Введите ФИО:");
    }

    private void handleNewRequest(Long chatId, Long telegramId, UserSession session) {
        if (!userService.isRegistered(telegramId)) {
            notificationService.sendText(chatId, "Сначала зарегистрируйтесь через /start");
            return;
        }
        RequestDraft draft = new RequestDraft();
        session.setRequestDraft(draft);
        session.setState(UserState.WAITING_FOR_REQUEST_TYPE);
        notificationService.sendText(chatId, "Выберите тип заявки:", buildTypeKeyboard());
    }

    private void handleMyRequests(Long chatId, Long telegramId) {
        if (!userService.isRegistered(telegramId)) {
            notificationService.sendText(chatId, "Сначала зарегистрируйтесь через /start");
            return;
        }
        User user = userService.getByTelegramId(telegramId);
        var requests = requestService.getUserRequests(user);
        if (requests.isEmpty()) {
            notificationService.sendText(chatId, "У вас пока нет заявок.");
            return;
        }

        StringBuilder builder = new StringBuilder("Ваши заявки:\n\n");
        for (Request request : requests) {
            builder.append("#")
                    .append(request.getId())
                    .append(" | ")
                    .append(formatRequestType(request.getType()))
                    .append(" | ")
                    .append(formatRequestStatus(request.getStatus()));
            if (StringUtils.hasText(request.getManagerComment())) {
                builder.append(" | Комментарий: ").append(request.getManagerComment());
            }
            builder.append('\n');
        }
        notificationService.sendText(chatId, builder.toString());
    }

    private void handleTypeSelection(Long chatId, UserSession session, String rawType) {
        if (session.getState() != UserState.WAITING_FOR_REQUEST_TYPE || session.getRequestDraft() == null) {
            notificationService.sendText(chatId, "Эта кнопка уже неактуальна. Начните новую заявку через /new_request");
            return;
        }
        try {
            RequestType type = RequestType.valueOf(rawType);
            session.getRequestDraft().setType(type);
            session.setState(UserState.WAITING_FOR_REQUEST_DESCRIPTION);
            notificationService.sendText(chatId, "Введите описание заявки:");
        } catch (IllegalArgumentException exception) {
            notificationService.sendText(chatId, "Неизвестный тип заявки.");
        }
    }

    private void handlePrioritySelection(Long chatId, UserSession session, String rawPriority) {
        if (session.getState() != UserState.WAITING_FOR_REQUEST_PRIORITY || session.getRequestDraft() == null) {
            notificationService.sendText(chatId, "Эта кнопка уже неактуальна. Сначала заново создайте заявку через /new_request.");
            return;
        }
        try {
            RequestPriority priority = RequestPriority.valueOf(rawPriority);
            session.getRequestDraft().setPriority(priority);
            session.setState(UserState.WAITING_FOR_REQUEST_CONFIRM);
            notificationService.sendText(chatId, buildDraftSummary(session.getRequestDraft()), buildConfirmKeyboard());
        } catch (IllegalArgumentException exception) {
            notificationService.sendText(chatId, "Неизвестная срочность.");
        }
    }

    private void handleConfirmation(Long chatId, Long telegramId, UserSession session, String action) {
        if (session.getState() != UserState.WAITING_FOR_REQUEST_CONFIRM || session.getRequestDraft() == null) {
            notificationService.sendText(chatId, "Эта заявка уже подтверждена или отменена. Для новой заявки используйте /new_request.");
            return;
        }
        if ("SEND".equalsIgnoreCase(action)) {
            User user = userService.getByTelegramId(telegramId);
            Request request = requestService.create(user, session.getRequestDraft());
            requestService.moveToReview(request);
            log.info("Request {} created by telegramId={}", request.getId(), telegramId);
            notificationService.sendText(chatId, "Заявка отправлена. ID: " + request.getId());
            notificationService.notifyManager(
                    buildManagerNotification(user, request),
                    buildManagerKeyboard(request.getId())
            );
            session.setState(UserState.IDLE);
            sessionStorage.clearRequestDraft(telegramId);
            return;
        }
        if ("CANCEL".equalsIgnoreCase(action)) {
            session.setState(UserState.IDLE);
            sessionStorage.clearRequestDraft(telegramId);
            notificationService.sendText(chatId, "Создание заявки отменено.");
            return;
        }
        notificationService.sendText(chatId, "Неизвестное действие подтверждения.");
    }

    private void handleManagerDecision(Long chatId, Long telegramId, String rawRequestId, boolean approved) {
        if (!isManager(chatId, telegramId)) {
            log.warn("Unauthorized manager action attempt by telegramId={} chatId={}", telegramId, chatId);
            notificationService.sendText(chatId, "Только руководитель может использовать эти действия.");
            return;
        }
        Request request = getRequestFromCallback(chatId, rawRequestId);
        if (request == null) {
            return;
        }
        if (request.getStatus() == RequestStatus.APPROVED || request.getStatus() == RequestStatus.REJECTED) {
            notificationService.sendText(chatId, "Эта заявка уже обработана.");
            return;
        }
        if (approved) {
            requestService.approve(request);
            log.info("Request {} approved by manager telegramId={}", request.getId(), telegramId);
            notificationService.sendText(chatId, "Заявка #" + request.getId() + " одобрена.");
            notificationService.sendText(request.getUser().getChatId(),
                    "Ваша заявка #" + request.getId() + " одобрена.");
        } else {
            requestService.reject(request);
            log.info("Request {} rejected by manager telegramId={}", request.getId(), telegramId);
            notificationService.sendText(chatId, "Заявка #" + request.getId() + " отклонена.");
            notificationService.sendText(request.getUser().getChatId(),
                    "Ваша заявка #" + request.getId() + " отклонена.");
        }
    }

    private void handleManagerCommentStart(Long chatId, Long telegramId, UserSession session, String rawRequestId) {
        if (!isManager(chatId, telegramId)) {
            log.warn("Unauthorized comment attempt by telegramId={} chatId={}", telegramId, chatId);
            notificationService.sendText(chatId, "Только руководитель может использовать эти действия.");
            return;
        }
        Request request = getRequestFromCallback(chatId, rawRequestId);
        if (request == null) {
            return;
        }
        session.setRequestIdForComment(request.getId());
        session.setState(UserState.WAITING_FOR_MANAGER_COMMENT);
        notificationService.sendText(chatId, "Введите комментарий для заявки #" + request.getId() + ":");
    }

    private void handleManagerComment(Long chatId, Long telegramId, UserSession session, String comment) {
        if (!isManager(chatId, telegramId)) {
            log.warn("Unauthorized manager comment text by telegramId={} chatId={}", telegramId, chatId);
            notificationService.sendText(chatId, "Только руководитель может добавить комментарий.");
            session.setState(UserState.IDLE);
            session.setRequestIdForComment(null);
            return;
        }
        Long requestId = session.getRequestIdForComment();
        if (requestId == null) {
            session.setState(UserState.IDLE);
            notificationService.sendText(chatId, "Не удалось определить заявку для комментария.");
            return;
        }
        if (!StringUtils.hasText(comment)) {
            notificationService.sendText(chatId, "Комментарий не должен быть пустым. Введите комментарий:");
            return;
        }
        Request request = requestService.getById(requestId);
        requestService.addManagerComment(request, comment);
        log.info("Comment added to request {} by manager telegramId={}", request.getId(), telegramId);
        session.setState(UserState.IDLE);
        session.setRequestIdForComment(null);
        notificationService.sendText(chatId, "Комментарий отправлен для заявки #" + request.getId());
        notificationService.sendText(request.getUser().getChatId(),
                """
Комментарий руководителя к заявке #%d:

%s
""".formatted(request.getId(), comment).trim());
    }

    private Request getRequestFromCallback(Long chatId, String rawRequestId) {
        try {
            return requestService.getById(Long.valueOf(rawRequestId));
        } catch (Exception exception) {
            notificationService.sendText(chatId, "Заявка не найдена.");
            return null;
        }
    }

    private boolean isManager(Long chatId, Long telegramId) {
        Long managerChatId = notificationServiceManagerChatId();
        return managerChatId != null && (managerChatId.equals(chatId) || managerChatId.equals(telegramId));
    }

    private Long notificationServiceManagerChatId() {
        return notificationService.getManagerChatId();
    }

    private String buildDraftSummary(RequestDraft draft) {
        return """
Проверьте заявку:

Тип: %s
Описание: %s
Срочность: %s
""".formatted(
                formatRequestType(draft.getType()),
                draft.getDescription(),
                formatRequestPriority(draft.getPriority())
        ).trim();
    }

    private InlineKeyboardMarkup buildTypeKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("Финансы", TYPE_PREFIX + RequestType.FINANCE.name()),
                        button("Оборудование", TYPE_PREFIX + RequestType.EQUIPMENT.name())
                ))
                .keyboardRow(new InlineKeyboardRow(
                        button("Отпуск", TYPE_PREFIX + RequestType.LEAVE.name()),
                        button("Другое", TYPE_PREFIX + RequestType.OTHER.name())
                ))
                .build();
    }

    private InlineKeyboardMarkup buildPriorityKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("Низкая", PRIORITY_PREFIX + RequestPriority.LOW.name()),
                        button("Средняя", PRIORITY_PREFIX + RequestPriority.MEDIUM.name()),
                        button("Высокая", PRIORITY_PREFIX + RequestPriority.HIGH.name())
                ))
                .build();
    }

    private InlineKeyboardMarkup buildConfirmKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("Отправить", CONFIRM_PREFIX + "SEND"),
                        button("Отменить", CONFIRM_PREFIX + "CANCEL")
                ))
                .build();
    }

    private InlineKeyboardMarkup buildManagerKeyboard(Long requestId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("Одобрить", APPROVE_PREFIX + requestId),
                        button("Отклонить", REJECT_PREFIX + requestId),
                        button("Комментарий", COMMENT_PREFIX + requestId)
                ))
                .build();
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private String buildManagerNotification(User user, Request request) {
        return """
Новая заявка

Сотрудник: %s
Отдел: %s
Должность: %s
Тип: %s
Срочность: %s
Описание: %s
ID заявки: %d
Статус: %s
""".formatted(
                user.getName(),
                user.getDepartment(),
                user.getPosition(),
                formatRequestType(request.getType()),
                formatRequestPriority(request.getPriority()),
                request.getDescription(),
                request.getId(),
                formatRequestStatus(request.getStatus())
        ).trim();
    }

    private String formatRequestType(RequestType type) {
        return switch (type) {
            case FINANCE -> "Финансы";
            case EQUIPMENT -> "Оборудование";
            case LEAVE -> "Отпуск";
            case OTHER -> "Другое";
        };
    }

    private String formatRequestPriority(RequestPriority priority) {
        return switch (priority) {
            case LOW -> "Низкая";
            case MEDIUM -> "Средняя";
            case HIGH -> "Высокая";
        };
    }

    private String formatRequestStatus(RequestStatus status) {
        return switch (status) {
            case NEW -> "Новая";
            case IN_REVIEW -> "На рассмотрении";
            case APPROVED -> "Одобрена";
            case REJECTED -> "Отклонена";
        };
    }
}
