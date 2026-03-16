package com.example.request_bot.service;

import com.example.request_bot.dto.RequestDraft;
import com.example.request_bot.model.Request;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestStatus;
import com.example.request_bot.repository.RequestRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RequestService {

    private final RequestRepository requestRepository;

    public RequestService(RequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public Request create(User user, RequestDraft draft) {
        Request request = new Request();
        request.setUser(user);
        request.setType(draft.getType());
        request.setDescription(draft.getDescription());
        request.setPriority(draft.getPriority());
        request.setStatus(RequestStatus.NEW);
        request.setCreatedAt(LocalDateTime.now());
        return requestRepository.save(request);
    }

    public List<Request> getUserRequests(User user) {
        return requestRepository.findAllByUserOrderByCreatedAtDesc(user);
    }

    public Request getById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Request not found"));
    }

    public void moveToReview(Request request) {
        request.setStatus(RequestStatus.IN_REVIEW);
        requestRepository.save(request);
    }

    public void approve(Request request) {
        request.setStatus(RequestStatus.APPROVED);
        requestRepository.save(request);
    }

    public void reject(Request request) {
        request.setStatus(RequestStatus.REJECTED);
        requestRepository.save(request);
    }

    public Request save(Request request) {
        return requestRepository.save(request);
    }

    public void addManagerComment(Request request, String comment) {
        request.setManagerComment(comment);
        requestRepository.save(request);
    }
}
