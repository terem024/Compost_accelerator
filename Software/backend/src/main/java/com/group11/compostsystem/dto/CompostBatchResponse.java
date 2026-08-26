package com.group11.compostsystem.dto;

import java.sql.Timestamp;

public class CompostBatchResponse {
    private Integer batchId;
    private String batchCode;
    private String batchName;
    private String primaryMaterial;
    private String materialDescription;
    private String fillLevel;
    private String startDate;
    private String latestPredictedReadyDate;
    private String actualReadyDate;
    private String status;
    private String binLocation;
    private String notes;
    private Integer createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public CompostBatchResponse(Integer batchId,
                                String batchCode,
                                String batchName,
                                String primaryMaterial,
                                String materialDescription,
                                String fillLevel,
                                String startDate,
                                String latestPredictedReadyDate,
                                String actualReadyDate,
                                String status,
                                String binLocation,
                                String notes,
                                Integer createdBy,
                                Timestamp createdAt,
                                Timestamp updatedAt) {
        this.batchId = batchId;
        this.batchCode = batchCode;
        this.batchName = batchName;
        this.primaryMaterial = primaryMaterial;
        this.materialDescription = materialDescription;
        this.fillLevel = fillLevel;
        this.startDate = startDate;
        this.latestPredictedReadyDate = latestPredictedReadyDate;
        this.actualReadyDate = actualReadyDate;
        this.status = status;
        this.binLocation = binLocation;
        this.notes = notes;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Integer getBatchId() {
        return batchId;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public String getBatchName() {
        return batchName;
    }

    public String getPrimaryMaterial() {
        return primaryMaterial;
    }

    public String getMaterialDescription() {
        return materialDescription;
    }

    public String getFillLevel() {
        return fillLevel;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getLatestPredictedReadyDate() {
        return latestPredictedReadyDate;
    }

    public String getActualReadyDate() {
        return actualReadyDate;
    }

    public String getStatus() {
        return status;
    }

    public String getBinLocation() {
        return binLocation;
    }

    public String getNotes() {
        return notes;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
}
