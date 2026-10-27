package org.example.synchro.dto;

public class MidiaRatingDto {

    private Long midiaId;
    private Double media;
    private Long total;

    public MidiaRatingDto(Long midiaId, Double media, Long total) {
        this.midiaId = midiaId;
        this.media = media;
        this.total = total;
    }

    public Long getMidiaId() {
        return midiaId;
    }

    public Double getMedia() {
        return media;
    }

    public Long getTotal() {
        return total;
    }
}