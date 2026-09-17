package com.collabcase.modelado.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AtributoDiagramaRequest(

        UUID id,

        @NotBlank
        @Size(max = 100)
        String nombre,

        @NotBlank
        @Size(max = 50)
        String tipoDato,

        @NotNull
        Boolean permiteNulo,

        @NotNull
        Boolean identificador

) {
}
