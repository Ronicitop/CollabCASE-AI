package com.collabcase.notificacion;

import com.collabcase.modelado.dto.ModeloCompletoResponse;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Comparación estructural: ignora versión, fechas, orden y posiciones. */
public record ContenidoModelo(List<Clase> clases, List<?> relaciones) {
    public record Clase(UUID id, String nombre, List<?> atributos) {}

    public static ContenidoModelo de(ModeloCompletoResponse modelo) {
        return new ContenidoModelo(modelo.clases().stream()
                .sorted(Comparator.comparing(c -> c.id().toString()))
                .map(c -> new Clase(c.id(), c.nombre(), c.atributos().stream()
                        .sorted(Comparator.comparing(a -> a.id().toString())).toList())).toList(),
                modelo.relaciones().stream().sorted(Comparator.comparing(r -> r.id().toString())).toList());
    }
}
