package com.aibook.controller;

import com.aibook.dto.BookDTO;
import com.aibook.dto.ShelfBookOrderRequest;
import com.aibook.dto.ShelfGroupDTO;
import com.aibook.dto.ShelfGroupOrderRequest;
import com.aibook.dto.ShelfGroupRequest;
import com.aibook.dto.ShelfMoveRequest;
import com.aibook.dto.ShelfOverviewDTO;
import com.aibook.model.entity.User;
import com.aibook.service.ShelfService;
import com.aibook.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shelf")
@RequiredArgsConstructor
public class ShelfController {

    private final ShelfService shelfService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ShelfOverviewDTO> getShelf(Authentication authentication) {
        return ResponseEntity.ok(shelfService.getShelf(currentUser(authentication)));
    }

    @PostMapping("/books/{bookId}")
    public ResponseEntity<BookDTO> addBook(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestBody(required = false) ShelfMoveRequest request) {
        Long groupId = request == null ? null : request.getGroupId();
        return ResponseEntity.ok(shelfService.addBook(bookId, groupId, currentUser(authentication)));
    }

    @DeleteMapping("/books/{bookId}")
    public ResponseEntity<BookDTO> removeBook(
            Authentication authentication, @PathVariable Long bookId) {
        return ResponseEntity.ok(shelfService.removeBook(bookId, currentUser(authentication)));
    }

    @PutMapping("/books/{bookId}/group")
    public ResponseEntity<BookDTO> moveBook(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestBody ShelfMoveRequest request) {
        return ResponseEntity.ok(shelfService.moveBook(
                bookId, request.getGroupId(), currentUser(authentication)));
    }

    @PutMapping("/books/order")
    public ResponseEntity<Void> reorderBooks(
            Authentication authentication,
            @Valid @RequestBody ShelfBookOrderRequest request) {
        shelfService.reorderBooks(request, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups")
    public ResponseEntity<ShelfGroupDTO> createGroup(
            Authentication authentication,
            @Valid @RequestBody ShelfGroupRequest request) {
        return ResponseEntity.ok(shelfService.createGroup(request, currentUser(authentication)));
    }

    @PutMapping("/groups/{groupId}")
    public ResponseEntity<ShelfGroupDTO> updateGroup(
            Authentication authentication,
            @PathVariable Long groupId,
            @Valid @RequestBody ShelfGroupRequest request) {
        return ResponseEntity.ok(
                shelfService.updateGroup(groupId, request, currentUser(authentication)));
    }

    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            Authentication authentication, @PathVariable Long groupId) {
        shelfService.deleteGroup(groupId, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/groups/order")
    public ResponseEntity<Void> reorderGroups(
            Authentication authentication,
            @Valid @RequestBody ShelfGroupOrderRequest request) {
        shelfService.reorderGroups(request, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
