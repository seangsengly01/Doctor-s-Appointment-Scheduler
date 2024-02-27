package org.example;

import java.sql.Timestamp;

public class AvailabilitySlot {
    private Timestamp startTime;
    private Timestamp endTime;

    // Constructors, getters, and setters
    public Timestamp getStartTime() {
        return startTime;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(Timestamp endTime) {
        this.endTime = endTime;
    }
}
