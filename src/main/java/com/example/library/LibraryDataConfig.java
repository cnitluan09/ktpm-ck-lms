package com.example.library;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LibraryDataConfig {

    @Bean
    ApplicationRunner seedData(BookRepository bookRepo, ReaderRepository readerRepo) {
        return args -> {
            if (bookRepo.count() > 0) return; // seed 1 lần duy nhất

            bookRepo.save(new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3));
            bookRepo.save(new Book("978-0-13-110362-7", "The C Programming Language", "Kernighan & Ritchie", 2));
            bookRepo.save(new Book("978-0-201-63361-0", "Design Patterns", "Gang of Four", 2));

            readerRepo.save(new Reader("R001", "Nguyễn Văn An", "an@example.com"));
            readerRepo.save(new Reader("R002", "Trần Thị Bình", "binh@example.com"));
        };
    }
}
