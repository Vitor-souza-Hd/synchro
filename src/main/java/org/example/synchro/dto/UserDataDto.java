package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.User;
import org.example.synchro.entities.UserData;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@JsonPropertyOrder({
        "scrobbles",
        "artistas"
})
public class UserDataDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer scrobbles;
    private Integer Artistas;

    public UserDataDto(){}

    public UserDataDto(UserData data) {
        this.scrobbles = data.getScrobbles();
        this.Artistas = data.getArtistas();
    }
}
