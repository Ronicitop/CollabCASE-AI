package com.collabcase.comun.excepcion;

import java.time.LocalDateTime;

public record ErrorResponse(
        LocalDateTime fechaHora,
        int estado,
        String error,
        String mensaje,
        String ruta
) {
}
