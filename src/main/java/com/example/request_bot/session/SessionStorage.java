package com.example.request_bot.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStorage {

    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession getSession(Long telegramId) {
        return sessions.computeIfAbsent(telegramId, id -> new UserSession());
    }

    public void clearRequestDraft(Long telegramId) {
        UserSession session = getSession(telegramId);
        session.setRequestDraft(null);
        session.setEmployeeFlowMessageId(null);
        session.setEditingRequestDescription(false);
        session.setRequestIdForComment(null);
        session.setManagerViewMessageId(null);
        session.setManagerViewFilter(null);
        session.setManagerViewPending(false);
        session.setManagerViewPage(null);
        session.setManagerSearchQuery(null);
        session.setManagerTypeFilter(null);
        session.setManagerSortMode(com.example.request_bot.service.RequestService.SortMode.CREATED);
        session.setManagerPriorityFilter(null);
        session.setManagerReviewedStatus(null);
        session.setManagerHighOnly(false);
    }
}
