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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

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
    private static final String MANAGER_MENU = "manager:menu";
    private static final String MANAGER_PENDING = "manager:pending";
    private static final String MANAGER_REVIEWED = "manager:reviewed";
    private static final String MANAGER_PENDING_FILTER = "manager:pending:";
    private static final String MANAGER_REVIEWED_FILTER = "manager:reviewed:";
    private static final String MANAGER_OPEN_PENDING = "manager:open:pending:";
    private static final String MANAGER_OPEN_REVIEWED = "manager:open:reviewed:";

    private final SessionStorage sessionStorage;
    private final UserService userService;
    private final RequestService requestService;
    private final NotificationService notificationService;

    public ConversationService(SessionStorage sessionStorage, UserService userService,
                               RequestService requestService, NotificationService notificationService) {
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
            showManagerPanel(chatId, telegramId, null);
            return;
        }

        switch (session.getState()) {
            case WAITING_FOR_NAME -> handleName(chatId, session, text);
            case WAITING_FOR_DEPARTMENT -> handleDepartment(chatId, session, text);
            case WAITING_FOR_POSITION -> handlePosition(chatId, telegramId, session, text);
            case WAITING_FOR_REQUEST_DESCRIPTION -> handleDescription(chatId, session, text);
            case WAITING_FOR_MANAGER_COMMENT -> handleManagerComment(chatId, telegramId, session, text);
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
        if (data.startsWith(APPROVE_PREFIX)) {
            handleManagerDecision(chatId, telegramId, data.substring(APPROVE_PREFIX.length()), true, messageId);
            return;
        }
        if (data.startsWith(REJECT_PREFIX)) {
            handleManagerDecision(chatId, telegramId, data.substring(REJECT_PREFIX.length()), false, messageId);
            return;
        }
        if (data.startsWith(COMMENT_PREFIX)) {
            handleManagerCommentStart(chatId, telegramId, session, data.substring(COMMENT_PREFIX.length()), messageId);
            return;
        }
        if (MANAGER_MENU.equals(data)) {
            showManagerPanel(chatId, telegramId, messageId);
            return;
        }
        if (MANAGER_PENDING.equals(data)) {
            showPendingMenu(chatId, telegramId, messageId);
            return;
        }
        if (MANAGER_REVIEWED.equals(data)) {
            showReviewedMenu(chatId, telegramId, messageId);
            return;
        }
        if (data.startsWith(MANAGER_PENDING_FILTER)) {
            showPendingList(chatId, telegramId, data.substring(MANAGER_PENDING_FILTER.length()), messageId);
            return;
        }
        if (data.startsWith(MANAGER_REVIEWED_FILTER)) {
            showReviewedList(chatId, telegramId, data.substring(MANAGER_REVIEWED_FILTER.length()), messageId);
            return;
        }
        if (data.startsWith(MANAGER_OPEN_PENDING)) {
            openManagerCard(chatId, telegramId, data.substring(MANAGER_OPEN_PENDING.length()), true, messageId);
            return;
        }
        if (data.startsWith(MANAGER_OPEN_REVIEWED)) {
            openManagerCard(chatId, telegramId, data.substring(MANAGER_OPEN_REVIEWED.length()), false, messageId);
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
        List<Request> requests = requestService.getUserRequests(user);
        if (requests.isEmpty()) {
            notificationService.sendText(chatId, "У вас пока нет заявок.");
            return;
        }
        StringBuilder builder = new StringBuilder("Ваши заявки:\n\n");
        for (Request request : requests) {
            builder.append("#").append(request.getId())
                    .append(" | ").append(formatRequestType(request.getType()))
                    .append(" | ").append(formatRequestStatus(request.getStatus()));
            if (StringUtils.hasText(request.getManagerComment())) {
                builder.append(" | Комментарий: ").append(request.getManagerComment());
            }
            builder.append('\n');
        }
        notificationService.sendText(chatId, builder.toString().trim());
    }

    private void showManagerPanel(Long chatId, Long telegramId, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        renderManagerView(chatId, messageId, """
Панель руководителя

Выберите раздел:
""".trim(), buildManagerPanelKeyboard());
    }

    private void showPendingMenu(Long chatId, Long telegramId, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        renderManagerView(chatId, messageId, """
Нерассмотренные заявки

Выберите срочность:
""".trim(), buildPendingFilterKeyboard());
    }

    private void showReviewedMenu(Long chatId, Long telegramId, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        renderManagerView(chatId, messageId, """
Рассмотренные заявки

Выберите тип:
""".trim(), buildReviewedFilterKeyboard());
    }

    private void showPendingList(Long chatId, Long telegramId, String filter, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        String filterKey = filter.toUpperCase();
        List<Request> requests = "ALL".equals(filterKey)
                ? requestService.getPendingRequests()
                : requestService.getPendingRequestsByPriority(parsePriority(filterKey, chatId));
        if (requests == null) {
            return;
        }

        String label = "ALL".equals(filterKey) ? "Все" : formatRequestPriority(RequestPriority.valueOf(filterKey));
        if (requests.isEmpty()) {
            renderManagerView(chatId, messageId,
                    "Нерассмотренные заявки: " + label + "\n\nПодходящих заявок пока нет.",
                    buildPendingFilterKeyboard());
            return;
        }

        StringBuilder builder = new StringBuilder("Нерассмотренные заявки: ").append(label).append("\n\n");
        for (Request request : requests) {
            builder.append("#").append(request.getId())
                    .append(" | ").append(formatRequestType(request.getType()))
                    .append(" | ").append(formatRequestPriority(request.getPriority()))
                    .append(" | ").append(request.getUser().getName())
                    .append('\n');
        }
        renderManagerView(chatId, messageId, builder.toString().trim(), buildPendingRequestsKeyboard(requests, filterKey));
    }

    private void showReviewedList(Long chatId, Long telegramId, String filter, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        String filterKey = filter.toUpperCase();
        List<Request> requests = "ALL".equals(filterKey)
                ? requestService.getReviewedRequests()
                : requestService.getReviewedRequestsByStatus(parseReviewedStatus(filterKey, chatId));
        if (requests == null) {
            return;
        }

        String label = formatReviewedFilter(filterKey);
        if (requests.isEmpty()) {
            renderManagerView(chatId, messageId,
                    "Рассмотренные заявки: " + label + "\n\nПодходящих заявок пока нет.",
                    buildReviewedFilterKeyboard());
            return;
        }

        StringBuilder builder = new StringBuilder("Рассмотренные заявки: ").append(label).append("\n\n");
        for (Request request : requests) {
            builder.append("#").append(request.getId())
                    .append(" | ").append(formatRequestType(request.getType()))
                    .append(" | ").append(formatRequestStatus(request.getStatus()))
                    .append(" | ").append(request.getUser().getName())
                    .append('\n');
        }
        renderManagerView(chatId, messageId, builder.toString().trim(), buildReviewedRequestsKeyboard(requests, filterKey));
    }

    private void openManagerCard(Long chatId, Long telegramId, String payload, boolean pending, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            notificationService.sendText(chatId, "Не удалось открыть заявку.");
            return;
        }
        Request request = getRequest(parts[1], chatId);
        if (request == null) {
            return;
        }
        UserSession session = sessionStorage.getSession(telegramId);
        session.setManagerViewMessageId(messageId);
        session.setManagerViewPending(pending);
        session.setManagerViewFilter(parts[0]);
        renderManagerView(chatId, messageId,
                buildManagerNotification(request.getUser(), request),
                buildManagerRequestCardKeyboard(request, pending, parts[0]));
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

    private void handleManagerDecision(Long chatId, Long telegramId, String rawRequestId, boolean approved, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Только руководитель может использовать эти действия.");
            return;
        }
        Request request = getRequest(rawRequestId, chatId);
        if (request == null) {
            return;
        }
        if (request.getStatus() == RequestStatus.APPROVED || request.getStatus() == RequestStatus.REJECTED) {
            notificationService.sendText(chatId, "Эта заявка уже обработана.");
            refreshManagerCard(chatId, telegramId, request, messageId);
            return;
        }
        if (approved) {
            requestService.approve(request);
            notificationService.sendText(chatId, "Заявка #" + request.getId() + " одобрена.");
            notificationService.sendText(request.getUser().getChatId(), "Ваша заявка #" + request.getId() + " одобрена.");
        } else {
            requestService.reject(request);
            notificationService.sendText(chatId, "Заявка #" + request.getId() + " отклонена.");
            notificationService.sendText(request.getUser().getChatId(), "Ваша заявка #" + request.getId() + " отклонена.");
        }
        refreshManagerCard(chatId, telegramId, request, messageId);
    }

    private void handleManagerCommentStart(Long chatId, Long telegramId, UserSession session, String rawRequestId, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Только руководитель может использовать эти действия.");
            return;
        }
        Request request = getRequest(rawRequestId, chatId);
        if (request == null) {
            return;
        }
        session.setRequestIdForComment(request.getId());
        session.setState(UserState.WAITING_FOR_MANAGER_COMMENT);
        session.setManagerViewMessageId(messageId);
        session.setManagerViewPending(request.getStatus() == RequestStatus.IN_REVIEW);
        if (!StringUtils.hasText(session.getManagerViewFilter())) {
            session.setManagerViewFilter(request.getStatus() == RequestStatus.IN_REVIEW ? "ALL" : request.getStatus().name());
        }
        notificationService.sendText(chatId, "Введите комментарий для заявки #" + request.getId() + ":");
    }

    private void handleManagerComment(Long chatId, Long telegramId, UserSession session, String comment) {
        if (!isManager(chatId, telegramId)) {
            session.setState(UserState.IDLE);
            session.setRequestIdForComment(null);
            notificationService.sendText(chatId, "Только руководитель может добавить комментарий.");
            return;
        }
        if (!StringUtils.hasText(comment)) {
            notificationService.sendText(chatId, "Комментарий не должен быть пустым. Введите комментарий:");
            return;
        }
        Long requestId = session.getRequestIdForComment();
        if (requestId == null) {
            session.setState(UserState.IDLE);
            notificationService.sendText(chatId, "Не удалось определить заявку для комментария.");
            return;
        }
        Request request = requestService.getById(requestId);
        requestService.addManagerComment(request, comment);
        session.setState(UserState.IDLE);
        session.setRequestIdForComment(null);
        notificationService.sendText(chatId, "Комментарий отправлен для заявки #" + request.getId());
        notificationService.sendText(request.getUser().getChatId(), """
Комментарий руководителя к заявке #%d:

%s
""".formatted(request.getId(), comment).trim());
        refreshManagerCard(chatId, telegramId, request, session.getManagerViewMessageId());
        session.setManagerViewMessageId(null);
        session.setManagerViewFilter(null);
        session.setManagerViewPending(false);
    }

    private Request getRequest(String rawRequestId, Long chatId) {
        try {
            return requestService.getById(Long.valueOf(rawRequestId));
        } catch (Exception exception) {
            notificationService.sendText(chatId, "Заявка не найдена.");
            return null;
        }
    }

    private RequestPriority parsePriority(String rawPriority, Long chatId) {
        try {
            return RequestPriority.valueOf(rawPriority);
        } catch (Exception exception) {
            notificationService.sendText(chatId, "Не удалось определить срочность.");
            return null;
        }
    }

    private RequestStatus parseReviewedStatus(String rawStatus, Long chatId) {
        try {
            return RequestStatus.valueOf(rawStatus);
        } catch (Exception exception) {
            notificationService.sendText(chatId, "Не удалось определить тип списка.");
            return null;
        }
    }

    private boolean isManager(Long chatId, Long telegramId) {
        Long managerChatId = notificationService.getManagerChatId();
        return managerChatId != null && (managerChatId.equals(chatId) || managerChatId.equals(telegramId));
    }

    private void renderManagerView(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        if (messageId == null) {
            notificationService.sendText(chatId, text, keyboard);
        } else {
            notificationService.editText(chatId, messageId, text, keyboard);
        }
    }

    private void refreshManagerCard(Long chatId, Long telegramId, Request request, Integer messageId) {
        UserSession session = sessionStorage.getSession(telegramId);
        Integer targetMessageId = messageId != null ? messageId : session.getManagerViewMessageId();
        if (targetMessageId == null) {
            return;
        }

        String filter = session.getManagerViewFilter();
        boolean pending = session.isManagerViewPending();
        if (!StringUtils.hasText(filter)) {
            filter = pending ? "ALL" : request.getStatus().name();
        }
        pending = request.getStatus() == RequestStatus.IN_REVIEW && pending;

        renderManagerView(
                chatId,
                targetMessageId,
                buildManagerNotification(request.getUser(), request),
                buildManagerRequestCardKeyboard(request, pending, filter)
        );
    }

    private String buildDraftSummary(RequestDraft draft) {
        return """
Проверьте заявку:

Тип: %s
Описание: %s
Срочность: %s
""".formatted(formatRequestType(draft.getType()), draft.getDescription(), formatRequestPriority(draft.getPriority())).trim();
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
                .keyboardRow(new InlineKeyboardRow(button("Одобрить", APPROVE_PREFIX + requestId),
                        button("Отклонить", REJECT_PREFIX + requestId),
                        button("Комментарий", COMMENT_PREFIX + requestId)))
                .build();
    }

    private InlineKeyboardMarkup buildManagerPanelKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(button("Нерассмотренные", MANAGER_PENDING),
                        button("Рассмотренные", MANAGER_REVIEWED)))
                .build();
    }

    private InlineKeyboardMarkup buildPendingFilterKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(button("Все", MANAGER_PENDING_FILTER + "ALL"),
                        button("Высокая", MANAGER_PENDING_FILTER + RequestPriority.HIGH.name())))
                .keyboardRow(new InlineKeyboardRow(button("Средняя", MANAGER_PENDING_FILTER + RequestPriority.MEDIUM.name()),
                        button("Низкая", MANAGER_PENDING_FILTER + RequestPriority.LOW.name())))
                .keyboardRow(new InlineKeyboardRow(button("Назад", MANAGER_MENU)))
                .build();
    }

    private InlineKeyboardMarkup buildReviewedFilterKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(button("Все", MANAGER_REVIEWED_FILTER + "ALL"),
                        button("Одобренные", MANAGER_REVIEWED_FILTER + RequestStatus.APPROVED.name())))
                .keyboardRow(new InlineKeyboardRow(button("Отклоненные", MANAGER_REVIEWED_FILTER + RequestStatus.REJECTED.name())))
                .keyboardRow(new InlineKeyboardRow(button("Назад", MANAGER_MENU)))
                .build();
    }

    private InlineKeyboardMarkup buildPendingRequestsKeyboard(List<Request> requests, String filter) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Request request : requests) {
            rows.add(new InlineKeyboardRow(button("Открыть #" + request.getId(),
                    MANAGER_OPEN_PENDING + filter + ":" + request.getId())));
        }
        rows.add(new InlineKeyboardRow(button("К фильтрам", MANAGER_PENDING)));
        rows.add(new InlineKeyboardRow(button("В панель", MANAGER_MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup buildReviewedRequestsKeyboard(List<Request> requests, String filter) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Request request : requests) {
            rows.add(new InlineKeyboardRow(button("Открыть #" + request.getId(),
                    MANAGER_OPEN_REVIEWED + filter + ":" + request.getId())));
        }
        rows.add(new InlineKeyboardRow(button("К фильтрам", MANAGER_REVIEWED)));
        rows.add(new InlineKeyboardRow(button("В панель", MANAGER_MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup buildManagerRequestCardKeyboard(Request request, boolean pending, String filter) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (pending && request.getStatus() == RequestStatus.IN_REVIEW) {
            rows.add(new InlineKeyboardRow(button("Одобрить", APPROVE_PREFIX + request.getId()),
                    button("Отклонить", REJECT_PREFIX + request.getId()),
                    button("Комментарий", COMMENT_PREFIX + request.getId())));
        }
        rows.add(new InlineKeyboardRow(button("Назад к списку",
                (pending ? MANAGER_PENDING_FILTER : MANAGER_REVIEWED_FILTER) + filter)));
        rows.add(new InlineKeyboardRow(button("В панель", MANAGER_MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
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
""".formatted(user.getName(), user.getDepartment(), user.getPosition(), formatRequestType(request.getType()),
                formatRequestPriority(request.getPriority()), request.getDescription(), request.getId(),
                formatRequestStatus(request.getStatus())).trim();
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

    private String formatReviewedFilter(String filter) {
        return switch (filter) {
            case "APPROVED" -> "Одобренные";
            case "REJECTED" -> "Отклоненные";
            default -> "Все";
        };
    }
}
