package org.example.synchro.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfileSelectionDto(List<Item> items, List<Long> selectedIds) {
    public static final int LIMIT = 10;

    public record Item(Long id, String title) {}

    public record Request(@NotNull @Size(max = LIMIT) List<@NotNull @Positive Long> ids) {}
}
