package com.collabcase.notificacion;

import com.collabcase.modelado.dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ContenidoModeloTest {
    @Test void ignoraMovimientoVersionYFechaPeroDetectaContenido() {
        UUID id = UUID.randomUUID(), proyecto = UUID.randomUUID(), clase = UUID.randomUUID();
        var before = new ModeloCompletoResponse(id, proyecto, 1L, null,
                List.of(new ClaseDiagramaResponse(clase, "Cliente", 0d, 0d, List.of())), List.of());
        var moved = new ModeloCompletoResponse(id, proyecto, 2L, java.time.LocalDateTime.now(),
                List.of(new ClaseDiagramaResponse(clase, "Cliente", 800d, 400d, List.of())), List.of());
        var renamed = new ModeloCompletoResponse(id, proyecto, 3L, null,
                List.of(new ClaseDiagramaResponse(clase, "Persona", 800d, 400d, List.of())), List.of());
        assertEquals(ContenidoModelo.de(before), ContenidoModelo.de(moved));
        assertNotEquals(ContenidoModelo.de(before), ContenidoModelo.de(renamed));
    }
}
