package com.collabcase.modelado.dto;

import java.util.List;
import java.util.UUID;

public record ClaseDiagramaResponse(
        UUID id,
        String nombre,
        Double posicionX,
        Double posicionY,
        List<AtributoDiagramaResponse> atributos
) {
}
