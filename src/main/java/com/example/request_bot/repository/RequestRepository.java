package com.example.request_bot.repository;

import com.example.request_bot.model.Request;
import com.example.request_bot.model.User;
import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findAllByUserOrderByCreatedAtDesc(User user);

    List<Request> findAllByStatusOrderByCreatedAtDesc(RequestStatus status);

    List<Request> findAllByStatusAndPriorityOrderByCreatedAtDesc(RequestStatus status, RequestPriority priority);

    List<Request> findAllByStatusInOrderByCreatedAtDesc(List<RequestStatus> statuses);
}
