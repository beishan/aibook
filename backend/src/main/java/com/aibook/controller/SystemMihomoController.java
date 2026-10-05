package com.aibook.controller;

import com.aibook.dto.crawler.MihomoDtos.*;
import com.aibook.service.SystemMihomoService;
import com.aibook.service.SystemMihomoService.ConnectionPayload;
import com.aibook.service.UserService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/proxy-settings/system")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SystemMihomoController {
    private final SystemMihomoService service;
    private final UserService users;

    @PostMapping("/mihomo/catalog")
    public CatalogView browse(@RequestBody ConnectionPayload request) throws Exception {
        return service.browse(request);
    }

    @PostMapping("/mihomo/test")
    public ConnectionTestView test(@RequestBody ConnectionPayload request) throws Exception {
        return service.test(request);
    }

    @PostMapping("/{id}/mihomo/delay")
    public DelayView delay(@PathVariable Long id, @RequestBody NodeRequest request) throws Exception {
        return service.delay(id, request.name());
    }

    @GetMapping("/{id}/mihomo/groups")
    public List<NodeGroupView> groups(Authentication auth, @PathVariable Long id) {
        return service.list(id, users.findByUsername(auth.getName()).getId());
    }

    @PostMapping("/{id}/mihomo/groups")
    public NodeGroupView create(Authentication auth, @PathVariable Long id,
            @RequestBody NodeGroupPayload request) throws Exception {
        return service.save(id, null, users.findByUsername(auth.getName()).getId(), request);
    }

    @PutMapping("/{id}/mihomo/groups/{groupId}")
    public NodeGroupView update(Authentication auth, @PathVariable Long id, @PathVariable Long groupId,
            @RequestBody NodeGroupPayload request) throws Exception {
        return service.save(id, groupId, users.findByUsername(auth.getName()).getId(), request);
    }

    @DeleteMapping("/{id}/mihomo/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id, @PathVariable Long groupId) {
        service.delete(id, groupId, users.findByUsername(auth.getName()).getId());
    }

    @ExceptionHandler(IOException.class)
    public void apiFailure(IOException exception) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mihomo API 操作失败，请检查连接和节点状态");
    }
}
