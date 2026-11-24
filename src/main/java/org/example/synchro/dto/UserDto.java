package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.User;
import org.example.synchro.entities.UserData;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@JsonPropertyOrder({
        "id",
        "username",
        "email",
        "birthDay",
        "lastFmUsername",
        "lastFmConnected",
        "isPremium",
        "data"
})
public class UserDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private LocalDate birthDay;
    private String lastFmUsername;
    private boolean lastFmConnected;

    @JsonProperty("isPremium")
    private boolean isPremium;

    private UserDataDto data;

    public UserDto() {}

    public UserDto(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.birthDay = user.getBirthDay();
        this.lastFmUsername = user.getLastFmUsername();
        this.lastFmConnected = user.hasLastFmConnection();
        this.isPremium = user.isPremium(); // <--- Adicionado aqui!
        this.data = user.getData() == null ? new UserDataDto() : new UserDataDto(user.getData());
    }
}