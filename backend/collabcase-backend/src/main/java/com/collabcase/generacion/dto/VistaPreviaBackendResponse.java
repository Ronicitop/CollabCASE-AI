package com.collabcase.generacion.dto;

import java.util.List;
import java.util.UUID;

public record VistaPreviaBackendResponse(
        UUID proyectoId,
        int totalArchivos,
        List<ArchivoGeneradoResponse> archivos
) {
}
