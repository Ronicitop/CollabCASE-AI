package com.collabcase.ia.dto;

import java.util.List;

public record PlanIa(
        String mensaje,
        List<OperacionIa> operaciones
) {
}
