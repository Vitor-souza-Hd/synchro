package org.example.synchro.dto.lastFmDtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({
        "name",
        "age",
        "subscriber",
        "realname",
        "bootstrap",
        "playlists",
        "country",
        "gender",
        "url",
        "playcount",
        "artistCount",
        "trackCount",
        "albumCount",
        "images",
        "registered"
})
public class UserInfo {

    private String name;
    private String age;
    private String subscriber;
    private String realname;
    private String bootstrap;
    private String playlists;
    private String country;
    private String gender;
    private String url;
    private String type;

    private String playcount;

    @JsonProperty("artist_count")
    private String artistCount;

    @JsonProperty("track_count")
    private String trackCount;

    @JsonProperty("album_count")
    private String albumCount;

    @JsonProperty("image")
    private List<ImageDto> images;

    private RegisteredDto registered;
}