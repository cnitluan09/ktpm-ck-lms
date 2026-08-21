package com.example.library;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReaderRepository extends JpaRepository<Reader, String> {
    Optional<Reader> findByEmail(String email);
    boolean existsByEmail(String email);
}
