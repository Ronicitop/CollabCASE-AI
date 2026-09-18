package com.collabcase.colaboracion.servicio;

import com.collabcase.colaboracion.dto.OperacionColaborativaRequest;
import com.collabcase.colaboracion.dto.OperacionColaborativaResponse;
import com.collabcase.colaboracion.dto.SesionColaborativaResponse;
import com.collabcase.modelado.dominio.AtributoDiagrama;
import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.dominio.RelacionDiagrama;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.repositorio.AtributoDiagramaRepository;
import com.collabcase.modelado.repositorio.ClaseDiagramaRepository;
import com.collabcase.modelado.repositorio.ModeloDiagramaRepository;
import com.collabcase.modelado.repositorio.RelacionDiagramaRepository;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperacionColaborativaService {

    private final SesionColaborativaService sesionColaborativaService;
    private final GestorBloqueosColaborativos gestorBloqueosColaborativos;

    private final ClaseDiagramaRepository claseDiagramaRepository;
    private final AtributoDiagramaRepository atributoDiagramaRepository;
    private final RelacionDiagramaRepository relacionDiagramaRepository;
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

            case ELIMINAR_CLASE ->
                    eliminarClase(sesion, solicitud);

            case CREAR_ATRIBUTO ->
                    crearAtributo(sesion, solicitud);

            case ACTUALIZAR_ATRIBUTO ->
                    actualizarAtributo(sesion, solicitud);

            case ELIMINAR_ATRIBUTO ->
                    eliminarAtributo(sesion, solicitud);

            case CREAR_RELACION ->
                    crearRelacion(sesion, solicitud);

            case ACTUALIZAR_RELACION ->
                    actualizarRelacion(sesion, solicitud);

            case ELIMINAR_RELACION ->
                    eliminarRelacion(sesion, solicitud);

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
                datos.get("claseId").asString()
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

                                ClaseDiagrama clase = obtenerClaseDeSesion(
                                        claseId,
                                        sesion
                                );

                                ModeloDiagrama modelo = clase.getModelo();

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

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
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

        String nombre = datos.get("nombre").asString().trim();
        double posicionX = datos.get("posicionX").asDouble();
        double posicionY = datos.get("posicionY").asDouble();

        validarNombreClase(nombre);

        ModeloDiagrama modelo = obtenerModeloDeSesion(sesion);

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

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
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
                datos.get("claseId").asString()
        );

        String nombre = datos.get("nombre").asString().trim();

        validarNombreClase(nombre);

        String claveBloqueo =
                "CLASE:" + claseId + ":NOMBRE";

        return gestorBloqueosColaborativos.ejecutarConBloqueo(
                claveBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                ClaseDiagrama clase = obtenerClaseDeSesion(
                                        claseId,
                                        sesion
                                );

                                ModeloDiagrama modelo = clase.getModelo();

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

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private OperacionColaborativaResponse eliminarClase(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        UUID claseId = UUID.fromString(
                datos.get("claseId").asString()
        );

        ModeloDiagrama modeloSesion =
                obtenerModeloDeSesion(sesion);

        List<String> clavesBloqueo = List.of(
                "CLASE:" + claseId + ":ESTRUCTURA",
                "CLASE:" + claseId + ":NOMBRE",
                "CLASE:" + claseId + ":POSICION",
                "MODELO:" + modeloSesion.getId() + ":RELACIONES"
        );

        return gestorBloqueosColaborativos.ejecutarConBloqueos(
                clavesBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                ClaseDiagrama clase = obtenerClaseDeSesion(
                                        claseId,
                                        sesion
                                );

                                ModeloDiagrama modelo = clase.getModelo();

                                relacionDiagramaRepository.eliminarPorClaseId(
                                        claseId
                                );

                                atributoDiagramaRepository.eliminarPorClaseId(
                                        claseId
                                );

                                int filasClase =
                                        claseDiagramaRepository.eliminarPorIdYModeloId(
                                                claseId,
                                                modelo.getId()
                                        );

                                if (filasClase != 1) {
                                    throw new IllegalStateException(
                                            "No se pudo eliminar la clase UML"
                                    );
                                }

                                incrementarVersionModelo(modelo.getId());

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private OperacionColaborativaResponse crearAtributo(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        UUID claseId = UUID.fromString(
                datos.get("claseId").asString()
        );

        String nombre = datos.get("nombre").asString().trim();
        String tipoDato = datos.get("tipoDato").asString().trim();
        boolean permiteNulo = datos.get("permiteNulo").asBoolean();
        boolean identificador = datos.get("identificador").asBoolean();

        validarAtributo(nombre, tipoDato);

        List<String> clavesBloqueo = List.of(
                "CLASE:" + claseId + ":ESTRUCTURA",
                "CLASE:"
                        + claseId
                        + ":NOMBRE_ATRIBUTO:"
                        + nombre.toLowerCase(Locale.ROOT)
        );

        return gestorBloqueosColaborativos.ejecutarConBloqueos(
                clavesBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                ClaseDiagrama clase = obtenerClaseDeSesion(
                                        claseId,
                                        sesion
                                );

                                boolean nombreDuplicado =
                                        atributoDiagramaRepository
                                                .existsByClaseIdAndNombreIgnoreCase(
                                                        claseId,
                                                        nombre
                                                );

                                if (nombreDuplicado) {
                                    throw new IllegalStateException(
                                            "Ya existe un atributo con ese nombre en la clase"
                                    );
                                }

                                AtributoDiagrama nuevoAtributo =
                                        AtributoDiagrama.builder()
                                                .clase(clase)
                                                .nombre(nombre)
                                                .tipoDato(tipoDato)
                                                .permiteNulo(permiteNulo)
                                                .identificador(identificador)
                                                .build();

                                atributoDiagramaRepository.saveAndFlush(
                                        nuevoAtributo
                                );

                                incrementarVersionModelo(
                                        clase.getModelo().getId()
                                );

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private OperacionColaborativaResponse actualizarAtributo(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        UUID atributoId = UUID.fromString(
                datos.get("atributoId").asString()
        );

        UUID claseId = UUID.fromString(
                datos.get("claseId").asString()
        );

        String nombre = datos.get("nombre").asString().trim();
        String tipoDato = datos.get("tipoDato").asString().trim();
        boolean permiteNulo = datos.get("permiteNulo").asBoolean();
        boolean identificador = datos.get("identificador").asBoolean();

        validarAtributo(nombre, tipoDato);

        List<String> clavesBloqueo = List.of(
                "ATRIBUTO:" + atributoId,
                "CLASE:" + claseId + ":ESTRUCTURA",
                "CLASE:"
                        + claseId
                        + ":NOMBRE_ATRIBUTO:"
                        + nombre.toLowerCase(Locale.ROOT)
        );

        return gestorBloqueosColaborativos.ejecutarConBloqueos(
                clavesBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                ClaseDiagrama clase = obtenerClaseDeSesion(
                                        claseId,
                                        sesion
                                );

                                AtributoDiagrama atributo =
                                        atributoDiagramaRepository
                                                .findById(atributoId)
                                                .orElseThrow(() ->
                                                        new IllegalArgumentException(
                                                                "Atributo UML no encontrado"
                                                        )
                                                );

                                if (!atributo.getClase()
                                        .getId()
                                        .equals(claseId)) {

                                    throw new IllegalStateException(
                                            "El atributo no pertenece a la clase indicada"
                                    );
                                }

                                boolean nombreDuplicado =
                                        atributoDiagramaRepository
                                                .existsByClaseIdAndNombreIgnoreCaseAndIdNot(
                                                        claseId,
                                                        nombre,
                                                        atributoId
                                                );

                                if (nombreDuplicado) {
                                    throw new IllegalStateException(
                                            "Ya existe un atributo con ese nombre en la clase"
                                    );
                                }

                                int filasAtributo =
                                        atributoDiagramaRepository.actualizarAtributo(
                                                atributoId,
                                                claseId,
                                                nombre,
                                                tipoDato,
                                                permiteNulo,
                                                identificador
                                        );

                                if (filasAtributo != 1) {
                                    throw new IllegalStateException(
                                            "No se pudo actualizar el atributo UML"
                                    );
                                }

                                incrementarVersionModelo(
                                        clase.getModelo().getId()
                                );

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private OperacionColaborativaResponse eliminarAtributo(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        UUID atributoId = UUID.fromString(
                datos.get("atributoId").asString()
        );

        UUID claseId = UUID.fromString(
                datos.get("claseId").asString()
        );

        List<String> clavesBloqueo = List.of(
                "ATRIBUTO:" + atributoId,
                "CLASE:" + claseId + ":ESTRUCTURA"
        );

        return gestorBloqueosColaborativos.ejecutarConBloqueos(
                clavesBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                ClaseDiagrama clase = obtenerClaseDeSesion(
                                        claseId,
                                        sesion
                                );

                                AtributoDiagrama atributo =
                                        atributoDiagramaRepository
                                                .findById(atributoId)
                                                .orElseThrow(() ->
                                                        new IllegalArgumentException(
                                                                "Atributo UML no encontrado"
                                                        )
                                                );

                                if (!atributo.getClase()
                                        .getId()
                                        .equals(claseId)) {

                                    throw new IllegalStateException(
                                            "El atributo no pertenece a la clase indicada"
                                    );
                                }

                                int filasAtributo =
                                        atributoDiagramaRepository.eliminarPorIdYClaseId(
                                                atributoId,
                                                claseId
                                        );

                                if (filasAtributo != 1) {
                                    throw new IllegalStateException(
                                            "No se pudo eliminar el atributo UML"
                                    );
                                }

                                incrementarVersionModelo(
                                        clase.getModelo().getId()
                                );

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private OperacionColaborativaResponse crearRelacion(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        UUID claseOrigenId = UUID.fromString(
                datos.get("claseOrigenId").asString()
        );

        UUID claseDestinoId = UUID.fromString(
                datos.get("claseDestinoId").asString()
        );

        String tipo = datos.get("tipo").asString().trim();
        String multiplicidadOrigen =
                obtenerTextoOpcional(datos, "multiplicidadOrigen");
        String multiplicidadDestino =
                obtenerTextoOpcional(datos, "multiplicidadDestino");
        String nombre =
                obtenerTextoOpcional(datos, "nombre");

        validarRelacion(
                tipo,
                multiplicidadOrigen,
                multiplicidadDestino,
                nombre
        );

        ModeloDiagrama modelo = obtenerModeloDeSesion(sesion);

        List<String> clavesBloqueo = List.of(
                "CLASE:" + claseOrigenId + ":ESTRUCTURA",
                "CLASE:" + claseDestinoId + ":ESTRUCTURA",
                "MODELO:" + modelo.getId() + ":RELACIONES"
        );

        return gestorBloqueosColaborativos.ejecutarConBloqueos(
                clavesBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                ClaseDiagrama claseOrigen =
                                        obtenerClaseDeSesion(
                                                claseOrigenId,
                                                sesion
                                        );

                                ClaseDiagrama claseDestino =
                                        obtenerClaseDeSesion(
                                                claseDestinoId,
                                                sesion
                                        );

                                if (!claseOrigen.getModelo()
                                        .getId()
                                        .equals(modelo.getId())
                                        || !claseDestino.getModelo()
                                        .getId()
                                        .equals(modelo.getId())) {

                                    throw new IllegalStateException(
                                            "Las clases de la relación no pertenecen al modelo de la sesión"
                                    );
                                }

                                RelacionDiagrama nuevaRelacion =
                                        RelacionDiagrama.builder()
                                                .modelo(modelo)
                                                .claseOrigen(claseOrigen)
                                                .claseDestino(claseDestino)
                                                .tipo(tipo)
                                                .multiplicidadOrigen(
                                                        multiplicidadOrigen
                                                )
                                                .multiplicidadDestino(
                                                        multiplicidadDestino
                                                )
                                                .nombre(nombre)
                                                .build();

                                relacionDiagramaRepository.saveAndFlush(
                                        nuevaRelacion
                                );

                                incrementarVersionModelo(
                                        modelo.getId()
                                );

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private OperacionColaborativaResponse actualizarRelacion(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        UUID relacionId = UUID.fromString(
                datos.get("relacionId").asString()
        );

        String tipo = datos.get("tipo").asString().trim();
        String multiplicidadOrigen =
                obtenerTextoOpcional(datos, "multiplicidadOrigen");
        String multiplicidadDestino =
                obtenerTextoOpcional(datos, "multiplicidadDestino");
        String nombre =
                obtenerTextoOpcional(datos, "nombre");

        validarRelacion(
                tipo,
                multiplicidadOrigen,
                multiplicidadDestino,
                nombre
        );

        ModeloDiagrama modelo = obtenerModeloDeSesion(sesion);

        List<String> clavesBloqueo = List.of(
                "MODELO:" + modelo.getId() + ":RELACIONES",
                "RELACION:" + relacionId
        );

        return gestorBloqueosColaborativos.ejecutarConBloqueos(
                clavesBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                RelacionDiagrama relacion =
                                        obtenerRelacionDeSesion(
                                                relacionId,
                                                modelo.getId(),
                                                sesion
                                        );

                                int filasRelacion =
                                        relacionDiagramaRepository
                                                .actualizarRelacion(
                                                        relacion.getId(),
                                                        modelo.getId(),
                                                        tipo,
                                                        multiplicidadOrigen,
                                                        multiplicidadDestino,
                                                        nombre
                                                );

                                if (filasRelacion != 1) {
                                    throw new IllegalStateException(
                                            "No se pudo actualizar la relación UML"
                                    );
                                }

                                incrementarVersionModelo(
                                        modelo.getId()
                                );

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private OperacionColaborativaResponse eliminarRelacion(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

        JsonNode datos = solicitud.datos();

        UUID relacionId = UUID.fromString(
                datos.get("relacionId").asString()
        );

        ModeloDiagrama modelo = obtenerModeloDeSesion(sesion);

        List<String> clavesBloqueo = List.of(
                "MODELO:" + modelo.getId() + ":RELACIONES",
                "RELACION:" + relacionId
        );

        return gestorBloqueosColaborativos.ejecutarConBloqueos(
                clavesBloqueo,
                () -> {
                    OperacionColaborativaResponse respuesta =
                            transactionTemplate.execute(status -> {

                                obtenerRelacionDeSesion(
                                        relacionId,
                                        modelo.getId(),
                                        sesion
                                );

                                int filasRelacion =
                                        relacionDiagramaRepository
                                                .eliminarPorIdYModeloId(
                                                        relacionId,
                                                        modelo.getId()
                                                );

                                if (filasRelacion != 1) {
                                    throw new IllegalStateException(
                                            "No se pudo eliminar la relación UML"
                                    );
                                }

                                incrementarVersionModelo(
                                        modelo.getId()
                                );

                                return construirRespuestaActualizada(
                                        sesion,
                                        solicitud
                                );
                            });

                    return validarRespuestaTransaccion(respuesta);
                }
        );
    }

    private RelacionDiagrama obtenerRelacionDeSesion(
            UUID relacionId,
            UUID modeloId,
            SesionColaborativaResponse sesion
    ) {

        RelacionDiagrama relacion =
                relacionDiagramaRepository
                        .findById(relacionId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Relación UML no encontrada"
                                )
                        );

        validarProyectoSesion(
                relacion.getModelo(),
                sesion
        );

        if (!relacion.getModelo()
                .getId()
                .equals(modeloId)) {

            throw new IllegalStateException(
                    "La relación no pertenece al modelo de la sesión"
            );
        }

        return relacion;
    }

    private String obtenerTextoOpcional(
            JsonNode datos,
            String campo
    ) {

        JsonNode valor = datos.get(campo);

        if (valor == null || valor.isNull()) {
            return null;
        }

        String texto = valor.asString().trim();

        return texto.isBlank()
                ? null
                : texto;
    }

    private void validarRelacion(
            String tipo,
            String multiplicidadOrigen,
            String multiplicidadDestino,
            String nombre
    ) {

        if (tipo.isBlank()) {
            throw new IllegalStateException(
                    "El tipo de relación es obligatorio"
            );
        }

        if (tipo.length() > 30) {
            throw new IllegalStateException(
                    "El tipo de relación no puede superar 30 caracteres"
            );
        }

        if (multiplicidadOrigen != null
                && multiplicidadOrigen.length() > 20) {

            throw new IllegalStateException(
                    "La multiplicidad de origen no puede superar 20 caracteres"
            );
        }

        if (multiplicidadDestino != null
                && multiplicidadDestino.length() > 20) {

            throw new IllegalStateException(
                    "La multiplicidad de destino no puede superar 20 caracteres"
            );
        }

        if (nombre != null && nombre.length() > 100) {
            throw new IllegalStateException(
                    "El nombre de la relación no puede superar 100 caracteres"
            );
        }
    }

    private ModeloDiagrama obtenerModeloDeSesion(
            SesionColaborativaResponse sesion
    ) {

        return modeloDiagramaRepository
                .findByProyectoId(sesion.proyectoId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Modelo UML no encontrado"
                        )
                );
    }

    private ClaseDiagrama obtenerClaseDeSesion(
            UUID claseId,
            SesionColaborativaResponse sesion
    ) {

        ClaseDiagrama clase = claseDiagramaRepository
                .findById(claseId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Clase UML no encontrada"
                        )
                );

        validarProyectoSesion(
                clase.getModelo(),
                sesion
        );

        return clase;
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

    private void validarAtributo(
            String nombre,
            String tipoDato
    ) {

        if (nombre.isBlank()) {
            throw new IllegalStateException(
                    "El nombre del atributo es obligatorio"
            );
        }

        if (nombre.length() > 100) {
            throw new IllegalStateException(
                    "El nombre del atributo no puede superar 100 caracteres"
            );
        }

        if (tipoDato.isBlank()) {
            throw new IllegalStateException(
                    "El tipo de dato del atributo es obligatorio"
            );
        }

        if (tipoDato.length() > 50) {
            throw new IllegalStateException(
                    "El tipo de dato no puede superar 50 caracteres"
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
                    "El elemento no pertenece al proyecto de la sesión"
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

    private OperacionColaborativaResponse construirRespuestaActualizada(
            SesionColaborativaResponse sesion,
            OperacionColaborativaRequest solicitud
    ) {

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
