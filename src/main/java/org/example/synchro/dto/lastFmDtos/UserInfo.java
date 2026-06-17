package org.example.synchro.dto.lastFmDtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo {

    private String name;
    private String realname;
    private String url;
    private String country;
    private String gender;
    private String age;
    private String subscriber;
    private String playcount;
    private String playlists;
    private String track_count;
    private String album_count;
    private String artist_count;

    @JsonProperty("image")
    private List<ImageDto> images;

    private RegisteredDto registered;
}