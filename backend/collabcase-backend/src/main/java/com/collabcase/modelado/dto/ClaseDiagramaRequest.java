package com.collabcase.modelado.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ClaseDiagramaRequest(

        UUID id,

        @NotBlank
        String claveCliente,

        @NotBlank
        @Size(max = 100)
        String nombre,

        @NotNull
        Double posicionX,

        @NotNull
        Double posicionY,

        @Valid
        @NotNull
        List<AtributoDiagramaRequest> atributos

) {
}