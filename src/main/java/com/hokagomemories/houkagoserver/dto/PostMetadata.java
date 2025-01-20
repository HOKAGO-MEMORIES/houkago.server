package com.hokagomemories.houkagoserver.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostMetadata {
    private String title;
    private String date;
    private String desc;
    private String from;
    private String slug;
    private String thumbnail;
    private String content;
}
