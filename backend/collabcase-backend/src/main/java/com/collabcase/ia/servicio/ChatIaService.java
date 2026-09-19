package com.collabcase.ia.servicio;

import com.collabcase.colaboracion.dto.OperacionColaborativaRequest;
import com.collabcase.colaboracion.dto.OperacionColaborativaResponse;
import com.collabcase.colaboracion.dto.SesionColaborativaResponse;
import com.collabcase.colaboracion.dto.TipoOperacionColaborativa;
import com.collabcase.colaboracion.servicio.OperacionColaborativaService;
import com.collabcase.colaboracion.servicio.SesionColaborativaService;
import com.collabcase.ia.dto.ChatIaRequest;
import com.collabcase.ia.dto.ChatIaResponse;
import com.collabcase.ia.dto.OperacionIa;
import com.collabcase.ia.dto.PlanIa;
import com.collabcase.modelado.dto.AtributoDiagramaResponse;
import com.collabcase.modelado.dto.ClaseDiagramaResponse;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.dto.RelacionDiagramaResponse;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatIaService {

    private final SesionColaborativaService sesionColaborativaService;
    private final ModeloDiagramaService modeloDiagramaService;
    private final OperacionColaborativaService operacionColaborativaService;
    private final InterpretadorOpenAiService interpretadorOpenAiService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public ChatIaResponse procesar(
            String codigoSesion,
            ChatIaRequest request
    ) {
        SesionColaborativaResponse sesion =
                sesionColaborativaService.unirseSesion(
                        codigoSesion
                );

        ModeloCompletoResponse modeloActual =
                modeloDiagramaService.obtenerModeloCompleto(
                        sesion.proyectoId()
                );

        PlanIa plan =
                interpretadorOpenAiService.interpretar(
                        request.mensaje(),
                        modeloActual
                );

        List<String> acciones =
                new ArrayList<>();

        if (plan.operaciones() != null) {
            for (OperacionIa operacion : plan.operaciones()) {

                OperacionColaborativaRequest solicitud =
                        convertirOperacion(
                                operacion,
                                request.clienteId(),
                                modeloActual
                        );

                OperacionColaborativaResponse respuesta =
                        operacionColaborativaService.aplicarOperacion(
                                codigoSesion,
                                solicitud
                        );

                modeloActual =
                        respuesta.modelo();

                acciones.add(
                        describirOperacion(
                                operacion
                        )
                );

                publicarOperacion(
                        codigoSesion,
                        respuesta
                );
            }
        }

        return new ChatIaResponse(
                plan.mensaje(),
                List.copyOf(acciones),
                modeloActual
        );
    }

    private OperacionColaborativaRequest convertirOperacion(
            OperacionIa operacion,
            String clienteId,
            ModeloCompletoResponse modelo
    ) {
        String tipo =
                obligatorio(
                        operacion.tipo(),
                        "La IA no indicó el tipo de operación"
                )
                        .toUpperCase(Locale.ROOT);

        ObjectNode datos =
                objectMapper.createObjectNode();

        TipoOperacionColaborativa tipoColaborativo;

        switch (tipo) {

            case "CREAR_CLASE" -> {
                tipoColaborativo =
                        TipoOperacionColaborativa.CREAR_CLASE;

                datos.put(
                        "nombre",
                        obligatorio(
                                operacion.clase(),
                                "Falta el nombre de la clase a crear"
                        )
                );

                double[] posicion =
                        siguientePosicion(modelo);

                datos.put(
                        "posicionX",
                        posicion[0]
                );

                datos.put(
                        "posicionY",
                        posicion[1]
                );
            }

            case "RENOMBRAR_CLASE" -> {
                tipoColaborativo =
                        TipoOperacionColaborativa.RENOMBRAR_CLASE;

                ClaseDiagramaResponse clase =
                        buscarClase(
                                modelo,
                                operacion.clase()
                        );

                datos.put(
                        "claseId",
                        clase.id().toString()
                );

                datos.put(
                        "nombre",
                        obligatorio(
                                operacion.nuevoNombre(),
                                "Falta el nuevo nombre de la clase"
                        )
                );
            }

            case "ELIMINAR_CLASE" -> {
                tipoColaborativo =
                        TipoOperacionColaborativa.ELIMINAR_CLASE;

                ClaseDiagramaResponse clase =
                        buscarClase(
                                modelo,
                                operacion.clase()
                        );

                datos.put(
                        "claseId",
                        clase.id().toString()
                );
            }

            case "CREAR_ATRIBUTO" -> {
                tipoColaborativo =
                        TipoOperacionColaborativa.CREAR_ATRIBUTO;

                ClaseDiagramaResponse clase =
                        buscarClase(
                                modelo,
                                operacion.clase()
                        );

                datos.put(
                        "claseId",
                        clase.id().toString()
                );

                datos.put(
                        "nombre",
                        obligatorio(
                                operacion.atributo(),
                                "Falta el nombre del atributo"
                        )
                );

                datos.put(
                        "tipoDato",
                        obligatorio(
                                operacion.tipoDato(),
                                "Falta el tipo de dato del atributo"
                        )
                );

                datos.put(
                        "permiteNulo",
                        Boolean.TRUE.equals(
                                operacion.permiteNulo()
                        )
                );

                datos.put(
                        "identificador",
                        Boolean.TRUE.equals(
                                operacion.identificador()
                        )
                );
            }

            case "ACTUALIZAR_ATRIBUTO" -> {
                tipoColaborativo =
                        TipoOperacionColaborativa.ACTUALIZAR_ATRIBUTO;

                ClaseDiagramaResponse clase =
                        buscarClase(
                                modelo,
                                operacion.clase()
                        );

                AtributoDiagramaResponse atributo =
                        buscarAtributo(
                                clase,
                                operacion.atributo()
                        );

                datos.put(
                        "atributoId",
                        atributo.id().toString()
                );

                datos.put(
                        "claseId",
                        clase.id().toString()
                );

                datos.put(
                        "nombre",
                        textoPreferido(
                                operacion.nuevoNombreAtributo(),
                                atributo.nombre()
                        )
                );

                datos.put(
                        "tipoDato",
                        textoPreferido(
                                operacion.tipoDato(),
                                atributo.tipoDato()
                        )
                );

                datos.put(
                        "permiteNulo",
                        operacion.permiteNulo() != null
                                ? operacion.permiteNulo()
                                : Boolean.TRUE.equals(
                                        atributo.permiteNulo()
                                )
                );

                datos.put(
                        "identificador",
                        operacion.identificador() != null
                                ? operacion.identificador()
                                : Boolean.TRUE.equals(
                                        atributo.identificador()
                                )
                );
            }

            case "ELIMINAR_ATRIBUTO" -> {
                tipoColaborativo =
                        TipoOperacionColaborativa.ELIMINAR_ATRIBUTO;

                ClaseDiagramaResponse clase =
                        buscarClase(
                                modelo,
                                operacion.clase()
                        );

                AtributoDiagramaResponse atributo =
                        buscarAtributo(
                                clase,
                                operacion.atributo()
                        );

                datos.put(
                        "atributoId",
                        atributo.id().toString()
                );

                datos.put(
                        "claseId",
                        clase.id().toString()
                );
            }

            case "CREAR_RELACION" -> {
                tipoColaborativo =
                        TipoOperacionColaborativa.CREAR_RELACION;

                ClaseDiagramaResponse origen =
                        buscarClase(
                                modelo,
                                operacion.claseOrigen()
                        );

                ClaseDiagramaResponse destino =
                        buscarClase(
                                modelo,
                                operacion.claseDestino()
                        );

                datos.put(
                        "claseOrigenId",
                        origen.id().toString()
                );

                datos.put(
                        "claseDestinoId",
                        destino.id().toString()
                );

                datos.put(
                        "tipo",
                        textoPreferido(
                                operacion.tipoRelacion(),
                                "ASOCIACION"
                        )
                );

                ponerTextoNullable(
                        datos,
                        "multiplicidadOrigen",
                        operacion.multiplicidadOrigen()
                );

                ponerTextoNullable(
                        datos,
                        "multiplicidadDestino",
                        operacion.multiplicidadDestino()
                );

                ponerTextoNullable(
                        datos,
                        "nombre",
                        operacion.nombreRelacion()
                );
            }

            case "ACTUALIZAR_RELACION" -> {
                tipoColaborativo =
                        TipoOperacionColaborativa.ACTUALIZAR_RELACION;

                RelacionDiagramaResponse relacion =
                        buscarRelacion(
                                modelo,
                                operacion
                        );

                datos.put(
                        "relacionId",
                        relacion.id().toString()
                );

                datos.put(
                        "tipo",
                        textoPreferido(
                                operacion.tipoRelacion(),
                                relacion.tipo()
                        )
                );

                ponerTextoNullable(
                        datos,
                        "multiplicidadOrigen",
                        valorActualizadoNullable(
                                operacion.multiplicidadOrigen(),
                                relacion.multiplicidadOrigen()
                        )
                );

                ponerTextoNullable(
                        datos,
                        "multiplicidadDestino",
                        valorActualizadoNullable(
                                operacion.multiplicidadDestino(),
                                relacion.multiplicidadDestino()
                        )
                );

                ponerTextoNullable(
                        datos,
                        "nombre",
                        valorActualizadoNullable(
                                operacion.nuevoNombreRelacion(),
                                relacion.nombre()
                        )
                );
            }

            case "ELIMINAR_RELACION" -> {
                tipoColaborativo =
                        TipoOperacionColaborativa.ELIMINAR_RELACION;

                RelacionDiagramaResponse relacion =
                        buscarRelacion(
                                modelo,
                                operacion
                        );

                datos.put(
                        "relacionId",
                        relacion.id().toString()
                );
            }

            default ->
                    throw new IllegalArgumentException(
                            "Operación de IA no soportada: "
                                    + tipo
                    );
        }

        return new OperacionColaborativaRequest(
                UUID.randomUUID(),
                clienteId,
                tipoColaborativo,
                datos
        );
    }

    private ClaseDiagramaResponse buscarClase(
            ModeloCompletoResponse modelo,
            String nombre
    ) {
        String buscado =
                obligatorio(
                        nombre,
                        "Falta indicar la clase"
                );

        return modelo.clases()
                .stream()
                .filter(clase ->
                        clase.nombre()
                                .equalsIgnoreCase(
                                        buscado
                                )
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No existe la clase UML: "
                                        + buscado
                        )
                );
    }

    private AtributoDiagramaResponse buscarAtributo(
            ClaseDiagramaResponse clase,
            String nombre
    ) {
        String buscado =
                obligatorio(
                        nombre,
                        "Falta indicar el atributo"
                );

        return clase.atributos()
                .stream()
                .filter(atributo ->
                        atributo.nombre()
                                .equalsIgnoreCase(
                                        buscado
                                )
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No existe el atributo "
                                        + buscado
                                        + " en la clase "
                                        + clase.nombre()
                        )
                );
    }

    private RelacionDiagramaResponse buscarRelacion(
            ModeloCompletoResponse modelo,
            OperacionIa operacion
    ) {
        List<RelacionDiagramaResponse> candidatas =
                modelo.relaciones()
                        .stream()
                        .filter(relacion ->
                                coincideNombreRelacion(
                                        relacion,
                                        operacion.nombreRelacion()
                                )
                        )
                        .filter(relacion ->
                                coincideExtremos(
                                        modelo,
                                        relacion,
                                        operacion.claseOrigen(),
                                        operacion.claseDestino()
                                )
                        )
                        .toList();

        if (candidatas.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se encontró la relación UML indicada"
            );
        }

        if (candidatas.size() > 1) {
            throw new IllegalArgumentException(
                    "La relación indicada es ambigua; especifica su nombre y clases"
            );
        }

        return candidatas.get(0);
    }

    private boolean coincideNombreRelacion(
            RelacionDiagramaResponse relacion,
            String nombre
    ) {
        if (nombre == null || nombre.isBlank()) {
            return true;
        }

        return relacion.nombre() != null
                && relacion.nombre()
                .equalsIgnoreCase(
                        nombre.trim()
                );
    }

    private boolean coincideExtremos(
            ModeloCompletoResponse modelo,
            RelacionDiagramaResponse relacion,
            String origen,
            String destino
    ) {
        if ((origen == null || origen.isBlank())
                && (destino == null || destino.isBlank())) {
            return true;
        }

        ClaseDiagramaResponse claseOrigen =
                buscarClasePorId(
                        modelo,
                        relacion.claseOrigenId()
                );

        ClaseDiagramaResponse claseDestino =
                buscarClasePorId(
                        modelo,
                        relacion.claseDestinoId()
                );

        if (claseOrigen == null || claseDestino == null) {
            return false;
        }

        boolean coincideOrigen =
                origen == null
                        || origen.isBlank()
                        || claseOrigen.nombre()
                        .equalsIgnoreCase(
                                origen.trim()
                        );

        boolean coincideDestino =
                destino == null
                        || destino.isBlank()
                        || claseDestino.nombre()
                        .equalsIgnoreCase(
                                destino.trim()
                        );

        return coincideOrigen && coincideDestino;
    }

    private ClaseDiagramaResponse buscarClasePorId(
            ModeloCompletoResponse modelo,
            UUID claseId
    ) {
        return modelo.clases()
                .stream()
                .filter(clase ->
                        clase.id().equals(
                                claseId
                        )
                )
                .findFirst()
                .orElse(null);
    }

    private double[] siguientePosicion(
            ModeloCompletoResponse modelo
    ) {
        int indice =
                modelo.clases().size();

        int columna =
                indice % 4;

        int fila =
                indice / 4;

        return new double[]{
                80.0 + columna * 280.0,
                80.0 + fila * 220.0
        };
    }

    private void publicarOperacion(
            String codigoSesion,
            OperacionColaborativaResponse respuesta
    ) {
        String codigoNormalizado =
                codigoSesion
                        .trim()
                        .toUpperCase(Locale.ROOT);

        messagingTemplate.convertAndSend(
                "/topic/sesiones/"
                        + codigoNormalizado
                        + "/operaciones",
                respuesta
        );
    }

    private void ponerTextoNullable(
            ObjectNode datos,
            String campo,
            String valor
    ) {
        if (valor == null || valor.isBlank()) {
            datos.putNull(campo);
            return;
        }

        datos.put(
                campo,
                valor.trim()
        );
    }

    private String obligatorio(
            String valor,
            String mensaje
    ) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    mensaje
            );
        }

        return valor.trim();
    }

    private String textoPreferido(
            String candidato,
            String respaldo
    ) {
        if (candidato != null && !candidato.isBlank()) {
            return candidato.trim();
        }

        return respaldo;
    }

    private String valorActualizadoNullable(
            String candidato,
            String respaldo
    ) {
        if (candidato != null) {
            return candidato.isBlank()
                    ? null
                    : candidato.trim();
        }

        return respaldo;
    }

    private String describirOperacion(
            OperacionIa operacion
    ) {
        return switch (operacion.tipo()) {
            case "CREAR_CLASE" ->
                    "Clase creada: " + operacion.clase();

            case "RENOMBRAR_CLASE" ->
                    "Clase renombrada: "
                            + operacion.clase()
                            + " → "
                            + operacion.nuevoNombre();

            case "ELIMINAR_CLASE" ->
                    "Clase eliminada: " + operacion.clase();

            case "CREAR_ATRIBUTO" ->
                    "Atributo creado: "
                            + operacion.clase()
                            + "."
                            + operacion.atributo();

            case "ACTUALIZAR_ATRIBUTO" ->
                    "Atributo actualizado: "
                            + operacion.clase()
                            + "."
                            + operacion.atributo();

            case "ELIMINAR_ATRIBUTO" ->
                    "Atributo eliminado: "
                            + operacion.clase()
                            + "."
                            + operacion.atributo();

            case "CREAR_RELACION" ->
                    "Relación creada: "
                            + operacion.claseOrigen()
                            + " → "
                            + operacion.claseDestino();

            case "ACTUALIZAR_RELACION" ->
                    "Relación actualizada";

            case "ELIMINAR_RELACION" ->
                    "Relación eliminada";

            default ->
                    operacion.tipo();
        };
    }
}
