package com.hokagomemories.houkagoserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

@Data
public class GitHubContent {
    private String name;
    private String path;
    private String sha;
    private String type;

    @Getter
    @JsonProperty("content")
    private String content;

    @JsonProperty("download_url")
    private String downloadUrl;

    @JsonProperty("html_url")
    private String htmlUrl;
}
