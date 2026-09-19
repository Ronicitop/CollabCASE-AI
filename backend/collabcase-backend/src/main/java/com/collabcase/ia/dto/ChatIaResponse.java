package com.collabcase.ia.dto;

import com.collabcase.modelado.dto.ModeloCompletoResponse;

import java.util.List;

public record ChatIaResponse(

        String mensaje,

        List<String> accionesEjecutadas,

        ModeloCompletoResponse modelo

) {
}
