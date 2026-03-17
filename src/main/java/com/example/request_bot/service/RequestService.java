package com.example.request_bot.service;

import com.example.request_bot.dto.RequestDraft;
import com.example.request_bot.model.Request;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestActionType;
import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestStatus;
import com.example.request_bot.repository.RequestRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class RequestService {

    private final RequestRepository requestRepository;
    private final RequestHistoryService requestHistoryService;

    public RequestService(RequestRepository requestRepository, RequestHistoryService requestHistoryService) {
        this.requestRepository = requestRepository;
        this.requestHistoryService = requestHistoryService;
    }

    public Request create(User user, RequestDraft draft) {
        LocalDateTime now = LocalDateTime.now();
        Request request = new Request();
        request.setUser(user);
        request.setType(draft.getType());
        request.setDescription(draft.getDescription());
        request.setPriority(draft.getPriority());
        request.setStatus(RequestStatus.NEW);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        Request saved = requestRepository.save(request);
        requestHistoryService.record(saved, user, RequestActionType.CREATED, null, RequestStatus.NEW, null);
        return saved;
    }

    public List<Request> getUserRequests(User user) {
        return requestRepository.findAllByUserOrderByCreatedAtDesc(user);
    }

    public List<Request> getPendingRequests() {
        return requestRepository.findAllByStatusOrderByCreatedAtDesc(RequestStatus.IN_REVIEW);
    }

    public List<Request> getPendingRequestsByPriority(RequestPriority priority) {
        return requestRepository.findAllByStatusAndPriorityOrderByCreatedAtDesc(RequestStatus.IN_REVIEW, priority);
    }

    public List<Request> getReviewedRequests() {
        return requestRepository.findAllByStatusInOrderByCreatedAtDesc(
                Arrays.asList(RequestStatus.APPROVED, RequestStatus.REJECTED)
        );
    }

    public List<Request> getReviewedRequestsByStatus(RequestStatus status) {
        return requestRepository.findAllByStatusOrderByCreatedAtDesc(status);
    }

    public Request getById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Request not found"));
    }

    public void moveToReview(Request request) {
        moveToReview(request, request.getUser());
    }

    public void moveToReview(Request request, User actor) {
        RequestStatus previousStatus = request.getStatus();
        request.setStatus(RequestStatus.IN_REVIEW);
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        requestHistoryService.record(request, actor, RequestActionType.SUBMITTED, previousStatus, RequestStatus.IN_REVIEW, null);
    }

    public void approve(Request request) {
        approve(request, null);
    }

    public void approve(Request request, User actor) {
        RequestStatus previousStatus = request.getStatus();
        request.setStatus(RequestStatus.APPROVED);
        request.setUpdatedAt(LocalDateTime.now());
        request.setResolvedAt(LocalDateTime.now());
        requestRepository.save(request);
        requestHistoryService.record(request, actor, RequestActionType.APPROVED, previousStatus, RequestStatus.APPROVED, null);
    }

    public void reject(Request request) {
        reject(request, null);
    }

    public void reject(Request request, User actor) {
        RequestStatus previousStatus = request.getStatus();
        request.setStatus(RequestStatus.REJECTED);
        request.setUpdatedAt(LocalDateTime.now());
        request.setResolvedAt(LocalDateTime.now());
        requestRepository.save(request);
        requestHistoryService.record(request, actor, RequestActionType.REJECTED, previousStatus, RequestStatus.REJECTED, null);
    }

    public Request save(Request request) {
        request.setUpdatedAt(LocalDateTime.now());
        return requestRepository.save(request);
    }

    public void addManagerComment(Request request, String comment) {
        request.setManagerComment(comment);
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
    }

    public void recordComment(Request request, User actor, String comment) {
        requestHistoryService.record(request, actor, RequestActionType.COMMENTED, request.getStatus(), request.getStatus(), comment);
    }

    public List<Request> searchPendingRequests(String query, RequestTypeFilter typeFilter, RequestPriority priorityFilter,
                                               boolean highOnly, SortMode sortMode) {
        Stream<Request> stream = requestRepository.findAllByStatusOrderByCreatedAtDesc(RequestStatus.IN_REVIEW).stream();
        stream = applyFilters(stream, query, typeFilter, priorityFilter, highOnly);
        return stream.sorted(resolveComparator(sortMode)).toList();
    }

    public List<Request> searchReviewedRequests(String query, RequestStatus statusFilter, RequestTypeFilter typeFilter,
                                                SortMode sortMode) {
        Stream<Request> stream = requestRepository.findAllByStatusInOrderByCreatedAtDesc(
                Arrays.asList(RequestStatus.APPROVED, RequestStatus.REJECTED)
        ).stream();
        if (statusFilter != null) {
            stream = stream.filter(request -> request.getStatus() == statusFilter);
        }
        stream = applyTypeAndQuery(stream, query, typeFilter);
        return stream.sorted(resolveComparator(sortMode)).toList();
    }

    public long countByStatus(RequestStatus status) {
        return requestRepository.findAllByStatusOrderByCreatedAtDesc(status).size();
    }

    public List<com.example.request_bot.model.RequestHistory> getHistory(Request request) {
        return requestHistoryService.getHistory(request);
    }

    private Stream<Request> applyFilters(Stream<Request> stream, String query, RequestTypeFilter typeFilter,
                                         RequestPriority priorityFilter, boolean highOnly) {
        Stream<Request> filtered = applyTypeAndQuery(stream, query, typeFilter);
        if (priorityFilter != null) {
            filtered = filtered.filter(request -> request.getPriority() == priorityFilter);
        }
        if (highOnly) {
            filtered = filtered.filter(request -> request.getPriority() == RequestPriority.HIGH);
        }
        return filtered;
    }

    private Stream<Request> applyTypeAndQuery(Stream<Request> stream, String query, RequestTypeFilter typeFilter) {
        Stream<Request> filtered = stream;
        if (typeFilter != null && typeFilter.requestType() != null) {
            filtered = filtered.filter(request -> request.getType() == typeFilter.requestType());
        }
        if (query != null && !query.isBlank()) {
            String normalized = query.toLowerCase(Locale.ROOT);
            filtered = filtered.filter(request ->
                    request.getDescription().toLowerCase(Locale.ROOT).contains(normalized)
                            || request.getUser().getName().toLowerCase(Locale.ROOT).contains(normalized)
                            || request.getUser().getDepartment().toLowerCase(Locale.ROOT).contains(normalized));
        }
        return filtered;
    }

    private Comparator<Request> resolveComparator(SortMode sortMode) {
        if (sortMode == SortMode.PRIORITY) {
            return Comparator.comparing(Request::getPriority, this::comparePriority)
                    .thenComparing(Request::getCreatedAt, Comparator.reverseOrder());
        }
        if (sortMode == SortMode.UPDATED) {
            return Comparator.comparing(Request::getUpdatedAt, Comparator.reverseOrder());
        }
        return Comparator.comparing(Request::getCreatedAt, Comparator.reverseOrder());
    }

    private int comparePriority(RequestPriority left, RequestPriority right) {
        return Integer.compare(priorityWeight(right), priorityWeight(left));
    }

    private int priorityWeight(RequestPriority priority) {
        return switch (priority) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    public enum SortMode {
        CREATED,
        UPDATED,
        PRIORITY
    }

    public record RequestTypeFilter(com.example.request_bot.model.enums.RequestType requestType) {
    }
}
