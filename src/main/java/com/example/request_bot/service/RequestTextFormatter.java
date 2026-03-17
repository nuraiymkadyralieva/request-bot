package com.example.request_bot.service;

import com.example.request_bot.dto.RequestDraft;
import com.example.request_bot.model.Request;
import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestStatus;
import com.example.request_bot.model.enums.RequestType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class RequestTextFormatter {

    public String buildDraftSummary(RequestDraft draft) {
        return """
Проверьте заявку:

Тип: %s
Описание: %s
Срочность: %s
""".formatted(formatRequestType(draft.getType()), draft.getDescription(), formatRequestPriority(draft.getPriority())).trim();
    }

    public String buildUserRequestsList(List<Request> requests) {
        StringBuilder builder = new StringBuilder("Ваши заявки:\n\n");
        for (Request request : requests) {
            builder.append("#").append(request.getId())
                    .append(" | ").append(formatRequestType(request.getType()))
                    .append(" | ").append(formatRequestStatus(request.getStatus()));
            if (StringUtils.hasText(request.getManagerComment())) {
                builder.append(" | Комментарий: ").append(request.getManagerComment());
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    public String buildSubmittedRequestCard(Request request) {
        return """
Ваша заявка #%d отправлена.

Тип: %s
Описание: %s
Срочность: %s
Статус: %s
""".formatted(
                request.getId(),
                formatRequestType(request.getType()),
                request.getDescription(),
                formatRequestPriority(request.getPriority()),
                formatRequestStatus(request.getStatus())
        ).trim();
    }

    public String buildDecisionNotification(Request request) {
        return """
Ваша заявка #%d %s.

Тип: %s
Описание: %s
Срочность: %s
Статус: %s
""".formatted(
                request.getId(),
                request.getStatus() == RequestStatus.APPROVED ? "одобрена" : "отклонена",
                formatRequestType(request.getType()),
                request.getDescription(),
                formatRequestPriority(request.getPriority()),
                formatRequestStatus(request.getStatus())
        ).trim();
    }

    public String formatRequestType(RequestType type) {
        return switch (type) {
            case FINANCE -> "Финансы";
            case EQUIPMENT -> "Оборудование";
            case LEAVE -> "Отпуск";
            case OTHER -> "Другое";
        };
    }

    public String formatRequestPriority(RequestPriority priority) {
        return switch (priority) {
            case LOW -> "Низкая";
            case MEDIUM -> "Средняя";
            case HIGH -> "Высокая";
        };
    }

    public String formatRequestStatus(RequestStatus status) {
        return switch (status) {
            case NEW -> "Новая";
            case IN_REVIEW -> "На рассмотрении";
            case APPROVED -> "Одобрена";
            case REJECTED -> "Отклонена";
        };
    }
}
