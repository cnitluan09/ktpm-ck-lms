package com.example.library;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "reader_id")
    private Reader reader;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "isbn")
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;

    @Column(nullable = false)
    private LocalDate requestDate;

    private LocalDate approveDate;
    private LocalDate dueDate;
    private LocalDate returnDate;

    /** BR07: chỉ được gia hạn 1 lần */
    @Column(nullable = false)
    private boolean renewed = false;

    protected Loan() {}

    public Loan(Reader reader, Book book, LocalDate requestDate) {
        this.reader = reader;
        this.book = book;
        this.requestDate = requestDate;
        this.status = LoanStatus.PENDING;
    }

    public Long getId() { return id; }
    public Reader getReader() { return reader; }
    public Book getBook() { return book; }
    public LoanStatus getStatus() { return status; }
    public LocalDate getRequestDate() { return requestDate; }
    public LocalDate getApproveDate() { return approveDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public boolean isRenewed() { return renewed; }

    /** BR02: hạn trả = ngày duyệt + 14 ngày */
    public void approve(LocalDate approveDate) {
        this.approveDate = approveDate;
        this.dueDate = approveDate.plusDays(14);
        this.status = LoanStatus.BORROWED;
    }

    public void reject() {
        this.status = LoanStatus.REJECTED;
    }

    public void markReturned(LocalDate returnDate) {
        this.returnDate = returnDate;
        this.status = LoanStatus.RETURNED;
    }

    /** BR08: gia hạn thêm 7 ngày */
    public void renew() {
        this.dueDate = this.dueDate.plusDays(7);
        this.renewed = true;
    }
}
