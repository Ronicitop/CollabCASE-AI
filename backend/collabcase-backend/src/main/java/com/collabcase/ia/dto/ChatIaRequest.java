package com.collabcase.ia.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatIaRequest(

        @NotBlank
        String clienteId,

        @NotBlank
        String mensaje

) {
}
