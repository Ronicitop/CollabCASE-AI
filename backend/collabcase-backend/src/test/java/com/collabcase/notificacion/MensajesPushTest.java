package com.collabcase.notificacion;

import com.collabcase.colaboracion.controlador.SalaColaborativaController;
import com.collabcase.colaboracion.dto.MensajeSalaDTO;
import com.collabcase.colaboracion.repositorio.SesionColaborativaRepository;
import com.collabcase.colaboracion.servicio.ChatProyectoService;
import com.collabcase.ia.servicio.ServicioIaPar;
import com.collabcase.modelado.repositorio.*;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MensajesPushTest {
    @Test void consultaLegadaYConsultaPorProyectoDeleganSinCrearMensajes() {
        var chat = mock(ChatProyectoService.class);
        var controller = new SalaColaborativaController(mock(NotificacionesService.class), chat,
                mock(SimpMessagingTemplate.class), mock(ServicioIaPar.class), mock(ModeloDiagramaService.class),
                mock(SesionColaborativaRepository.class), mock(ClaseDiagramaRepository.class),
                mock(AtributoDiagramaRepository.class), mock(RelacionDiagramaRepository.class));
        UUID proyecto = UUID.randomUUID(), sesion = UUID.randomUUID();
        var historial = java.util.List.of(new MensajeSalaDTO(UUID.randomUUID(), sesion, "Equipo", "USUARIO", "Anterior", null));
        when(chat.historialProyecto(proyecto)).thenReturn(historial);
        when(chat.historialSesion(sesion)).thenReturn(historial);
        assertEquals(historial, controller.obtenerHistorialProyecto(proyecto).getBody());
        assertEquals(historial, controller.obtenerHistorial(sesion).getBody());
        verify(chat, never()).guardar(any(), any());
    }
}
