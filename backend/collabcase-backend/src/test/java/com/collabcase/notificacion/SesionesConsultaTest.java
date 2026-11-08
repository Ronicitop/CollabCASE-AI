package com.collabcase.notificacion;

import com.collabcase.colaboracion.dominio.SesionColaborativa;
import com.collabcase.colaboracion.repositorio.SesionColaborativaRepository;
import com.collabcase.colaboracion.servicio.SesionColaborativaService;
import com.collabcase.proyecto.dominio.Proyecto;
import com.collabcase.proyecto.repositorio.ProyectoRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SesionesConsultaTest {
    @Test void listaSinCrearSesionesYValidaProyecto() {
        var proyectos = mock(ProyectoRepository.class);
        var sesiones = mock(SesionColaborativaRepository.class);
        var service = new SesionColaborativaService(sesiones, proyectos);
        UUID id = UUID.randomUUID();
        var proyecto = Proyecto.builder().id(id).build();
        var sala = SesionColaborativa.builder().id(UUID.randomUUID()).codigo("ABC12345")
                .proyecto(proyecto).activa(true).creadaEn(LocalDateTime.now()).build();
        when(proyectos.existsById(id)).thenReturn(true);
        when(sesiones.findByProyectoId(id)).thenReturn(List.of(sala));
        assertEquals("ABC12345", service.listarPorProyecto(id).getFirst().codigo());
        verify(sesiones, never()).save(any());
        assertThrows(IllegalArgumentException.class, () -> service.listarPorProyecto(UUID.randomUUID()));
    }
}
