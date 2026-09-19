package com.collabcase.ia.dto;

public record OperacionIa(
        String tipo,
        String clase,
        String nuevoNombre,
        String atributo,
        String nuevoNombreAtributo,
        String tipoDato,
        Boolean permiteNulo,
        Boolean identificador,
        String claseOrigen,
        String claseDestino,
        String tipoRelacion,
        String multiplicidadOrigen,
        String multiplicidadDestino,
        String nombreRelacion,
        String nuevoNombreRelacion
) {
}
