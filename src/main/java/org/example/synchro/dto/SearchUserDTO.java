package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record SearchUserDTO(
        Long id,
        String username,
        @JsonInclude(JsonInclude.Include.NON_NULL) String fullName
) {
    public SearchUserDTO(Long id, String username) {
        this(id, username, null);
    }
}

