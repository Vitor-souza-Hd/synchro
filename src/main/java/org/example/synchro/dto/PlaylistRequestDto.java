package org.example.synchro.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaylistRequestDto {

    @NotBlank(message = "O título da playlist é obrigatório")
    private String titulo;

    private String descricao;
}
