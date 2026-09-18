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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperacionColaborativaService {

    private final SesionColaborativaService sesionColaborativaService;
    private final GestorBloqueosColaborativos gestorBloqueosColaborativos;

    private final ClaseDiagramaRepository claseDiagramaRepository;
    private final ModeloDiagramaRepository modeloDiagramaRepository;
    private final ModeloDiagramaService modeloDiagramaService;

    private final TransactionTemplate transactionTemplate;

    public OperacionColaborativaResponse aplicarOperacion(
            String codigoSesion,
            OperacionColaborativaRequest solicitud
    ) {

        SesionColaborativaResponse sesion =
                sesionColaborativaService.unirseSesion(codigoSesion);

        return switch (solicitud.tipo()) {
            case MOVER_CLASE ->
                    moverClase(sesion, solicitud);

            case CREAR_CLASE ->
                    crearClase(sesion, solicitud);

            case RENOMBRAR_CLASE ->
                    renombrarClase(sesion, solicitud);

            default ->
                    throw new IllegalStateException(
                            "La operación colaborativa todavía no está implementada: "
                                    + solicitud.tipo()
                    );
        };
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
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                ClaseDiagrama clase = claseDiagramaRepository
                                        .findById(claseId)
                                        .orElseThrow(() ->
                                                new IllegalArgumentException(
                                                        "Clase UML no encontrada"
                                                )
                                        );

                                ModeloDiagrama modelo = clase.getModelo();

                                validarProyectoSesion(
                                        modelo,
                                        sesion
                                );

                                int filasClase =
                                        claseDiagramaRepository.actualizarPosicion(
                                                claseId,
                                                modelo.getId(),
                                                posicionX,
                                                posicionY
                                        );

                                if (filasClase != 1) {
                                    throw new IllegalStateException(
                                            "No se pudo actualizar la posición de la clase UML"
                                    );
                                }

                                incrementarVersionModelo(modelo.getId());

                                ModeloCompletoResponse modeloActualizado =
                                        modeloDiagramaService.obtenerModeloCompleto(
                                                sesion.proyectoId()
                                        );

                                return construirRespuesta(
                                        solicitud,
                                        modeloActualizado
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private OperacionColaborativaResponse crearClase(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        String nombre = datos.get("nombre").asText().trim();
        double posicionX = datos.get("posicionX").asDouble();
        double posicionY = datos.get("posicionY").asDouble();

        validarNombreClase(nombre);

        ModeloDiagrama modelo = modeloDiagramaRepository
                .findByProyectoId(sesion.proyectoId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Modelo UML no encontrado"
                        )
                );

        String nombreNormalizado =
                nombre.toLowerCase(Locale.ROOT);

        String claveBloqueo =
                "MODELO:"
                        + modelo.getId()
                        + ":NOMBRE_CLASE:"
                        + nombreNormalizado;

        return gestorBloqueosColaborativos.ejecutarConBloqueo(
                claveBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                boolean nombreDuplicado =
                                        claseDiagramaRepository
                                                .existsByModeloIdAndNombreIgnoreCase(
                                                        modelo.getId(),
                                                        nombre
                                                );

                                if (nombreDuplicado) {
                                    throw new IllegalStateException(
                                            "Ya existe una clase con ese nombre en el modelo"
                                    );
                                }

                                ClaseDiagrama nuevaClase =
                                        ClaseDiagrama.builder()
                                                .modelo(modelo)
                                                .nombre(nombre)
                                                .posicionX(posicionX)
                                                .posicionY(posicionY)
                                                .build();

                                claseDiagramaRepository.saveAndFlush(
                                        nuevaClase
                                );

                                incrementarVersionModelo(modelo.getId());

                                ModeloCompletoResponse modeloActualizado =
                                        modeloDiagramaService.obtenerModeloCompleto(
                                                sesion.proyectoId()
                                        );

                                return construirRespuesta(
                                        solicitud,
                                        modeloActualizado
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private OperacionColaborativaResponse renombrarClase(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        UUID claseId = UUID.fromString(
                datos.get("claseId").asText()
        );

        String nombre = datos.get("nombre").asText().trim();

        validarNombreClase(nombre);

        String claveBloqueo =
                "CLASE:" + claseId + ":NOMBRE";

        return gestorBloqueosColaborativos.ejecutarConBloqueo(
                claveBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                ClaseDiagrama clase = claseDiagramaRepository
                                        .findById(claseId)
                                        .orElseThrow(() ->
                                                new IllegalArgumentException(
                                                        "Clase UML no encontrada"
                                                )
                                        );

                                ModeloDiagrama modelo = clase.getModelo();

                                validarProyectoSesion(
                                        modelo,
                                        sesion
                                );

                                boolean nombreDuplicado =
                                        claseDiagramaRepository
                                                .existsByModeloIdAndNombreIgnoreCaseAndIdNot(
                                                        modelo.getId(),
                                                        nombre,
                                                        claseId
                                                );

                                if (nombreDuplicado) {
                                    throw new IllegalStateException(
                                            "Ya existe una clase con ese nombre en el modelo"
                                    );
                                }

                                int filasClase =
                                        claseDiagramaRepository.actualizarNombre(
                                                claseId,
                                                modelo.getId(),
                                                nombre
                                        );

                                if (filasClase != 1) {
                                    throw new IllegalStateException(
                                            "No se pudo actualizar el nombre de la clase UML"
                                    );
                                }

                                incrementarVersionModelo(modelo.getId());

                                ModeloCompletoResponse modeloActualizado =
                                        modeloDiagramaService.obtenerModeloCompleto(
                                                sesion.proyectoId()
                                        );

                                return construirRespuesta(
                                        solicitud,
                                        modeloActualizado
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private void validarNombreClase(String nombre) {

        if (nombre.isBlank()) {
            throw new IllegalStateException(
                    "El nombre de la clase es obligatorio"
            );
        }

        if (nombre.length() > 100) {
            throw new IllegalStateException(
                    "El nombre de la clase no puede superar 100 caracteres"
            );
        }
    }

    private void validarProyectoSesion(
            ModeloDiagrama modelo,
            SesionColaborativaResponse sesion
    ) {

        if (!modelo.getProyecto()
                .getId()
                .equals(sesion.proyectoId())) {

            throw new IllegalStateException(
                    "La clase no pertenece al proyecto de la sesión"
            );
        }
    }

    private void incrementarVersionModelo(UUID modeloId) {

        int filasModelo =
                modeloDiagramaRepository.incrementarVersion(
                        modeloId
                );

        if (filasModelo != 1) {
            throw new IllegalStateException(
                    "No se pudo actualizar la versión del modelo UML"
            );
        }
    }

    private OperacionColaborativaResponse construirRespuesta(
            OperacionColaborativaRequest solicitud,
            ModeloCompletoResponse modeloActualizado
    ) {

        return new OperacionColaborativaResponse(
                solicitud.operacionId(),
                solicitud.clienteId(),
                solicitud.tipo(),
                modeloActualizado
        );
    }

    private OperacionColaborativaResponse validarRespuestaTransaccion(
            OperacionColaborativaResponse respuesta
    ) {

        if (respuesta == null) {
            throw new IllegalStateException(
                    "No se pudo completar la operación colaborativa"
            );
        }

        return respuesta;
    }
}
