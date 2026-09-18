package com.collabcase.colaboracion.controlador;

import com.collabcase.colaboracion.dto.OperacionColaborativaRequest;
import com.collabcase.colaboracion.dto.OperacionColaborativaResponse;
import com.collabcase.colaboracion.dto.SesionColaborativaResponse;
import com.collabcase.colaboracion.servicio.OperacionColaborativaService;
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
    private final OperacionColaborativaService operacionColaborativaService;
    private final SimpMessagingTemplate messagingTemplate;

    /*
     * Sincronización integral.
     * Se mantiene temporalmente para las operaciones que todavía
     * no han sido migradas al protocolo granular.
     */
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

    /*
     * Protocolo granular de colaboración.
     */
    @MessageMapping("/sesiones/{codigo}/operaciones")
    public void aplicarOperacion(
            @DestinationVariable String codigo,
            OperacionColaborativaRequest solicitud
    ) {

        OperacionColaborativaResponse respuesta =
                operacionColaborativaService.aplicarOperacion(
                        codigo,
                        solicitud
                );

        String codigoNormalizado =
                codigo.trim().toUpperCase();

        messagingTemplate.convertAndSend(
                "/topic/sesiones/"
                        + codigoNormalizado
                        + "/operaciones",
                respuesta
        );
    }
}