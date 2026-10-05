package com.aibook.controller;

import com.aibook.dto.crawler.MihomoDtos.*;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerMihomoService;
import com.aibook.service.crawler.MihomoApiClient;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/crawler/tasks/queues/{queueId}/executors/{executorId}/mihomo")
@RequiredArgsConstructor
public class CrawlerMihomoController {
    private final CrawlerMihomoService service;
    private final UserService users;

    @GetMapping
    public PolicyView get(Authentication auth, @PathVariable Long queueId, @PathVariable Long executorId) {
        return service.get(users.findByUsername(auth.getName()), queueId, executorId);
    }

    @PutMapping
    public PolicyView save(Authentication auth, @PathVariable Long queueId, @PathVariable Long executorId,
            @RequestBody PolicyPayload payload) throws Exception {
        return service.save(users.findByUsername(auth.getName()), queueId, executorId, payload);
    }

    @PostMapping("/catalog")
    public CatalogView catalog(Authentication auth, @PathVariable Long queueId, @PathVariable Long executorId,
            @RequestBody PolicyPayload draft) throws Exception {
        return service.browse(users.findByUsername(auth.getName()), queueId, executorId, draft);
    }

    @PostMapping("/delay")
    public DelayView delay(Authentication auth, @PathVariable Long queueId, @PathVariable Long executorId,
            @RequestBody NodeRequest request) throws Exception {
        return service.test(users.findByUsername(auth.getName()), queueId, executorId, request.name());
    }

    @PostMapping("/switch")
    public PolicyView select(Authentication auth, @PathVariable Long queueId, @PathVariable Long executorId,
            @RequestBody NodeRequest request) throws Exception {
        return service.switchManually(users.findByUsername(auth.getName()), queueId, executorId, request.name());
    }

    @ExceptionHandler(IOException.class)
    public void apiFailure(IOException exception) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                exception instanceof MihomoApiClient.ApiException ? exception.getMessage()
                        : "Mihomo 连接、检测或切换失败，请检查控制地址、密钥及节点状态");
    }
}
