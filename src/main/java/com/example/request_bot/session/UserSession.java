package com.example.request_bot.session;

import com.example.request_bot.dto.RequestDraft;
import com.example.request_bot.model.enums.RequestPriority;
import com.example.request_bot.model.enums.RequestStatus;
import com.example.request_bot.model.enums.RequestType;
import com.example.request_bot.service.RequestService;

public class UserSession {

    private UserState state = UserState.IDLE;
    private String tempName;
    private String tempDepartment;
    private RequestDraft requestDraft;
    private Integer employeeFlowMessageId;
    private boolean editingRequestDescription;
    private Long requestIdForComment;
    private Integer managerViewMessageId;
    private String managerViewFilter;
    private boolean managerViewPending;
    private Integer managerViewPage;
    private String managerSearchQuery;
    private RequestType managerTypeFilter;
    private RequestService.SortMode managerSortMode = RequestService.SortMode.CREATED;
    private RequestPriority managerPriorityFilter;
    private RequestStatus managerReviewedStatus;
    private boolean managerHighOnly;

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

    public Integer getEmployeeFlowMessageId() {
        return employeeFlowMessageId;
    }

    public void setEmployeeFlowMessageId(Integer employeeFlowMessageId) {
        this.employeeFlowMessageId = employeeFlowMessageId;
    }

    public boolean isEditingRequestDescription() {
        return editingRequestDescription;
    }

    public void setEditingRequestDescription(boolean editingRequestDescription) {
        this.editingRequestDescription = editingRequestDescription;
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

    public Integer getManagerViewPage() {
        return managerViewPage;
    }

    public void setManagerViewPage(Integer managerViewPage) {
        this.managerViewPage = managerViewPage;
    }

    public String getManagerSearchQuery() {
        return managerSearchQuery;
    }

    public void setManagerSearchQuery(String managerSearchQuery) {
        this.managerSearchQuery = managerSearchQuery;
    }

    public RequestType getManagerTypeFilter() {
        return managerTypeFilter;
    }

    public void setManagerTypeFilter(RequestType managerTypeFilter) {
        this.managerTypeFilter = managerTypeFilter;
    }

    public RequestService.SortMode getManagerSortMode() {
        return managerSortMode;
    }

    public void setManagerSortMode(RequestService.SortMode managerSortMode) {
        this.managerSortMode = managerSortMode;
    }

    public RequestPriority getManagerPriorityFilter() {
        return managerPriorityFilter;
    }

    public void setManagerPriorityFilter(RequestPriority managerPriorityFilter) {
        this.managerPriorityFilter = managerPriorityFilter;
    }

    public RequestStatus getManagerReviewedStatus() {
        return managerReviewedStatus;
    }

    public void setManagerReviewedStatus(RequestStatus managerReviewedStatus) {
        this.managerReviewedStatus = managerReviewedStatus;
    }

    public boolean isManagerHighOnly() {
        return managerHighOnly;
    }

    public void setManagerHighOnly(boolean managerHighOnly) {
        this.managerHighOnly = managerHighOnly;
    }
}
