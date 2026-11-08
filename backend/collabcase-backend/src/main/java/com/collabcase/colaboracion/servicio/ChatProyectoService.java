package com.collabcase.colaboracion.servicio;

import com.collabcase.colaboracion.dominio.MensajeSala;
import com.collabcase.colaboracion.dominio.SesionColaborativa;
import com.collabcase.colaboracion.dto.MensajeSalaDTO;
import com.collabcase.colaboracion.repositorio.MensajeSalaRepository;
import com.collabcase.colaboracion.repositorio.SesionColaborativaRepository;
import com.collabcase.notificacion.NotificacionesService;
import com.collabcase.proyecto.repositorio.ProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatProyectoService {
    private static final Logger log = LoggerFactory.getLogger(ChatProyectoService.class);
    private final MensajeSalaRepository mensajes;
    private final SesionColaborativaRepository sesiones;
    private final ProyectoRepository proyectos;
    private final NotificacionesService notificaciones;
    private final ApplicationEventPublisher eventos;
    private final SimpMessagingTemplate websocket;

    public record MensajePublicado(UUID proyectoId, MensajeSalaDTO mensaje) {}

    @Transactional(readOnly = true)
    public List<MensajeSalaDTO> historialProyecto(UUID proyectoId) {
        if (!proyectos.existsById(proyectoId)) throw new IllegalArgumentException("Proyecto no encontrado");
        return mensajes.findHistorialProyecto(proyectoId).stream().map(ChatProyectoService::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<MensajeSalaDTO> historialSesion(UUID sesionId) {
        return historialProyecto(sesion(sesionId).getProyecto().getId());
    }

    @Transactional
    public MensajeSalaDTO guardar(UUID sesionId, MensajeSalaDTO solicitud) {
        SesionColaborativa sesion = sesion(sesionId);
        if (!sesion.isActiva()) throw new IllegalStateException("La sesión colaborativa ya no está activa");
        if (solicitud.getContenido() == null || solicitud.getContenido().isBlank()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío");
        }
        var emisor = MensajeSala.TipoEmisor.valueOf(solicitud.getTipoEmisor());
        var guardado = mensajes.save(new MensajeSala(sesionId, solicitud.getRemitenteNombre(), emisor, solicitud.getContenido()));
        UUID proyectoId = sesion.getProyecto().getId();
        var respuesta = dto(guardado);
        if (emisor != MensajeSala.TipoEmisor.SISTEMA) {
            notificaciones.mensaje(proyectoId, sesionId, guardado.getId());
        }
        eventos.publishEvent(new MensajePublicado(proyectoId, respuesta));
        return respuesta;
    }

    @TransactionalEventListener
    public void transmitir(MensajePublicado aviso) {
        try {
            websocket.convertAndSend("/topic/proyecto/" + aviso.proyectoId() + "/mensajes", aviso.mensaje());
        } catch (Exception ex) {
            // El historial sigue disponible si un cliente o el broker se desconecta.
            log.warn("No se pudo transmitir un mensaje del proyecto: {}", ex.getClass().getSimpleName());
        }
    }

    private SesionColaborativa sesion(UUID id) {
        return sesiones.findById(id).orElseThrow(() -> new IllegalArgumentException("Sesión colaborativa no encontrada"));
    }

    private static MensajeSalaDTO dto(MensajeSala mensaje) {
        return new MensajeSalaDTO(mensaje.getId(), mensaje.getSesionId(), mensaje.getRemitenteNombre(),
                mensaje.getTipoEmisor().name(), mensaje.getContenido(), mensaje.getFechaHora());
    }
}
