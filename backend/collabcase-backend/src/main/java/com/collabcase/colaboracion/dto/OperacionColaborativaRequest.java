package com.collabcase.colaboracion.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OperacionColaborativaRequest(

        @NotNull
        UUID operacionId,

        @NotBlank
        String clienteId,

        @NotNull
        TipoOperacionColaborativa tipo,

        @NotNull
        JsonNode datos

) {
}
