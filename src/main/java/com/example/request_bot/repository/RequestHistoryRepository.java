package com.example.request_bot.repository;

import com.example.request_bot.model.Request;
import com.example.request_bot.model.RequestHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestHistoryRepository extends JpaRepository<RequestHistory, Long> {

    List<RequestHistory> findAllByRequestOrderByCreatedAtAsc(Request request);
}
