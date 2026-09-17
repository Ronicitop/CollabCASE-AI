package com.collabcase.modelado.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ModeloCompletoRequest(

        @Valid
        @NotNull
        List<ClaseDiagramaRequest> clases,

        @Valid
        @NotNull
        List<RelacionDiagramaRequest> relaciones

) {
}
