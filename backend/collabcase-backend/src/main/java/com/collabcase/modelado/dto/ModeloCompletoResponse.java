package com.collabcase.modelado.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ModeloCompletoResponse(
        UUID id,
        UUID proyectoId,
        Long version,
        LocalDateTime actualizadoEn,
        List<ClaseDiagramaResponse> clases,
        List<RelacionDiagramaResponse> relaciones
) {
}
