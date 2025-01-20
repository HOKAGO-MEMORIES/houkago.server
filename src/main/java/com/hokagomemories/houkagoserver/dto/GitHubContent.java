package com.hokagomemories.houkagoserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GitHubContent {
    private String name;
    private String path;
    private String sha;
    private String type;
    private String content;
    private String downloadUrl;

    @JsonProperty("html_url")
    private String htmlUrl;
}
