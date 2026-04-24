package com.movie.theatrevendor.model;

public enum SeatStatus {

    AVAILABLE("Available"),
    LOCKED("Locked"),
    BOOKED("Booked");

    private final String displayName;

    SeatStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}