package com.collabcase.modelado.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RelacionDiagramaRequest(

        UUID id,

        @NotBlank
        String claseOrigenClave,

        @NotBlank
        String claseDestinoClave,

        @NotBlank
        @Size(max = 30)
        String tipo,

        @Size(max = 20)
        String multiplicidadOrigen,

        @Size(max = 20)
        String multiplicidadDestino,

        @Size(max = 100)
        String nombre

) {
}