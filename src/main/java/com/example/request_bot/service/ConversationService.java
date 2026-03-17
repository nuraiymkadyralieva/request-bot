package com.example.request_bot.service;

import com.example.request_bot.dto.RequestDraft;
import com.example.request_bot.model.Request;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestType;
import com.example.request_bot.session.SessionStorage;
import com.example.request_bot.session.UserSession;
import com.example.request_bot.session.UserState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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

    private final SessionStorage sessionStorage;
    private final UserService userService;
    private final RequestService requestService;
    private final NotificationService notificationService;
    private final ManagerPanelService managerPanelService;
    private final RequestTextFormatter requestTextFormatter;

    public ConversationService(SessionStorage sessionStorage,
                               UserService userService,
                               RequestService requestService,
                               NotificationService notificationService,
                               ManagerPanelService managerPanelService,
                               RequestTextFormatter requestTextFormatter) {
        this.sessionStorage = sessionStorage;
        this.userService = userService;
        this.requestService = requestService;
        this.notificationService = notificationService;
        this.managerPanelService = managerPanelService;
        this.requestTextFormatter = requestTextFormatter;
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
/manager_panel - панель руководителя
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
        if ("/manager_panel".equals(text)) {
            managerPanelService.showManagerPanel(chatId, telegramId, null);
            return;
        }

        switch (session.getState()) {
            case WAITING_FOR_NAME -> handleName(chatId, session, text);
            case WAITING_FOR_DEPARTMENT -> handleDepartment(chatId, session, text);
            case WAITING_FOR_POSITION -> handlePosition(chatId, telegramId, session, text);
            case WAITING_FOR_REQUEST_DESCRIPTION -> handleDescription(chatId, session, text);
            case WAITING_FOR_MANAGER_COMMENT -> managerPanelService.handleManagerComment(chatId, telegramId, session, text);
            case WAITING_FOR_REQUEST_TYPE, WAITING_FOR_REQUEST_PRIORITY, WAITING_FOR_REQUEST_CONFIRM ->
                    notificationService.sendText(chatId, "Пожалуйста, используйте кнопки ниже.");
            default -> notificationService.sendText(chatId, "Неизвестная команда. Используйте /help");
        }
    }

    public void onCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
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
        if (managerPanelService.handlesCallback(data)) {
            managerPanelService.handleCallback(chatId, telegramId, messageId, data, session);
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

    private void handleName(Long chatId, UserSession session, String text) {
        if (!StringUtils.hasText(text)) {
            notificationService.sendText(chatId, "ФИО не должно быть пустым. Введите ФИО:");
            return;
        }
        session.setTempName(text);
        session.setState(UserState.WAITING_FOR_DEPARTMENT);
        notificationService.sendText(chatId, "Введите отдел:");
    }

    private void handleDepartment(Long chatId, UserSession session, String text) {
        if (!StringUtils.hasText(text)) {
            notificationService.sendText(chatId, "Отдел не должен быть пустым. Введите отдел:");
            return;
        }
        session.setTempDepartment(text);
        session.setState(UserState.WAITING_FOR_POSITION);
        notificationService.sendText(chatId, "Введите должность:");
    }

    private void handlePosition(Long chatId, Long telegramId, UserSession session, String text) {
        if (!StringUtils.hasText(text)) {
            notificationService.sendText(chatId, "Должность не должна быть пустой. Введите должность:");
            return;
        }
        userService.register(telegramId, chatId, session.getTempName(), session.getTempDepartment(), text);
        session.setState(UserState.IDLE);
        notificationService.sendText(chatId, "Регистрация завершена. Теперь можно использовать /new_request");
    }

    private void handleDescription(Long chatId, UserSession session, String text) {
        if (!StringUtils.hasText(text)) {
            notificationService.sendText(chatId, "Описание не должно быть пустым. Введите описание заявки:");
            return;
        }
        session.getRequestDraft().setDescription(text);
        session.setState(UserState.WAITING_FOR_REQUEST_PRIORITY);
        notificationService.sendText(chatId, "Выберите срочность:", buildPriorityKeyboard());
    }

    private void handleNewRequest(Long chatId, Long telegramId, UserSession session) {
        if (!userService.isRegistered(telegramId)) {
            notificationService.sendText(chatId, "Сначала зарегистрируйтесь через /start");
            return;
        }
        session.setRequestDraft(new RequestDraft());
        session.setState(UserState.WAITING_FOR_REQUEST_TYPE);
        notificationService.sendText(chatId, "Выберите тип заявки:", buildTypeKeyboard());
    }

    private void handleMyRequests(Long chatId, Long telegramId) {
        if (!userService.isRegistered(telegramId)) {
            notificationService.sendText(chatId, "Сначала зарегистрируйтесь через /start");
            return;
        }
        User user = userService.getByTelegramId(telegramId);
        java.util.List<Request> requests = requestService.getUserRequests(user);
        if (requests.isEmpty()) {
            notificationService.sendText(chatId, "У вас пока нет заявок.");
            return;
        }
        notificationService.sendText(chatId, requestTextFormatter.buildUserRequestsList(requests));
    }

    private void handleTypeSelection(Long chatId, UserSession session, String rawType) {
        if (session.getState() != UserState.WAITING_FOR_REQUEST_TYPE || session.getRequestDraft() == null) {
            notificationService.sendText(chatId, "Эта кнопка уже неактуальна. Начните новую заявку через /new_request");
            return;
        }
        try {
            session.getRequestDraft().setType(RequestType.valueOf(rawType));
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
            session.getRequestDraft().setPriority(RequestPriority.valueOf(rawPriority));
            session.setState(UserState.WAITING_FOR_REQUEST_CONFIRM);
            notificationService.sendText(chatId, requestTextFormatter.buildDraftSummary(session.getRequestDraft()), buildConfirmKeyboard());
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
            notificationService.sendText(chatId, "Заявка отправлена. ID: " + request.getId());
            notificationService.notifyManager(buildManagerNotification(user, request), buildManagerKeyboard(request.getId()));
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

    private InlineKeyboardMarkup buildTypeKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(button("Финансы", TYPE_PREFIX + RequestType.FINANCE.name()),
                        button("Оборудование", TYPE_PREFIX + RequestType.EQUIPMENT.name())))
                .keyboardRow(new InlineKeyboardRow(button("Отпуск", TYPE_PREFIX + RequestType.LEAVE.name()),
                        button("Другое", TYPE_PREFIX + RequestType.OTHER.name())))
                .build();
    }

    private InlineKeyboardMarkup buildPriorityKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(button("Низкая", PRIORITY_PREFIX + RequestPriority.LOW.name()),
                        button("Средняя", PRIORITY_PREFIX + RequestPriority.MEDIUM.name()),
                        button("Высокая", PRIORITY_PREFIX + RequestPriority.HIGH.name())))
                .build();
    }

    private InlineKeyboardMarkup buildConfirmKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(button("Отправить", CONFIRM_PREFIX + "SEND"),
                        button("Отменить", CONFIRM_PREFIX + "CANCEL")))
                .build();
    }

    private InlineKeyboardMarkup buildManagerKeyboard(Long requestId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(button("Одобрить", ManagerPanelService.APPROVE_PREFIX + requestId),
                        button("Отклонить", ManagerPanelService.REJECT_PREFIX + requestId),
                        button("Комментарий", ManagerPanelService.COMMENT_PREFIX + requestId)))
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
                requestTextFormatter.formatRequestType(request.getType()),
                requestTextFormatter.formatRequestPriority(request.getPriority()),
                request.getDescription(),
                request.getId(),
                requestTextFormatter.formatRequestStatus(request.getStatus())
        ).trim();
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }
}
