package org.example.synchro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "o email não deve ser vazio")
    @Email(message = "o email deve ser valido")
    private String email;

    @NotBlank(message = "a senha não pode ser vazia")
    @Size(min = 6, message = "a senha deve ter no mínimo 6 caracteres")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
