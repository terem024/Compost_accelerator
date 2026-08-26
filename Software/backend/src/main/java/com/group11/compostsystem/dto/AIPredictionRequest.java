package com.group11.compostsystem.dto;

public class AIPredictionRequest {

    private Integer batchId;

    public AIPredictionRequest() {
    }

    public AIPredictionRequest(Integer batchId) {
        this.batchId = batchId;
    }

    public Integer getBatchId() {
        return batchId;
    }

    public void setBatchId(Integer batchId) {
        this.batchId = batchId;
    }

}
