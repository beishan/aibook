package com.aibook.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BookMembershipRequest(
        @NotEmpty @Size(max = 200) List<@NotNull @Positive Long> bookIds,
        @NotNull Action action,
        @Size(max = 20) List<@NotNull @Positive Long> listIds) {

    public enum Action {
        SHELF, FAVORITE, BOOK_LIST
    }
}
