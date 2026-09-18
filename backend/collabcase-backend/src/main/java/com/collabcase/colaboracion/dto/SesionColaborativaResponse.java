package com.collabcase.colaboracion.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SesionColaborativaResponse(
        UUID id,
        String codigo,
        UUID proyectoId,
        boolean activa,
        LocalDateTime creadaEn
) {
}
