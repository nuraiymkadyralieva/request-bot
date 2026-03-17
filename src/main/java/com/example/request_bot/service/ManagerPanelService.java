package com.example.request_bot.service;

import com.example.request_bot.model.Request;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestStatus;
import com.example.request_bot.session.UserSession;
import com.example.request_bot.session.UserState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ManagerPanelService {
    public static final String APPROVE_PREFIX = "approve:";
    public static final String REJECT_PREFIX = "reject:";
    public static final String COMMENT_PREFIX = "comment:";
    public static final String MANAGER_MENU = "manager:menu";
    public static final String MANAGER_PENDING = "manager:pending";
    public static final String MANAGER_REVIEWED = "manager:reviewed";
    public static final String MANAGER_PENDING_FILTER = "manager:pending:";
    public static final String MANAGER_REVIEWED_FILTER = "manager:reviewed:";
    public static final String MANAGER_OPEN_PENDING = "manager:open:pending:";
    public static final String MANAGER_OPEN_REVIEWED = "manager:open:reviewed:";

    private final RequestService requestService;
    private final NotificationService notificationService;
    private final RequestTextFormatter requestTextFormatter;

    public ManagerPanelService(RequestService requestService, NotificationService notificationService,
                               RequestTextFormatter requestTextFormatter) {
        this.requestService = requestService;
        this.notificationService = notificationService;
        this.requestTextFormatter = requestTextFormatter;
    }

    public boolean handlesCallback(String data) {
        return MANAGER_MENU.equals(data)
                || MANAGER_PENDING.equals(data)
                || MANAGER_REVIEWED.equals(data)
                || data.startsWith(APPROVE_PREFIX)
                || data.startsWith(REJECT_PREFIX)
                || data.startsWith(COMMENT_PREFIX)
                || data.startsWith(MANAGER_PENDING_FILTER)
                || data.startsWith(MANAGER_REVIEWED_FILTER)
                || data.startsWith(MANAGER_OPEN_PENDING)
                || data.startsWith(MANAGER_OPEN_REVIEWED);
    }

    public void showManagerPanel(Long chatId, Long telegramId, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        renderManagerView(chatId, messageId, """
Панель руководителя

Выберите раздел:
""".trim(), buildManagerPanelKeyboard());
    }

    public void handleCallback(Long chatId, Long telegramId, Integer messageId, String data, UserSession session) {
        if (data.startsWith(APPROVE_PREFIX)) {
            handleManagerDecision(chatId, telegramId, data.substring(APPROVE_PREFIX.length()), true, messageId, session);
            return;
        }
        if (data.startsWith(REJECT_PREFIX)) {
            handleManagerDecision(chatId, telegramId, data.substring(REJECT_PREFIX.length()), false, messageId, session);
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
            openManagerCard(chatId, telegramId, data.substring(MANAGER_OPEN_PENDING.length()), true, messageId, session);
            return;
        }
        openManagerCard(chatId, telegramId, data.substring(MANAGER_OPEN_REVIEWED.length()), false, messageId, session);
    }

    public void handleManagerComment(Long chatId, Long telegramId, UserSession session, String comment) {
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
        refreshManagerCard(chatId, request, session.getManagerViewMessageId(), session);
        clearManagerView(session);
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

        String label = "ALL".equals(filterKey) ? "Все" : requestTextFormatter.formatRequestPriority(RequestPriority.valueOf(filterKey));
        if (requests.isEmpty()) {
            renderManagerView(chatId, messageId,
                    "Нерассмотренные заявки: " + label + "\n\nПодходящих заявок пока нет.",
                    buildPendingFilterKeyboard());
            return;
        }

        StringBuilder builder = new StringBuilder("Нерассмотренные заявки: ").append(label).append("\n\n");
        for (Request request : requests) {
            builder.append("#").append(request.getId())
                    .append(" | ").append(requestTextFormatter.formatRequestType(request.getType()))
                    .append(" | ").append(requestTextFormatter.formatRequestPriority(request.getPriority()))
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
                    .append(" | ").append(requestTextFormatter.formatRequestType(request.getType()))
                    .append(" | ").append(requestTextFormatter.formatRequestStatus(request.getStatus()))
                    .append(" | ").append(request.getUser().getName())
                    .append('\n');
        }
        renderManagerView(chatId, messageId, builder.toString().trim(), buildReviewedRequestsKeyboard(requests, filterKey));
    }

    private void openManagerCard(Long chatId, Long telegramId, String payload, boolean pending, Integer messageId,
                                 UserSession session) {
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
        session.setManagerViewMessageId(messageId);
        session.setManagerViewPending(pending);
        session.setManagerViewFilter(parts[0]);
        renderManagerView(chatId, messageId, buildManagerNotification(request.getUser(), request),
                buildManagerRequestCardKeyboard(request, pending, parts[0]));
    }

    private void handleManagerDecision(Long chatId, Long telegramId, String rawRequestId, boolean approved,
                                       Integer messageId, UserSession session) {
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
            refreshManagerCard(chatId, request, messageId, session);
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
        refreshManagerCard(chatId, request, messageId, session);
    }

    private void handleManagerCommentStart(Long chatId, Long telegramId, UserSession session, String rawRequestId,
                                           Integer messageId) {
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

    private void refreshManagerCard(Long chatId, Request request, Integer messageId, UserSession session) {
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
        renderManagerView(chatId, targetMessageId, buildManagerNotification(request.getUser(), request),
                buildManagerRequestCardKeyboard(request, pending, filter));
    }

    private void clearManagerView(UserSession session) {
        session.setManagerViewMessageId(null);
        session.setManagerViewFilter(null);
        session.setManagerViewPending(false);
    }

    private boolean isManager(Long chatId, Long telegramId) {
        Long managerChatId = notificationService.getManagerChatId();
        return managerChatId != null && (managerChatId.equals(chatId) || managerChatId.equals(telegramId));
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

    private void renderManagerView(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        if (messageId == null) {
            notificationService.sendText(chatId, text, keyboard);
        } else {
            notificationService.editText(chatId, messageId, text, keyboard);
        }
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

    private String formatReviewedFilter(String filter) {
        return switch (filter) {
            case "APPROVED" -> "Одобренные";
            case "REJECTED" -> "Отклоненные";
            default -> "Все";
        };
    }
}
