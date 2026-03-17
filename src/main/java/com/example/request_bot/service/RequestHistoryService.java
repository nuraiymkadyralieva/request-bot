package com.example.request_bot.service;

import com.example.request_bot.model.Request;
import com.example.request_bot.model.RequestHistory;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestActionType;
import com.example.request_bot.model.enums.RequestStatus;
import com.example.request_bot.repository.RequestHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RequestHistoryService {

    private final RequestHistoryRepository requestHistoryRepository;

    public RequestHistoryService(RequestHistoryRepository requestHistoryRepository) {
        this.requestHistoryRepository = requestHistoryRepository;
    }

    public void record(Request request, User actor, RequestActionType actionType,
                       RequestStatus fromStatus, RequestStatus toStatus, String comment) {
        RequestHistory history = new RequestHistory();
        history.setRequest(request);
        history.setActor(actor);
        history.setActionType(actionType);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setComment(comment);
        history.setCreatedAt(LocalDateTime.now());
        requestHistoryRepository.save(history);
    }

    public List<RequestHistory> getHistory(Request request) {
        return requestHistoryRepository.findAllByRequestOrderByCreatedAtAsc(request);
    }
}
