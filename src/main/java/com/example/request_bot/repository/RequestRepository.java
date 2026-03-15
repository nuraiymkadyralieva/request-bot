package com.example.request_bot.repository;

import com.example.request_bot.model.Request;
import com.example.request_bot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findAllByUserOrderByCreatedAtDesc(User user);
}
