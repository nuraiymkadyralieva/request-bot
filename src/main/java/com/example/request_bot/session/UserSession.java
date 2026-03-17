package com.example.request_bot.session;

import com.example.request_bot.dto.RequestDraft;

public class UserSession {

    private UserState state = UserState.IDLE;
    private String tempName;
    private String tempDepartment;
    private RequestDraft requestDraft;
    private Long requestIdForComment;
    private Integer managerViewMessageId;
    private String managerViewFilter;
    private boolean managerViewPending;

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

    public Integer getManagerViewMessageId() {
        return managerViewMessageId;
    }

    public void setManagerViewMessageId(Integer managerViewMessageId) {
        this.managerViewMessageId = managerViewMessageId;
    }

    public String getManagerViewFilter() {
        return managerViewFilter;
    }

    public void setManagerViewFilter(String managerViewFilter) {
        this.managerViewFilter = managerViewFilter;
    }

    public boolean isManagerViewPending() {
        return managerViewPending;
    }

    public void setManagerViewPending(boolean managerViewPending) {
        this.managerViewPending = managerViewPending;
    }
}
