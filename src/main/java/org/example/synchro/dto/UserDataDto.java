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
        "username",
        "fullName",
        "gender",
        "scrobbles",
        "artistas"
})
public class UserDataDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String fullName;
    private String gender;
    private Integer scrobbles;
    private Integer artistas;

    public UserDataDto(){}

    public UserDataDto(UserData data) {
        this.username = data.getUsername();
        this.fullName = data.getFullName();
        this.gender = data.getGender();
        this.scrobbles = data.getScrobbles();
        this.artistas = data.getArtistas();
    }
}
