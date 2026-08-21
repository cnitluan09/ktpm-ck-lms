package com.example.library;

import jakarta.persistence.*;

@Entity
@Table(name = "readers")
public class Reader {

    @Id
    @Column(length = 20)
    private String readerId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private int activeLoans = 0;

    /** Nợ phạt tính bằng VND (BR03, BR04) */
    @Column(nullable = false)
    private long debt = 0;

    protected Reader() {}

    public Reader(String readerId, String name, String email) {
        if (name == null || name.length() > 255)
            throw new IllegalArgumentException("Tên vượt quá 255 ký tự");
        this.readerId = readerId;
        this.name = name;
        this.email = email;
    }

    public String getReaderId() { return readerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public int getActiveLoans() { return activeLoans; }
    public long getDebt() { return debt; }

    /** BR01: tối đa 5 phiếu hiệu lực */
    public boolean canBorrowMore() { return activeLoans < 5; }

    /** BR04: nợ phạt không vượt 50.000đ */
    public boolean isDebtWithinLimit() { return debt <= 50_000; }

    public void incrementActiveLoans() { activeLoans++; }
    public void decrementActiveLoans() { if (activeLoans > 0) activeLoans--; }

    public void addDebt(long amount) { debt += amount; }
}
