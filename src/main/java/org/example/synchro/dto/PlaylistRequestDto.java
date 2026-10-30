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

    @jakarta.validation.constraints.Size(max = 2800000, message = "A capa deve ter no máximo 2 MB")
    @jakarta.validation.constraints.Pattern(regexp = "^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=]+$", message = "Formato de capa inválido")
    private String capa;

    @jakarta.validation.constraints.Size(max = 2800000, message = "O banner deve ter no máximo 2 MB")
    @jakarta.validation.constraints.Pattern(regexp = "^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=]+$", message = "Formato de banner inválido")
    private String banner;
}
