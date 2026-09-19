package com.collabcase.interoperabilidad.dto;

import java.util.List;

public record ModeloImportadoXmi(
        String nombre,
        List<ClaseImportada> clases,
        List<RelacionImportada> relaciones
) {

    public record ClaseImportada(
            String xmiId,
            String nombre,
            Double posicionX,
            Double posicionY,
            List<AtributoImportado> atributos
    ) {
    }

    public record AtributoImportado(
            String xmiId,
            String nombre,
            String tipoDato,
            Boolean permiteNulo,
            Boolean identificador
    ) {
    }

    public record RelacionImportada(
            String xmiId,
            String nombre,
            String tipo,
            String claseOrigenXmiId,
            String claseDestinoXmiId,
            String multiplicidadOrigen,
            String multiplicidadDestino
    ) {
    }
}
