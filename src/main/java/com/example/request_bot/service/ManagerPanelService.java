package com.example.request_bot.service;

import com.example.request_bot.model.Request;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestStatus;
import com.example.request_bot.model.enums.RequestType;
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
    private static final int PAGE_SIZE = 5;

    public static final String APPROVE_PREFIX = "approve:";
    public static final String REJECT_PREFIX = "reject:";
    public static final String COMMENT_PREFIX = "comment:";

    public static final String MANAGER_MENU = "manager:menu";
    public static final String MANAGER_PENDING_MENU = "manager:pending:menu";
    public static final String MANAGER_PENDING_LIST = "manager:pending:list";
    public static final String MANAGER_PENDING_HIGH_LIST = "manager:pending:high";
    public static final String MANAGER_PENDING_PRIORITY_PREFIX = "manager:pending:priority:";
    public static final String MANAGER_REVIEWED_MENU = "manager:reviewed:menu";
    public static final String MANAGER_REVIEWED_LIST = "manager:reviewed:list";
    public static final String MANAGER_REVIEWED_STATUS_PREFIX = "manager:reviewed:status:";
    public static final String MANAGER_TYPE_MENU_PREFIX = "manager:type:menu:";
    public static final String MANAGER_TYPE_SET_PREFIX = "manager:type:set:";
    public static final String MANAGER_SORT_MENU_PREFIX = "manager:sort:menu:";
    public static final String MANAGER_SORT_SET_PREFIX = "manager:sort:set:";
    public static final String MANAGER_SEARCH_START_PREFIX = "manager:search:start:";
    public static final String MANAGER_SEARCH_CLEAR_PREFIX = "manager:search:clear:";
    public static final String MANAGER_OPEN_PREFIX = "manager:open:";
    public static final String MANAGER_BACK_LIST = "manager:back:list";
    public static final String MANAGER_NOOP = "manager:noop";

    private final RequestService requestService;
    private final NotificationService notificationService;
    private final RequestTextFormatter requestTextFormatter;
    private final UserService userService;

    public ManagerPanelService(RequestService requestService,
                               NotificationService notificationService,
                               RequestTextFormatter requestTextFormatter,
                               UserService userService) {
        this.requestService = requestService;
        this.notificationService = notificationService;
        this.requestTextFormatter = requestTextFormatter;
        this.userService = userService;
    }

    public boolean handlesCallback(String data) {
        return MANAGER_MENU.equals(data)
                || MANAGER_PENDING_MENU.equals(data)
                || MANAGER_PENDING_LIST.equals(data)
                || data.startsWith(MANAGER_PENDING_LIST + ":")
                || MANAGER_PENDING_HIGH_LIST.equals(data)
                || MANAGER_REVIEWED_MENU.equals(data)
                || MANAGER_REVIEWED_LIST.equals(data)
                || data.startsWith(MANAGER_REVIEWED_LIST + ":")
                || MANAGER_BACK_LIST.equals(data)
                || MANAGER_NOOP.equals(data)
                || data.startsWith(APPROVE_PREFIX)
                || data.startsWith(REJECT_PREFIX)
                || data.startsWith(COMMENT_PREFIX)
                || data.startsWith(MANAGER_PENDING_PRIORITY_PREFIX)
                || data.startsWith(MANAGER_REVIEWED_STATUS_PREFIX)
                || data.startsWith(MANAGER_TYPE_MENU_PREFIX)
                || data.startsWith(MANAGER_TYPE_SET_PREFIX)
                || data.startsWith(MANAGER_SORT_MENU_PREFIX)
                || data.startsWith(MANAGER_SORT_SET_PREFIX)
                || data.startsWith(MANAGER_SEARCH_START_PREFIX)
                || data.startsWith(MANAGER_SEARCH_CLEAR_PREFIX)
                || data.startsWith(MANAGER_OPEN_PREFIX);
    }

    public void showManagerPanel(Long chatId, Long telegramId, UserSession session, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        resetAllFilters(session);
        session.setState(UserState.IDLE);
        session.setManagerViewMessageId(messageId);
        renderManagerView(chatId, messageId, buildManagerPanelText(), buildManagerPanelKeyboard());
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
            showManagerPanel(chatId, telegramId, session, messageId);
            return;
        }
        if (MANAGER_PENDING_MENU.equals(data)) {
            showPendingMenu(chatId, telegramId, messageId, session);
            return;
        }
        if (MANAGER_PENDING_LIST.equals(data)) {
            showPendingList(chatId, telegramId, messageId, session, 0);
            return;
        }
        if (data.startsWith(MANAGER_PENDING_LIST + ":")) {
            showPendingList(chatId, telegramId, messageId, session, parsePage(data.substring((MANAGER_PENDING_LIST + ":").length())));
            return;
        }
        if (MANAGER_PENDING_HIGH_LIST.equals(data)) {
            preparePendingDefaults(session);
            session.setManagerPriorityFilter(RequestPriority.HIGH);
            showPendingList(chatId, telegramId, messageId, session, 0);
            return;
        }
        if (MANAGER_REVIEWED_MENU.equals(data)) {
            showReviewedMenu(chatId, telegramId, messageId, session);
            return;
        }
        if (MANAGER_REVIEWED_LIST.equals(data)) {
            showReviewedList(chatId, telegramId, messageId, session, 0);
            return;
        }
        if (data.startsWith(MANAGER_REVIEWED_LIST + ":")) {
            showReviewedList(chatId, telegramId, messageId, session, parsePage(data.substring((MANAGER_REVIEWED_LIST + ":").length())));
            return;
        }
        if (MANAGER_BACK_LIST.equals(data)) {
            showCurrentList(chatId, telegramId, messageId, session);
            return;
        }
        if (MANAGER_NOOP.equals(data)) {
            return;
        }
        if (data.startsWith(MANAGER_PENDING_PRIORITY_PREFIX)) {
            setPendingPriority(chatId, telegramId, messageId, session, data.substring(MANAGER_PENDING_PRIORITY_PREFIX.length()));
            return;
        }
        if (data.startsWith(MANAGER_REVIEWED_STATUS_PREFIX)) {
            setReviewedStatus(chatId, telegramId, messageId, session, data.substring(MANAGER_REVIEWED_STATUS_PREFIX.length()));
            return;
        }
        if (data.startsWith(MANAGER_TYPE_MENU_PREFIX)) {
            showTypeMenu(chatId, telegramId, messageId, session, parseScope(data.substring(MANAGER_TYPE_MENU_PREFIX.length())));
            return;
        }
        if (data.startsWith(MANAGER_TYPE_SET_PREFIX)) {
            setTypeFilter(chatId, telegramId, messageId, session, data.substring(MANAGER_TYPE_SET_PREFIX.length()));
            return;
        }
        if (data.startsWith(MANAGER_SORT_MENU_PREFIX)) {
            showSortMenu(chatId, telegramId, messageId, session, parseScope(data.substring(MANAGER_SORT_MENU_PREFIX.length())));
            return;
        }
        if (data.startsWith(MANAGER_SORT_SET_PREFIX)) {
            setSortMode(chatId, telegramId, messageId, session, data.substring(MANAGER_SORT_SET_PREFIX.length()));
            return;
        }
        if (data.startsWith(MANAGER_SEARCH_START_PREFIX)) {
            startSearch(chatId, telegramId, session, data.substring(MANAGER_SEARCH_START_PREFIX.length()), messageId);
            return;
        }
        if (data.startsWith(MANAGER_SEARCH_CLEAR_PREFIX)) {
            clearSearch(chatId, telegramId, messageId, session, data.substring(MANAGER_SEARCH_CLEAR_PREFIX.length()));
            return;
        }
        openManagerCard(chatId, telegramId, messageId, session, data.substring(MANAGER_OPEN_PREFIX.length()));
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
        User actor = resolveActor(telegramId);
        requestService.addManagerComment(request, comment);
        requestService.recordComment(request, actor, comment);
        session.setState(UserState.IDLE);
        session.setRequestIdForComment(null);
        notificationService.sendText(chatId, "Комментарий отправлен для заявки #" + request.getId());
        notificationService.sendText(request.getUser().getChatId(), """
По заявке #%d есть комментарий руководителя.

Тип: %s
Срочность: %s
Статус: %s
Комментарий: %s
""".formatted(
                request.getId(),
                requestTextFormatter.formatRequestType(request.getType()),
                requestTextFormatter.formatRequestPriority(request.getPriority()),
                requestTextFormatter.formatRequestStatus(request.getStatus()),
                comment
        ).trim());
        refreshManagerCard(chatId, request, session.getManagerViewMessageId(), session);
    }

    public void handleManagerSearch(Long chatId, Long telegramId, UserSession session, String query) {
        if (!isManager(chatId, telegramId)) {
            session.setState(UserState.IDLE);
            notificationService.sendText(chatId, "Только руководитель может использовать поиск по заявкам.");
            return;
        }
        if (!StringUtils.hasText(query)) {
            notificationService.sendText(chatId, "Введите имя сотрудника, отдел или часть описания.");
            return;
        }
        session.setManagerSearchQuery(query.trim());
        session.setManagerViewPage(0);
        session.setState(UserState.IDLE);
        notificationService.sendText(chatId, "Поиск обновлен: " + session.getManagerSearchQuery());
        showCurrentList(chatId, telegramId, session.getManagerViewMessageId(), session);
    }

    private void showPendingMenu(Long chatId, Long telegramId, Integer messageId, UserSession session) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        ensurePendingScope(session);
        session.setManagerViewMessageId(messageId);
        renderManagerView(chatId, messageId, buildPendingMenuText(session), buildPendingMenuKeyboard(session));
    }

    private void showReviewedMenu(Long chatId, Long telegramId, Integer messageId, UserSession session) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        ensureReviewedScope(session);
        session.setManagerViewMessageId(messageId);
        renderManagerView(chatId, messageId, buildReviewedMenuText(session), buildReviewedMenuKeyboard(session));
    }

    private void showPendingList(Long chatId, Long telegramId, Integer messageId, UserSession session, int requestedPage) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        ensurePendingScope(session);
        List<Request> requests = requestService.searchPendingRequests(
                session.getManagerSearchQuery(),
                new RequestService.RequestTypeFilter(session.getManagerTypeFilter()),
                session.getManagerPriorityFilter(),
                session.isManagerHighOnly(),
                session.getManagerSortMode()
        );
        session.setManagerViewPage(requestedPage);
        renderRequestList(chatId, messageId, session, requests, true);
    }

    private void showReviewedList(Long chatId, Long telegramId, Integer messageId, UserSession session, int requestedPage) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        ensureReviewedScope(session);
        List<Request> requests = requestService.searchReviewedRequests(
                session.getManagerSearchQuery(),
                session.getManagerReviewedStatus(),
                new RequestService.RequestTypeFilter(session.getManagerTypeFilter()),
                session.getManagerSortMode()
        );
        session.setManagerViewPage(requestedPage);
        renderRequestList(chatId, messageId, session, requests, false);
    }

    private void renderRequestList(Long chatId, Integer messageId, UserSession session, List<Request> requests, boolean pending) {
        int totalPages = pageCount(requests.size());
        int page = normalizePage(session.getManagerViewPage() == null ? 0 : session.getManagerViewPage(), totalPages);
        session.setManagerViewPage(page);
        session.setManagerViewMessageId(messageId);

        if (requests.isEmpty()) {
            renderManagerView(
                    chatId,
                    messageId,
                    pending ? buildPendingEmptyText(session) : buildReviewedEmptyText(session),
                    pending ? buildPendingMenuKeyboard(session) : buildReviewedMenuKeyboard(session)
            );
            return;
        }

        List<Request> pageItems = paginate(requests, page);
        String text = pending
                ? buildPendingListText(session, pageItems, requests.size(), page, totalPages)
                : buildReviewedListText(session, pageItems, requests.size(), page, totalPages);
        InlineKeyboardMarkup keyboard = pending
                ? buildPendingListKeyboard(pageItems, page, totalPages)
                : buildReviewedListKeyboard(pageItems, page, totalPages);
        renderManagerView(chatId, messageId, text, keyboard);
    }

    private void setPendingPriority(Long chatId, Long telegramId, Integer messageId, UserSession session, String rawPriority) {
        ensurePendingScope(session);
        if ("ALL".equalsIgnoreCase(rawPriority)) {
            session.setManagerPriorityFilter(null);
        } else {
            try {
                session.setManagerPriorityFilter(RequestPriority.valueOf(rawPriority));
            } catch (IllegalArgumentException exception) {
                notificationService.sendText(chatId, "Не удалось определить срочность.");
                return;
            }
        }
        session.setManagerViewPage(0);
        showPendingMenu(chatId, telegramId, messageId, session);
    }

    private void setReviewedStatus(Long chatId, Long telegramId, Integer messageId, UserSession session, String rawStatus) {
        ensureReviewedScope(session);
        if ("ALL".equalsIgnoreCase(rawStatus)) {
            session.setManagerReviewedStatus(null);
        } else {
            try {
                session.setManagerReviewedStatus(RequestStatus.valueOf(rawStatus));
            } catch (IllegalArgumentException exception) {
                notificationService.sendText(chatId, "Не удалось определить статус списка.");
                return;
            }
        }
        session.setManagerViewPage(0);
        showReviewedMenu(chatId, telegramId, messageId, session);
    }

    private void showTypeMenu(Long chatId, Long telegramId, Integer messageId, UserSession session, ViewScope scope) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        session.setManagerViewPending(scope == ViewScope.PENDING);
        session.setManagerViewMessageId(messageId);
        renderManagerView(chatId, messageId, "Выберите тип заявки:", buildTypeKeyboard(scope));
    }

    private void setTypeFilter(Long chatId, Long telegramId, Integer messageId, UserSession session, String payload) {
        String[] parts = payload.split(":", 2);
        ViewScope scope = parseScope(parts[0]);
        session.setManagerViewPending(scope == ViewScope.PENDING);
        if (parts.length < 2 || "ALL".equalsIgnoreCase(parts[1])) {
            session.setManagerTypeFilter(null);
        } else {
            try {
                session.setManagerTypeFilter(RequestType.valueOf(parts[1]));
            } catch (IllegalArgumentException exception) {
                notificationService.sendText(chatId, "Не удалось определить тип заявки.");
                return;
            }
        }
        session.setManagerViewPage(0);
        if (scope == ViewScope.PENDING) {
            showPendingMenu(chatId, telegramId, messageId, session);
        } else {
            showReviewedMenu(chatId, telegramId, messageId, session);
        }
    }

    private void showSortMenu(Long chatId, Long telegramId, Integer messageId, UserSession session, ViewScope scope) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        session.setManagerViewPending(scope == ViewScope.PENDING);
        session.setManagerViewMessageId(messageId);
        renderManagerView(chatId, messageId, "Выберите сортировку:", buildSortKeyboard(scope));
    }

    private void setSortMode(Long chatId, Long telegramId, Integer messageId, UserSession session, String payload) {
        String[] parts = payload.split(":", 2);
        ViewScope scope = parseScope(parts[0]);
        session.setManagerViewPending(scope == ViewScope.PENDING);
        try {
            session.setManagerSortMode(RequestService.SortMode.valueOf(parts[1]));
        } catch (IllegalArgumentException exception) {
            notificationService.sendText(chatId, "Не удалось определить режим сортировки.");
            return;
        }
        session.setManagerViewPage(0);
        if (scope == ViewScope.PENDING) {
            showPendingMenu(chatId, telegramId, messageId, session);
        } else {
            showReviewedMenu(chatId, telegramId, messageId, session);
        }
    }

    private void startSearch(Long chatId, Long telegramId, UserSession session, String rawScope, Integer messageId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        ViewScope scope = parseScope(rawScope);
        session.setManagerViewPending(scope == ViewScope.PENDING);
        session.setManagerViewMessageId(messageId);
        session.setState(UserState.WAITING_FOR_MANAGER_SEARCH);
        notificationService.sendText(chatId, "Введите имя сотрудника, отдел или часть описания заявки:");
    }

    private void clearSearch(Long chatId, Long telegramId, Integer messageId, UserSession session, String rawScope) {
        ViewScope scope = parseScope(rawScope);
        session.setManagerSearchQuery(null);
        session.setManagerViewPage(0);
        if (scope == ViewScope.PENDING) {
            ensurePendingScope(session);
            showPendingMenu(chatId, telegramId, messageId, session);
        } else {
            ensureReviewedScope(session);
            showReviewedMenu(chatId, telegramId, messageId, session);
        }
    }

    private void openManagerCard(Long chatId, Long telegramId, Integer messageId, UserSession session, String rawRequestId) {
        if (!isManager(chatId, telegramId)) {
            notificationService.sendText(chatId, "Эта команда доступна только руководителю.");
            return;
        }
        Request request = getRequest(rawRequestId, chatId);
        if (request == null) {
            return;
        }
        session.setManagerViewMessageId(messageId);
        renderManagerView(
                chatId,
                messageId,
                buildManagerNotification(request.getUser(), request),
                buildManagerRequestCardKeyboard(request, session, true)
        );
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
            requestService.approve(request, resolveActor(telegramId));
            notificationService.sendText(chatId, "Заявка #" + request.getId() + " одобрена.");
            notificationService.sendText(request.getUser().getChatId(), requestTextFormatter.buildDecisionNotification(request));
        } else {
            requestService.reject(request, resolveActor(telegramId));
            notificationService.sendText(chatId, "Заявка #" + request.getId() + " отклонена.");
            notificationService.sendText(request.getUser().getChatId(), requestTextFormatter.buildDecisionNotification(request));
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
        notificationService.sendText(chatId, "Введите комментарий для заявки #" + request.getId() + ":");
    }

    private void refreshManagerCard(Long chatId, Request request, Integer messageId, UserSession session) {
        Integer targetMessageId = messageId != null ? messageId : session.getManagerViewMessageId();
        if (targetMessageId == null) {
            return;
        }
        renderManagerView(
                chatId,
                targetMessageId,
                buildManagerNotification(request.getUser(), request),
                buildManagerRequestCardKeyboard(request, session, targetMessageId.equals(session.getManagerViewMessageId()))
        );
    }

    private void showCurrentList(Long chatId, Long telegramId, Integer messageId, UserSession session) {
        if (session.isManagerViewPending()) {
            showPendingList(chatId, telegramId, messageId, session, session.getManagerViewPage() == null ? 0 : session.getManagerViewPage());
        } else {
            showReviewedList(chatId, telegramId, messageId, session, session.getManagerViewPage() == null ? 0 : session.getManagerViewPage());
        }
    }

    private String buildManagerPanelText() {
        long pendingCount = requestService.countByStatus(RequestStatus.IN_REVIEW);
        long approvedCount = requestService.countByStatus(RequestStatus.APPROVED);
        long rejectedCount = requestService.countByStatus(RequestStatus.REJECTED);
        return """
Панель руководителя

Сводка по заявкам:
- На рассмотрении: %d
- Одобрено: %d
- Отклонено: %d

Выберите раздел:
""".formatted(pendingCount, approvedCount, rejectedCount).trim();
    }

    private String buildPendingMenuText(UserSession session) {
        List<Request> requests = requestService.searchPendingRequests(
                session.getManagerSearchQuery(),
                new RequestService.RequestTypeFilter(session.getManagerTypeFilter()),
                session.getManagerPriorityFilter(),
                session.isManagerHighOnly(),
                session.getManagerSortMode()
        );
        return """
Нерассмотренные заявки

Подходящих заявок: %d
Поиск: %s
Тип: %s
Срочность: %s
Сортировка: %s
""".formatted(
                requests.size(),
                formatSearch(session.getManagerSearchQuery()),
                formatType(session.getManagerTypeFilter()),
                formatPriorityFilter(session.getManagerPriorityFilter()),
                formatSortMode(session.getManagerSortMode())
        ).trim();
    }

    private String buildReviewedMenuText(UserSession session) {
        List<Request> requests = requestService.searchReviewedRequests(
                session.getManagerSearchQuery(),
                session.getManagerReviewedStatus(),
                new RequestService.RequestTypeFilter(session.getManagerTypeFilter()),
                session.getManagerSortMode()
        );
        return """
Рассмотренные заявки

Подходящих заявок: %d
Поиск: %s
Статус: %s
Тип: %s
Сортировка: %s
""".formatted(
                requests.size(),
                formatSearch(session.getManagerSearchQuery()),
                formatReviewedStatus(session.getManagerReviewedStatus()),
                formatType(session.getManagerTypeFilter()),
                formatSortMode(session.getManagerSortMode())
        ).trim();
    }

    private String buildPendingEmptyText(UserSession session) {
        return buildPendingMenuText(session) + "\n\nПодходящих заявок пока нет.";
    }

    private String buildReviewedEmptyText(UserSession session) {
        return buildReviewedMenuText(session) + "\n\nПодходящих заявок пока нет.";
    }

    private String buildPendingListText(UserSession session, List<Request> pageItems, int totalItems, int page, int totalPages) {
        StringBuilder builder = new StringBuilder("""
Нерассмотренные заявки

Поиск: %s
Тип: %s
Срочность: %s
Сортировка: %s
Найдено: %d

""".formatted(
                formatSearch(session.getManagerSearchQuery()),
                formatType(session.getManagerTypeFilter()),
                formatPriorityFilter(session.getManagerPriorityFilter()),
                formatSortMode(session.getManagerSortMode()),
                totalItems
        ));
        for (Request request : pageItems) {
            builder.append("#").append(request.getId())
                    .append(" | ").append(requestTextFormatter.formatRequestType(request.getType()))
                    .append(" | ").append(requestTextFormatter.formatRequestPriority(request.getPriority()))
                    .append(" | ").append(request.getUser().getName())
                    .append('\n');
        }
        builder.append("\nСтраница ").append(page + 1).append(" из ").append(totalPages);
        return builder.toString().trim();
    }

    private String buildReviewedListText(UserSession session, List<Request> pageItems, int totalItems, int page, int totalPages) {
        StringBuilder builder = new StringBuilder("""
Рассмотренные заявки

Поиск: %s
Статус: %s
Тип: %s
Сортировка: %s
Найдено: %d

""".formatted(
                formatSearch(session.getManagerSearchQuery()),
                formatReviewedStatus(session.getManagerReviewedStatus()),
                formatType(session.getManagerTypeFilter()),
                formatSortMode(session.getManagerSortMode()),
                totalItems
        ));
        for (Request request : pageItems) {
            builder.append("#").append(request.getId())
                    .append(" | ").append(requestTextFormatter.formatRequestType(request.getType()))
                    .append(" | ").append(requestTextFormatter.formatRequestStatus(request.getStatus()))
                    .append(" | ").append(request.getUser().getName())
                    .append('\n');
        }
        builder.append("\nСтраница ").append(page + 1).append(" из ").append(totalPages);
        return builder.toString().trim();
    }

    private InlineKeyboardMarkup buildManagerPanelKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("Нерассмотренные", MANAGER_PENDING_MENU),
                        button("Только высокая", MANAGER_PENDING_HIGH_LIST)))
                .keyboardRow(new InlineKeyboardRow(button("Рассмотренные", MANAGER_REVIEWED_MENU)))
                .build();
    }

    private InlineKeyboardMarkup buildPendingMenuKeyboard(UserSession session) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("Все", MANAGER_PENDING_PRIORITY_PREFIX + "ALL"),
                        button("Высокая", MANAGER_PENDING_PRIORITY_PREFIX + RequestPriority.HIGH.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("Средняя", MANAGER_PENDING_PRIORITY_PREFIX + RequestPriority.MEDIUM.name()),
                        button("Низкая", MANAGER_PENDING_PRIORITY_PREFIX + RequestPriority.LOW.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("Тип заявки", MANAGER_TYPE_MENU_PREFIX + ViewScope.PENDING.name()),
                        button("Сортировка", MANAGER_SORT_MENU_PREFIX + ViewScope.PENDING.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("Поиск", MANAGER_SEARCH_START_PREFIX + ViewScope.PENDING.name()),
                        button("Сбросить поиск", MANAGER_SEARCH_CLEAR_PREFIX + ViewScope.PENDING.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("Показать список", MANAGER_PENDING_LIST)))
                .keyboardRow(new InlineKeyboardRow(button("Назад", MANAGER_MENU)))
                .build();
    }

    private InlineKeyboardMarkup buildReviewedMenuKeyboard(UserSession session) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("Все", MANAGER_REVIEWED_STATUS_PREFIX + "ALL"),
                        button("Одобренные", MANAGER_REVIEWED_STATUS_PREFIX + RequestStatus.APPROVED.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("Отклоненные", MANAGER_REVIEWED_STATUS_PREFIX + RequestStatus.REJECTED.name()),
                        button("Тип заявки", MANAGER_TYPE_MENU_PREFIX + ViewScope.REVIEWED.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("Сортировка", MANAGER_SORT_MENU_PREFIX + ViewScope.REVIEWED.name()),
                        button("Поиск", MANAGER_SEARCH_START_PREFIX + ViewScope.REVIEWED.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("Сбросить поиск", MANAGER_SEARCH_CLEAR_PREFIX + ViewScope.REVIEWED.name()),
                        button("Показать список", MANAGER_REVIEWED_LIST)))
                .keyboardRow(new InlineKeyboardRow(button("Назад", MANAGER_MENU)))
                .build();
    }

    private InlineKeyboardMarkup buildTypeKeyboard(ViewScope scope) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("Все", MANAGER_TYPE_SET_PREFIX + scope.name() + ":ALL"),
                        button("Финансы", MANAGER_TYPE_SET_PREFIX + scope.name() + ":" + RequestType.FINANCE.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("Оборудование", MANAGER_TYPE_SET_PREFIX + scope.name() + ":" + RequestType.EQUIPMENT.name()),
                        button("Отпуск", MANAGER_TYPE_SET_PREFIX + scope.name() + ":" + RequestType.LEAVE.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("Другое", MANAGER_TYPE_SET_PREFIX + scope.name() + ":" + RequestType.OTHER.name())))
                .keyboardRow(new InlineKeyboardRow(button("Назад", scope == ViewScope.PENDING ? MANAGER_PENDING_MENU : MANAGER_REVIEWED_MENU)))
                .build();
    }

    private InlineKeyboardMarkup buildSortKeyboard(ViewScope scope) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("По дате создания", MANAGER_SORT_SET_PREFIX + scope.name() + ":" + RequestService.SortMode.CREATED.name()),
                        button("По обновлению", MANAGER_SORT_SET_PREFIX + scope.name() + ":" + RequestService.SortMode.UPDATED.name())))
                .keyboardRow(new InlineKeyboardRow(
                        button("По срочности", MANAGER_SORT_SET_PREFIX + scope.name() + ":" + RequestService.SortMode.PRIORITY.name())))
                .keyboardRow(new InlineKeyboardRow(button("Назад", scope == ViewScope.PENDING ? MANAGER_PENDING_MENU : MANAGER_REVIEWED_MENU)))
                .build();
    }

    private InlineKeyboardMarkup buildPendingListKeyboard(List<Request> requests, int page, int totalPages) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Request request : requests) {
            rows.add(new InlineKeyboardRow(button("Открыть #" + request.getId(), MANAGER_OPEN_PREFIX + request.getId())));
        }
        if (totalPages > 1) {
            rows.add(buildPaginationRow(true, page, totalPages));
        }
        rows.add(new InlineKeyboardRow(button("К фильтрам", MANAGER_PENDING_MENU)));
        rows.add(new InlineKeyboardRow(button("В панель", MANAGER_MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup buildReviewedListKeyboard(List<Request> requests, int page, int totalPages) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Request request : requests) {
            rows.add(new InlineKeyboardRow(button("Открыть #" + request.getId(), MANAGER_OPEN_PREFIX + request.getId())));
        }
        if (totalPages > 1) {
            rows.add(buildPaginationRow(false, page, totalPages));
        }
        rows.add(new InlineKeyboardRow(button("К фильтрам", MANAGER_REVIEWED_MENU)));
        rows.add(new InlineKeyboardRow(button("В панель", MANAGER_MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup buildManagerRequestCardKeyboard(Request request, UserSession session, boolean includeBack) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (request.getStatus() == RequestStatus.IN_REVIEW) {
            rows.add(new InlineKeyboardRow(
                    button("Одобрить", APPROVE_PREFIX + request.getId()),
                    button("Отклонить", REJECT_PREFIX + request.getId()),
                    button("Комментарий", COMMENT_PREFIX + request.getId())
            ));
        }
        if (includeBack) {
            rows.add(new InlineKeyboardRow(button("Назад к списку", MANAGER_BACK_LIST)));
        }
        rows.add(new InlineKeyboardRow(button("В панель", MANAGER_MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardRow buildPaginationRow(boolean pending, int page, int totalPages) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        if (page > 0) {
            buttons.add(button("← Назад", pending ? MANAGER_PENDING_LIST + ":" + (page - 1) : MANAGER_REVIEWED_LIST + ":" + (page - 1)));
        }
        buttons.add(button((page + 1) + "/" + totalPages, MANAGER_NOOP));
        if (page + 1 < totalPages) {
            buttons.add(button("Дальше →", pending ? MANAGER_PENDING_LIST + ":" + (page + 1) : MANAGER_REVIEWED_LIST + ":" + (page + 1)));
        }
        return new InlineKeyboardRow(buttons);
    }

    private String buildManagerNotification(User user, Request request) {
        return """
Заявка #%d

Сотрудник: %s
Отдел: %s
Должность: %s
Тип: %s
Срочность: %s
Описание: %s
Статус: %s
""".formatted(
                request.getId(),
                user.getName(),
                user.getDepartment(),
                user.getPosition(),
                requestTextFormatter.formatRequestType(request.getType()),
                requestTextFormatter.formatRequestPriority(request.getPriority()),
                request.getDescription(),
                requestTextFormatter.formatRequestStatus(request.getStatus())
        ).trim();
    }

    private void renderManagerView(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        if (messageId == null) {
            notificationService.sendText(chatId, text, keyboard);
        } else {
            notificationService.editText(chatId, messageId, text, keyboard);
        }
    }

    private boolean isManager(Long chatId, Long telegramId) {
        return userService.hasManagerAccess(chatId, telegramId);
    }

    private User resolveActor(Long telegramId) {
        try {
            return userService.getByTelegramId(telegramId);
        } catch (Exception exception) {
            return null;
        }
    }

    private Request getRequest(String rawRequestId, Long chatId) {
        try {
            return requestService.getById(Long.valueOf(rawRequestId));
        } catch (Exception exception) {
            notificationService.sendText(chatId, "Заявка не найдена.");
            return null;
        }
    }

    private void resetAllFilters(UserSession session) {
        session.setManagerViewFilter(null);
        session.setManagerViewPending(true);
        session.setManagerViewPage(0);
        session.setManagerSearchQuery(null);
        session.setManagerTypeFilter(null);
        session.setManagerSortMode(RequestService.SortMode.CREATED);
        session.setManagerPriorityFilter(null);
        session.setManagerReviewedStatus(null);
        session.setManagerHighOnly(false);
    }

    private void preparePendingDefaults(UserSession session) {
        session.setManagerViewPending(true);
        session.setManagerViewFilter(ViewScope.PENDING.name());
        session.setManagerViewPage(0);
        session.setManagerReviewedStatus(null);
        session.setManagerSortMode(session.getManagerSortMode() == null ? RequestService.SortMode.CREATED : session.getManagerSortMode());
    }

    private void prepareReviewedDefaults(UserSession session) {
        session.setManagerViewPending(false);
        session.setManagerViewFilter(ViewScope.REVIEWED.name());
        session.setManagerViewPage(0);
        session.setManagerPriorityFilter(null);
        session.setManagerHighOnly(false);
        session.setManagerSortMode(session.getManagerSortMode() == null ? RequestService.SortMode.CREATED : session.getManagerSortMode());
    }

    private void ensurePendingScope(UserSession session) {
        if (!session.isManagerViewPending() || !ViewScope.PENDING.name().equals(session.getManagerViewFilter())) {
            preparePendingDefaults(session);
        }
    }

    private void ensureReviewedScope(UserSession session) {
        if (session.isManagerViewPending() || !ViewScope.REVIEWED.name().equals(session.getManagerViewFilter())) {
            prepareReviewedDefaults(session);
        }
    }

    private String formatSearch(String query) {
        return StringUtils.hasText(query) ? query : "без поиска";
    }

    private String formatType(RequestType type) {
        return type == null ? "все" : requestTextFormatter.formatRequestType(type);
    }

    private String formatPriorityFilter(RequestPriority priority) {
        return priority == null ? "Все" : requestTextFormatter.formatRequestPriority(priority);
    }

    private String formatReviewedStatus(RequestStatus status) {
        return status == null ? "все" : requestTextFormatter.formatRequestStatus(status);
    }

    private String formatSortMode(RequestService.SortMode sortMode) {
        if (sortMode == null) {
            return "по дате создания";
        }
        return switch (sortMode) {
            case CREATED -> "по дате создания";
            case UPDATED -> "по обновлению";
            case PRIORITY -> "по срочности";
        };
    }

    private ViewScope parseScope(String rawScope) {
        try {
            return ViewScope.valueOf(rawScope.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ViewScope.PENDING;
        }
    }

    private int pageCount(int totalItems) {
        return Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
    }

    private int parsePage(String rawPage) {
        try {
            return Math.max(Integer.parseInt(rawPage), 0);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int normalizePage(int page, int totalPages) {
        return Math.max(0, Math.min(page, totalPages - 1));
    }

    private List<Request> paginate(List<Request> requests, int page) {
        int fromIndex = page * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, requests.size());
        return requests.subList(fromIndex, toIndex);
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    private enum ViewScope {
        PENDING,
        REVIEWED
    }
}
