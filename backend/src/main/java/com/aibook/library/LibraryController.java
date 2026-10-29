package com.aibook.library;

import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/libraries")
@RequiredArgsConstructor
public class LibraryController {
    private final LibraryService service;
    private final UserService users;

    public record LibraryRequest(String name, String description, Map<String, Object> config, long revision) {}
    public record BatchRequest(List<Long> libraryIds, List<Long> bookIds, Long listId) {}
    public record CollectionRequest(String name, String description, boolean recommended) {}
    public record ItemRequest(Long groupId, Boolean favorite, Boolean featured, boolean move) {}
    public record RemoveRequest(List<Long> bookIds, Long listId) {}
    public record OrderRequest(String kind, long itemId, long otherId, Long listId) {}

    private User user(Authentication authentication) {
        return users.findByUsername(authentication.getName());
    }

    @GetMapping("/preferences")
    public Map<String, Integer> preferences(Authentication auth) {
        return Map.of("pageSize", service.pageSize(user(auth)));
    }

    @PutMapping("/preferences")
    public void preferences(Authentication auth, @RequestBody Map<String, Integer> body) {
        service.pageSize(user(auth), body.getOrDefault("pageSize", 24));
    }

    @GetMapping
    public LibraryService.Page list(Authentication auth, @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size,
            @RequestParam(defaultValue = "manual") String sort) {
        return service.libraries(user(auth), keyword, page, size, sort);
    }

    @PostMapping
    public Map<String, Object> create(Authentication auth, @RequestBody LibraryRequest request) {
        return service.create(user(auth), request.name(), request.description(), request.config());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(Authentication auth, @PathVariable long id) {
        return service.library(user(auth), id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> save(Authentication auth, @PathVariable long id, @RequestBody LibraryRequest request) {
        return service.save(user(auth), id, request.revision(), request.name(), request.description(), request.config());
    }

    @DeleteMapping("/{id}")
    public void delete(Authentication auth, @PathVariable long id) {
        service.delete(user(auth), id);
    }

    public record ItemsRequest(List<Long> bookIds, Long groupId, Boolean favorite, boolean move) {}

    @GetMapping("/{id}/membership")
    public List<Long> membership(Authentication auth, @PathVariable long id,
            @RequestParam List<Long> bookIds, @RequestParam(required = false) Long listId) {
        return service.membership(user(auth), id, bookIds, listId);
    }

    @PutMapping("/{id}/books")
    public void items(Authentication auth, @PathVariable long id, @RequestBody ItemsRequest request) {
        service.items(user(auth), id, request.bookIds(), request.groupId(), request.favorite(), request.move());
    }

    @GetMapping("/{id}/home")
    public Map<String, Object> home(Authentication auth, @PathVariable long id) {
        return service.home(user(auth), id);
    }

    @GetMapping("/{id}/books")
    public LibraryService.Page books(Authentication auth, @PathVariable long id,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long listId, @RequestParam(defaultValue = "false") boolean favorites,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId, @RequestParam(defaultValue = "added") String sort,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
        return service.books(user(auth), id, keyword, groupId, listId, favorites, status, categoryId, tagId, sort, page, size);
    }

    @PostMapping("/books")
    public List<LibraryService.BatchItem> add(Authentication auth, @RequestBody BatchRequest request) {
        return service.add(user(auth), request.libraryIds(), request.bookIds(), request.listId());
    }

    @PutMapping("/{id}/books/{bookId}")
    public void item(Authentication auth, @PathVariable long id, @PathVariable long bookId,
            @RequestBody ItemRequest request) {
        service.item(user(auth), id, bookId, request.groupId(), request.favorite(), request.featured(), request.move());
    }

    @PostMapping("/{id}/remove")
    public void remove(Authentication auth, @PathVariable long id, @RequestBody RemoveRequest request) {
        service.remove(user(auth), id, request.bookIds(), request.listId());
    }

    @PostMapping("/{id}/{kind:groups|lists}")
    public void createCollection(Authentication auth, @PathVariable long id, @PathVariable String kind,
            @RequestBody CollectionRequest request) {
        service.collectionSave(user(auth), id, kind, null, request.name(), request.description(), request.recommended());
    }

    @PutMapping("/{id}/{kind:groups|lists}/{collectionId}")
    public void saveCollection(Authentication auth, @PathVariable long id, @PathVariable String kind,
            @PathVariable long collectionId, @RequestBody CollectionRequest request) {
        service.collectionSave(user(auth), id, kind, collectionId, request.name(), request.description(), request.recommended());
    }

    @DeleteMapping("/{id}/{kind:groups|lists}/{collectionId}")
    public void deleteCollection(Authentication auth, @PathVariable long id, @PathVariable String kind,
            @PathVariable long collectionId) {
        service.collectionDelete(user(auth), id, kind, collectionId);
    }

    @PutMapping("/{id}/order")
    public void order(Authentication auth, @PathVariable long id, @RequestBody OrderRequest request) {
        service.order(user(auth), id, request.kind(), request.itemId(), request.otherId(), request.listId());
    }
}
