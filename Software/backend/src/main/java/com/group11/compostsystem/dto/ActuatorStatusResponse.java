package com.group11.compostsystem.dto;

import java.util.ArrayList;
import java.util.List;

public class ActuatorStatusResponse {
    private boolean fanActive;
    private boolean waterPumpActive;
    private List<ActuatorRuntimeStatusResponse> actuators = new ArrayList<>();
    private ActuatorLogResponse latestActivity;

    public ActuatorStatusResponse() {}

    public ActuatorStatusResponse(boolean fanActive, boolean waterPumpActive) {
        this.fanActive = fanActive;
        this.waterPumpActive = waterPumpActive;
    }

    public ActuatorStatusResponse(boolean fanActive, boolean waterPumpActive,
                                  List<ActuatorRuntimeStatusResponse> actuators,
                                  ActuatorLogResponse latestActivity) {
        this.fanActive = fanActive;
        this.waterPumpActive = waterPumpActive;
        this.actuators = actuators != null ? actuators : new ArrayList<>();
        this.latestActivity = latestActivity;
    }

    public boolean isFanActive() { return fanActive; }
    public void setFanActive(boolean fanActive) { this.fanActive = fanActive; }

    public boolean isWaterPumpActive() { return waterPumpActive; }
    public void setWaterPumpActive(boolean waterPumpActive) { this.waterPumpActive = waterPumpActive; }

    public List<ActuatorRuntimeStatusResponse> getActuators() { return actuators; }
    public void setActuators(List<ActuatorRuntimeStatusResponse> actuators) {
        this.actuators = actuators != null ? actuators : new ArrayList<>();
    }

    public ActuatorLogResponse getLatestActivity() { return latestActivity; }
    public void setLatestActivity(ActuatorLogResponse latestActivity) { this.latestActivity = latestActivity; }
}
