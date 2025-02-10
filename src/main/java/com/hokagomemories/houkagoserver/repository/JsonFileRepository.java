package com.hokagomemories.houkagoserver.repository;

import com.hokagomemories.houkagoserver.dto.JsonFileMetadata;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JsonFileRepository extends JpaRepository<JsonFileMetadata, Long> {
    Optional<JsonFileMetadata> findByFileName(String fileName);
}
