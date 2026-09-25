package com.collabcase.modelado.dto;

import java.util.UUID;

public record RelacionDiagramaResponse(
        UUID id,
        UUID claseOrigenId,
        UUID claseDestinoId,
        String tipo,
        String multiplicidadOrigen,
        String multiplicidadDestino,
        String nombre,
        UUID claseAsociacionId
) {
}
