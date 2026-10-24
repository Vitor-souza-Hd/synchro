package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @JsonAlias("email")
    @NotBlank(message = "o usuário ou e-mail não deve ser vazio")
    private String username;

    @NotBlank(message = "a senha não pode ser vazia")
    @Size(min = 6, message = "a senha deve ter no mínimo 6 caracteres")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
