package org.example.synchro.dto;

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
  "username",
  "email",
  "birthDay",
  "lastFmUsername",
  "data"
})
public class UserDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    private String username;
    private String email;
    private LocalDate birthDay;
    private String lastFmUsername;

    private UserDataDto data;

    public UserDto() {}

    public UserDto(User user) {
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.birthDay = user.getBirthDay();
        this.lastFmUsername = user.getLastFmUsername();
        this.data = new UserDataDto(user.getData());
    }


}
