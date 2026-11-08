package com.collabcase.notificacion;

import java.util.Map;
import java.util.UUID;

public record AvisoPush(String tipo, UUID proyectoId, UUID sesionId, UUID mensajeId) {
    public Map<String, String> datos() {
        var datos = new java.util.HashMap<String, String>();
        datos.put("tipo", tipo);
        datos.put("proyectoId", proyectoId.toString());
        if (sesionId != null) datos.put("sesionId", sesionId.toString());
        if (mensajeId != null) datos.put("mensajeId", mensajeId.toString());
        return Map.copyOf(datos);
    }
}
