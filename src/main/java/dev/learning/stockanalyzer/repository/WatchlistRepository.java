package dev.learning.stockanalyzer.repository;

import dev.learning.stockanalyzer.entity.WatchlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistEntity, Long> {

    Optional<WatchlistEntity> findByFullCode(String fullCode);

    boolean existsByFullCode(String fullCode);

    void deleteByFullCode(String fullCode);

    List<WatchlistEntity> findAllByOrderByAddedTimeDesc();
}
