package org.example.synchro.dto.lastFmDtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LastFmUserDto {

    @JsonProperty("user")
    private UserInfo user;

    // Campos de erro da API (caso aconteça)
    private Integer error;
    private String message;
}

