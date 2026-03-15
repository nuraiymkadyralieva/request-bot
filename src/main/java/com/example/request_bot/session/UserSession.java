package com.example.request_bot.session;

import com.example.request_bot.dto.RequestDraft;

public class UserSession {

    private UserState state = UserState.IDLE;
    private String tempName;
    private String tempDepartment;
    private RequestDraft requestDraft;
    private Long requestIdForComment;

    public UserState getState() {
        return state;
    }

    public void setState(UserState state) {
        this.state = state;
    }

    public String getTempName() {
        return tempName;
    }

    public void setTempName(String tempName) {
        this.tempName = tempName;
    }

    public String getTempDepartment() {
        return tempDepartment;
    }

    public void setTempDepartment(String tempDepartment) {
        this.tempDepartment = tempDepartment;
    }

    public RequestDraft getRequestDraft() {
        return requestDraft;
    }

    public void setRequestDraft(RequestDraft requestDraft) {
        this.requestDraft = requestDraft;
    }

    public Long getRequestIdForComment() {
        return requestIdForComment;
    }

    public void setRequestIdForComment(Long requestIdForComment) {
        this.requestIdForComment = requestIdForComment;
    }
}
