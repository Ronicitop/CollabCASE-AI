package com.collabcase.colaboracion.dto;

import com.collabcase.modelado.dto.ModeloCompletoResponse;

import java.util.UUID;

public record OperacionColaborativaResponse(

        UUID operacionId,

        String clienteId,

        TipoOperacionColaborativa tipo,

        ModeloCompletoResponse modelo

) {
}
