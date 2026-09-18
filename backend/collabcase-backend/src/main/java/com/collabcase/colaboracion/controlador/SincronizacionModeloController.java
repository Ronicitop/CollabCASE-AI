package com.collabcase.colaboracion.controlador;

import com.collabcase.colaboracion.dto.SesionColaborativaResponse;
import com.collabcase.colaboracion.servicio.SesionColaborativaService;
import com.collabcase.modelado.dto.ModeloCompletoRequest;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class SincronizacionModeloController {

    private final SesionColaborativaService sesionColaborativaService;
    private final ModeloDiagramaService modeloDiagramaService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/sesiones/{codigo}/modelo")
    public void sincronizarModelo(
            @DestinationVariable String codigo,
            ModeloCompletoRequest solicitud
    ) {

        SesionColaborativaResponse sesion =
                sesionColaborativaService.unirseSesion(codigo);

        ModeloCompletoResponse modeloActualizado =
                modeloDiagramaService.guardarModeloCompleto(
                        sesion.proyectoId(),
                        solicitud
                );

        messagingTemplate.convertAndSend(
                "/topic/sesiones/" + sesion.codigo() + "/modelo",
                modeloActualizado
        );
    }
}
