package org.example.synchro.dto;

/** The list only carries the fields needed by a playlist card. */
public record PlaylistSummaryDto(Long id, String titulo, int totalMusicas,
                                 SearchUserDTO criador, String capa, String banner) {}
