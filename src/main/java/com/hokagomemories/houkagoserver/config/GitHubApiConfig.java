package com.hokagomemories.houkagoserver.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@ConfigurationProperties(prefix = "github")
public class GitHubApiConfig {
    private String apiUrl;
    private String token;
    private String imageUrl;
    private String[] allowedOrigins;
}
