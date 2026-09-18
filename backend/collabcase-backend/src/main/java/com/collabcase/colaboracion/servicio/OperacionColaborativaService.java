package com.collabcase.colaboracion.servicio;

import com.collabcase.colaboracion.dto.OperacionColaborativaRequest;
import com.collabcase.colaboracion.dto.OperacionColaborativaResponse;
import com.collabcase.colaboracion.dto.SesionColaborativaResponse;
import com.collabcase.colaboracion.dto.TipoOperacionColaborativa;
import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.repositorio.ClaseDiagramaRepository;
import com.collabcase.modelado.repositorio.ModeloDiagramaRepository;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperacionColaborativaService {

    private final SesionColaborativaService sesionColaborativaService;
    private final GestorBloqueosColaborativos gestorBloqueosColaborativos;

    private final ClaseDiagramaRepository claseDiagramaRepository;
    private final ModeloDiagramaRepository modeloDiagramaRepository;
    private final ModeloDiagramaService modeloDiagramaService;

    @Transactional
    public OperacionColaborativaResponse aplicarOperacion(
            String codigoSesion,
            OperacionColaborativaRequest solicitud
    ) {

        SesionColaborativaResponse sesion =
                sesionColaborativaService.unirseSesion(codigoSesion);

        if (solicitud.tipo() == TipoOperacionColaborativa.MOVER_CLASE) {
            return moverClase(sesion, solicitud);
        }

        throw new IllegalStateException(
                "La operación colaborativa todavía no está implementada: "
                        + solicitud.tipo()
        );
    }

    private OperacionColaborativaResponse moverClase(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        UUID claseId = UUID.fromString(
                datos.get("claseId").asText()
        );

        double posicionX = datos.get("posicionX").asDouble();
        double posicionY = datos.get("posicionY").asDouble();

        String claveBloqueo =
                "CLASE:" + claseId + ":POSICION";

        return gestorBloqueosColaborativos.ejecutarConBloqueo(
                claveBloqueo,
                () -> {

                    ClaseDiagrama clase = claseDiagramaRepository
                            .findById(claseId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Clase UML no encontrada"
                                    )
                            );

                    ModeloDiagrama modelo = clase.getModelo();

                    if (!modelo.getProyecto()
                            .getId()
                            .equals(sesion.proyectoId())) {

                        throw new IllegalStateException(
                                "La clase no pertenece al proyecto de la sesión"
                        );
                    }

                    clase.setPosicionX(posicionX);
                    clase.setPosicionY(posicionY);

                    claseDiagramaRepository.save(clase);

                    modelo.setVersion(modelo.getVersion() + 1);

                    modeloDiagramaRepository.saveAndFlush(modelo);

                    ModeloCompletoResponse modeloActualizado =
                            modeloDiagramaService.obtenerModeloCompleto(
                                    sesion.proyectoId()
                            );

                    return new OperacionColaborativaResponse(
                            solicitud.operacionId(),
                            solicitud.clienteId(),
                            solicitud.tipo(),
                            modeloActualizado
                    );
                }
        );
    }
}
