package com.example.request_bot.dto;

import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestType;

public class RequestDraft {

    private RequestType type;
    private String description;
    private RequestPriority priority;

    public RequestType getType() {
        return type;
    }

    public void setType(RequestType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RequestPriority getPriority() {
        return priority;
    }

    public void setPriority(RequestPriority priority) {
        this.priority = priority;
    }
}
