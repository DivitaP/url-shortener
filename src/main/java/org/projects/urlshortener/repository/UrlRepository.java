package org.projects.urlshortener.repository;


import org.projects.urlshortener.model.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    // Cleanup job: delete all expired URLs
    @Modifying
    @Query("DELETE FROM Url u WHERE u.expiresAt < :now")
    int deleteAllExpiredBefore(LocalDateTime now);
}