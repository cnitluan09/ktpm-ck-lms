package com.example.library;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @Column(length = 20)
    private String isbn;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String author;

    @Column(nullable = false)
    private int totalCopies;

    @Column(nullable = false)
    private int availableCopies;

    protected Book() {}

    public Book(String isbn, String title, String author, int totalCopies) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }

    /** BR05: kiểm tra còn tồn kho */
    public boolean isAvailable() { return availableCopies > 0; }

    public void decreaseAvailable() {
        if (availableCopies <= 0) throw new IllegalStateException("Sách đã hết, không thể mượn");
        availableCopies--;
    }

    public void increaseAvailable() {
        if (availableCopies >= totalCopies) throw new IllegalStateException("Số lượng tồn kho không hợp lệ");
        availableCopies++;
    }
}
