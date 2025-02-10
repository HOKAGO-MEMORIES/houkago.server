package com.hokagomemories.houkagoserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokagomemories.houkagoserver.dto.JsonFileMetadata;
import com.hokagomemories.houkagoserver.repository.JsonFileRepository;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JsonFileService {
    private final JsonFileRepository jsonFileRepository;
    private final ObjectMapper objectMapper;

    public String saveJsonFile(String fileName, Object content) throws IOException {
        String jsonString = objectMapper.writeValueAsString(content);
        JsonFileMetadata jsonFile = jsonFileRepository.findByFileName(fileName)
                .orElse(JsonFileMetadata.builder().fileName(fileName).build());

        jsonFile.setContent(jsonString);
        jsonFileRepository.save(jsonFile);

        return fileName;
    }

    public Optional<String> getJsonFile(String fileName) {
        return jsonFileRepository.findByFileName(fileName)
                .map(JsonFileMetadata::getContent);
    }
}
