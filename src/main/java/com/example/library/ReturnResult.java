package com.example.library;

public class ReturnResult {
    private final long daysLate;
    private final long fine;

    public ReturnResult(long daysLate, long fine) {
        this.daysLate = daysLate;
        this.fine = fine;
    }

    public long getDaysLate() { return daysLate; }
    public long getFine() { return fine; }
}
