package com.hokagomemories.houkagoserver;

import com.hokagomemories.houkagoserver.service.GitHubService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
public class GitHubServiceTest {

    @Autowired
    private GitHubService gitHubService;

    @Test
    public void testGetFileContent() throws IOException {
        // GitHub API에서 특정 파일을 읽어오는 예시
        String fileContent = gitHubService.getFileContent("blog/0-test/0-test.mdx");

        // 파일 내용이 제대로 불러와졌는지 확인 (예시로 출력만 함)
        System.out.println(fileContent);
    }
}
