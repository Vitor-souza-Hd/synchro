package org.example.synchro.dto;

public record RecentReviewDto(Long id, Integer rating, String review, Long authorId, String username,
                              String avatarUrl, Long mediaId, String mediaType, String title,
                              String artistName, String coverUrl, String profileImageUrl, boolean authorVvip) {}
