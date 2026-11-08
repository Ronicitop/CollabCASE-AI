package com.collabcase.colaboracion;

import com.collabcase.colaboracion.controlador.SalaColaborativaController;
import com.collabcase.colaboracion.dominio.MensajeSala;
import com.collabcase.colaboracion.dto.MensajeSalaDTO;
import com.collabcase.colaboracion.repositorio.*;
import com.collabcase.colaboracion.servicio.*;
import com.collabcase.ia.servicio.ServicioIaPar;
import com.collabcase.modelado.repositorio.*;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import com.collabcase.notificacion.NotificacionesService;
import com.collabcase.proyecto.dominio.Proyecto;
import com.collabcase.proyecto.repositorio.ProyectoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(showSql = false, properties = {"spring.config.import=", "spring.jpa.show-sql=false", "push.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:chat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="})
@Import({ChatProyectoService.class, SesionColaborativaService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatProyectoTest {
    @Autowired ChatProyectoService chat;
    @Autowired SesionColaborativaService colaboracion;
    @Autowired MensajeSalaRepository mensajes;
    @Autowired SesionColaborativaRepository sesiones;
    @Autowired ProyectoRepository proyectos;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean NotificacionesService push;
    @MockitoBean SimpMessagingTemplate websocket;

    @BeforeEach void limpiar() {
        mensajes.deleteAll(); sesiones.deleteAll(); proyectos.deleteAll();
        clearInvocations(push, websocket);
    }

    private UUID proyecto() {
        return proyectos.save(Proyecto.builder().nombre("Proyecto").build()).getId();
    }

    private MensajeSalaDTO mensaje(String texto, String tipo) {
        // El ID de sesión del cuerpo no debe poder cambiar el destino validado de la ruta.
        return new MensajeSalaDTO(null, UUID.randomUUID(), "Equipo", tipo, texto, null);
    }

    private SalaColaborativaController controller(ServicioIaPar ia) {
        return new SalaColaborativaController(push, chat, websocket, ia,
                mock(ModeloDiagramaService.class), sesiones, mock(ClaseDiagramaRepository.class),
                mock(AtributoDiagramaRepository.class), mock(RelacionDiagramaRepository.class));
    }

    @Test void nuevasSesionesCompartenHistorialAnteriorSinMezclarProyectos() {
        UUID proyecto = proyecto();
        var primera = colaboracion.iniciarSesion(proyecto);
        var uno = chat.guardar(primera.id(), mensaje("Anterior", "USUARIO"));
        var segunda = colaboracion.iniciarSesion(proyecto);
        assertNotEquals(primera.codigo(), segunda.codigo());
        var dos = chat.guardar(segunda.id(), mensaje("Nuevo", "USUARIO"));
        var otra = colaboracion.iniciarSesion(proyecto());
        chat.guardar(otra.id(), mensaje("Otro proyecto", "USUARIO"));
        clearInvocations(push, websocket);

        var historial = chat.historialProyecto(proyecto);
        assertEquals(java.util.List.of(uno.getId(), dos.getId()), historial.stream().map(MensajeSalaDTO::getId).toList());
        assertEquals(historial.stream().map(MensajeSalaDTO::getId).toList(),
                chat.historialSesion(colaboracion.unirseSesion(segunda.codigo()).id()).stream().map(MensajeSalaDTO::getId).toList());
        assertEquals(primera.id(), historial.getFirst().getSesionId());
        assertEquals(segunda.id(), historial.getLast().getSesionId());
        assertEquals(3, mensajes.count());
        verifyNoInteractions(push, websocket);
    }

    @Test void restYWebsocketPublicanUnaVezEnElMismoCanalDeProyecto() {
        UUID proyecto = proyecto();
        var primera = colaboracion.iniciarSesion(proyecto);
        var segunda = colaboracion.iniciarSesion(proyecto);
        var controller = controller(mock(ServicioIaPar.class));
        var uno = controller.recibirMensajeRest(primera.id(), mensaje("REST", "USUARIO")).getBody();
        controller.recibirYTransmitirMensaje(segunda.id(), mensaje("WS", "USUARIO"));
        var dos = chat.historialProyecto(proyecto).stream().filter(m -> "WS".equals(m.getContenido())).findFirst().orElseThrow();
        verify(websocket, times(1)).convertAndSend("/topic/proyecto/" + proyecto + "/mensajes", uno);
        verify(websocket, times(1)).convertAndSend(eq("/topic/proyecto/" + proyecto + "/mensajes"),
                argThat((Object value) -> value instanceof MensajeSalaDTO dto && dos.getId().equals(dto.getId())));
        verify(push, times(1)).mensaje(proyecto, primera.id(), uno.getId());
        verify(push, times(1)).mensaje(proyecto, segunda.id(), dos.getId());
        verifyNoMoreInteractions(push, websocket);
    }

    @Test void rollbackNoTransmiteYNoGuardaElMensaje() {
        var sesion = colaboracion.iniciarSesion(proyecto());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            chat.guardar(sesion.id(), mensaje("No confirmado", "USUARIO"));
            verifyNoInteractions(websocket);
            status.setRollbackOnly();
        });
        assertEquals(0, mensajes.count());
        verifyNoInteractions(websocket);
    }

    @Test void validaSesionAntesDeGuardar() {
        assertThrows(IllegalArgumentException.class, () -> chat.guardar(UUID.randomUUID(), mensaje("Hola", "USUARIO")));
        var sesion = colaboracion.iniciarSesion(proyecto());
        var entidad = sesiones.findById(sesion.id()).orElseThrow();
        entidad.setActiva(false); sesiones.save(entidad);
        assertThrows(IllegalStateException.class, () -> chat.guardar(sesion.id(), mensaje("Hola", "USUARIO")));
        assertEquals(0, mensajes.count());
        verifyNoInteractions(push, websocket);
    }

    @Test void proyectoVacioNoCreaSesiones() {
        assertTrue(chat.historialProyecto(proyecto()).isEmpty());
        assertEquals(0, sesiones.count());
        assertThrows(IllegalArgumentException.class, () -> chat.historialProyecto(UUID.randomUUID()));
        verifyNoInteractions(push, websocket);
    }

    @Test void ordenaPorFechaYDesempataDeFormaEstable() {
        UUID proyecto = proyecto();
        var sesion = colaboracion.iniciarSesion(proyecto);
        for (int i = 0; i < 3; i++) {
            var m = new MensajeSala(sesion.id(), "Equipo", MensajeSala.TipoEmisor.USUARIO, "Histórico " + i);
            m.setFechaHora(LocalDateTime.of(2026, 11, 1, i == 0 ? 12 : 10, 0));
            mensajes.save(m);
        }
        var historial = chat.historialProyecto(proyecto);
        assertEquals(10, historial.getFirst().getFechaHora().getHour());
        assertEquals(12, historial.getLast().getFechaHora().getHour());
        assertEquals(historial.stream().map(MensajeSalaDTO::getId).toList(),
                chat.historialProyecto(proyecto).stream().map(MensajeSalaDTO::getId).toList());
    }

    @Test void respuestaIaTambienSePublicaEnElChatDelProyecto() {
        UUID proyecto = proyecto();
        var sesion = colaboracion.iniciarSesion(proyecto);
        var ia = mock(ServicioIaPar.class);
        when(ia.procesarConsultaEnSala(eq(sesion.id()), anyString(), anyString())).thenReturn("Respuesta simulada");
        controller(ia).recibirMensajeRest(sesion.id(), mensaje("@ia explica el modelo", "USUARIO"));
        verify(websocket, timeout(5000)).convertAndSend(eq("/topic/proyecto/" + proyecto + "/mensajes"),
                argThat((Object value) -> value instanceof MensajeSalaDTO dto && "IA".equals(dto.getTipoEmisor())));
        assertEquals(2, chat.historialProyecto(proyecto).size());
        verify(push, times(2)).mensaje(eq(proyecto), eq(sesion.id()), any(UUID.class));
    }
}
