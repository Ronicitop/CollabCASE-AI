package com.collabcase.modelado.dto;

import java.util.UUID;

public record AtributoDiagramaResponse(
        UUID id,
        String nombre,
        String tipoDato,
        Boolean permiteNulo,
        Boolean identificador
) {
}
