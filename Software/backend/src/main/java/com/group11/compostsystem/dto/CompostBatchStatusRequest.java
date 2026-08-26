package com.group11.compostsystem.dto;

public class CompostBatchStatusRequest {
    private String status;
    private String actualReadyDate;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActualReadyDate() {
        return actualReadyDate;
    }

    public void setActualReadyDate(String actualReadyDate) {
        this.actualReadyDate = actualReadyDate;
    }
}
