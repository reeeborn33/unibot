package org.example.worldone.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldEventRepository extends JpaRepository<WorldEventEntity, String> {
    List<WorldEventEntity> findByStatusOrderByCreatedAtAsc(String status);
    List<WorldEventEntity> findByWorldIdAndStatusOrderByCreatedAtAsc(String worldId, String status);
    List<WorldEventEntity> findByWorldIdOrderByCreatedAtDesc(String worldId);
}
