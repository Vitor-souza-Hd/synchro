package org.example.synchro.services;

import org.example.synchro.dto.ProfileSelectionDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class ProfileSelection {
    private ProfileSelection() {}

    static List<Long> visibleIds(List<Long> available, String saved) {
        if (available.size() <= ProfileSelectionDto.LIMIT || saved == null) {
            return available.stream().limit(ProfileSelectionDto.LIMIT).toList();
        }
        if (saved.isEmpty()) return List.of();
        Set<Long> selected = Arrays.stream(saved.split(",")).map(Long::valueOf).collect(Collectors.toSet());
        return available.stream().filter(selected::contains).limit(ProfileSelectionDto.LIMIT).toList();
    }

    static ProfileSelectionDto options(List<ProfileSelectionDto.Item> items, String saved) {
        return new ProfileSelectionDto(items, visibleIds(items.stream().map(ProfileSelectionDto.Item::id).toList(), saved));
    }

    static String checkedValue(List<Long> available, List<Long> selected) {
        if (available.size() <= ProfileSelectionDto.LIMIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A seleção fica disponível acima de dez itens.");
        }
        if (selected.size() > ProfileSelectionDto.LIMIT || new HashSet<>(selected).size() != selected.size()
                || !new HashSet<>(available).containsAll(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha até dez itens públicos da sua conta.");
        }
        return selected.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
