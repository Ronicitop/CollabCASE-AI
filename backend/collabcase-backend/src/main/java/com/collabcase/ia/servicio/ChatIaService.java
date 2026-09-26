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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatIaService {

    private static final Logger log = LoggerFactory.getLogger(ChatIaService.class);

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
                sesionColaborativaService.unirseSesion(codigoSesion);

        ModeloCompletoResponse modeloActual =
                modeloDiagramaService.obtenerModeloCompleto(sesion.proyectoId());

        log.info(
                "[IA-TRACE] TEXTO recibido. sesion={}, mensaje={}",
                codigoSesion,
                request.mensaje()
        );
        log.info(
                "[IA-TRACE] MODELO ANTES: {}",
                resumirModelo(modeloActual)
        );

        PlanIa plan =
                interpretadorOpenAiService.interpretar(
                        request.mensaje(),
                        modeloActual
                );

        log.info(
                "[IA-TRACE] PLAN ORIGINAL TEXTO: {}",
                aJsonSeguro(plan)
        );

        plan = normalizarPlanComun(
                plan,
                modeloActual
        );

        log.info(
                "[IA-TRACE] PLAN NORMALIZADO TEXTO: {}",
                aJsonSeguro(plan)
        );

        return ejecutarPlan(
                codigoSesion,
                request.clienteId(),
                plan,
                modeloActual
        );
    }

    public ChatIaResponse procesarImagen(
            String codigoSesion,
            String clienteId,
            String mensaje,
            MultipartFile archivo
    ) {
        SesionColaborativaResponse sesion =
                sesionColaborativaService.unirseSesion(codigoSesion);

        ModeloCompletoResponse modeloActual =
                modeloDiagramaService.obtenerModeloCompleto(sesion.proyectoId());

        log.info(
                "[IA-TRACE] IMAGEN recibida. sesion={}, archivo={}, mensaje={}",
                codigoSesion,
                archivo != null ? archivo.getOriginalFilename() : null,
                mensaje
        );
        log.info(
                "[IA-TRACE] MODELO ANTES IMAGEN: {}",
                resumirModelo(modeloActual)
        );

        PlanIa plan =
                interpretadorOpenAiService.interpretarImagen(
                        archivo,
                        mensaje,
                        modeloActual
                );

        log.info(
                "[IA-TRACE] PLAN ORIGINAL IMAGEN: {}",
                aJsonSeguro(plan)
        );

        plan = normalizarPlanImagen(
                plan,
                modeloActual
        );

        log.info(
                "[IA-TRACE] PLAN NORMALIZADO IMAGEN: {}",
                aJsonSeguro(plan)
        );

        return ejecutarPlan(
                codigoSesion,
                clienteId,
                plan,
                modeloActual
        );
    }


    private PlanIa normalizarPlanImagen(
            PlanIa plan,
            ModeloCompletoResponse modeloActual
    ) {
        if (plan == null || plan.operaciones() == null
                || plan.operaciones().isEmpty()) {
            return plan;
        }

        List<OperacionIa> operaciones =
                normalizarTiposOperacionRelacion(
                        plan.operaciones()
                );

        operaciones =
                normalizarTiposAtributosImagen(
                        operaciones
                );

        operaciones =
                normalizarClasesAsociacionImagen(
                        operaciones,
                        modeloActual
                );

        operaciones =
                normalizarClaseAsociacionSobreRelacionExistente(
                        operaciones,
                        modeloActual
                );

        operaciones =
                eliminarCreacionesRedundantesParaClaseAsociacion(
                        operaciones
                );

        operaciones =
                protegerRelacionBaseClaseAsociacion(
                        operaciones,
                        modeloActual
                );

        operaciones =
                eliminarRelacionesAuxiliaresClaseAsociacionDelPlan(
                        operaciones
                );

        return new PlanIa(
                plan.mensaje(),
                List.copyOf(operaciones)
        );
    }

    /*
     * Normalización defensiva usada también por comandos de texto.
     *
     * La IA debe devolver en "tipo" una ACCIÓN (CREAR_RELACION,
     * ACTUALIZAR_RELACION, etc.) y en "tipoRelacion" el tipo UML
     * (ASOCIACION, HERENCIA, ...). Si por error devuelve directamente
     * ASOCIACION/HERENCIA/etc. como operación, lo corregimos aquí.
     *
     * También permite evolucionar una relación normal existente a una
     * relación con clase de asociación sin crear una relación duplicada.
     */
    private PlanIa normalizarPlanComun(
            PlanIa plan,
            ModeloCompletoResponse modeloActual
    ) {
        if (plan == null || plan.operaciones() == null
                || plan.operaciones().isEmpty()) {
            return plan;
        }

        List<OperacionIa> operaciones =
                normalizarTiposOperacionRelacion(
                        plan.operaciones()
                );

        operaciones =
                normalizarClaseAsociacionSobreRelacionExistente(
                        operaciones,
                        modeloActual
                );

        operaciones =
                eliminarCreacionesRedundantesParaClaseAsociacion(
                        operaciones
                );

        operaciones =
                protegerRelacionBaseClaseAsociacion(
                        operaciones,
                        modeloActual
                );

        operaciones =
                eliminarRelacionesAuxiliaresClaseAsociacionDelPlan(
                        operaciones
                );

        return new PlanIa(
                plan.mensaje(),
                List.copyOf(operaciones)
        );
    }

    /*
     * Evita un caso que puede devolver la IA en dos pasos para una misma pareja:
     *
     *   1) CREAR_RELACION Venta--Producto
     *   2) ACTUALIZAR_RELACION Venta--Producto con claseAsociacion=DetalleVenta
     *
     * Si Venta--Producto ya existe, ejecutar el paso 1 crea una relación paralela y
     * el paso 2 queda ambiguo. Cuando el plan contiene una operación que asigna una
     * clase de asociación a una pareja de clases, cualquier CREAR_RELACION adicional
     * y sin claseAsociacion para esa misma pareja es redundante y se descarta.
     */
    private List<OperacionIa> eliminarCreacionesRedundantesParaClaseAsociacion(
            List<OperacionIa> operaciones
    ) {
        List<OperacionIa> resultado = new ArrayList<>();

        for (OperacionIa operacion : operaciones) {
            if (operacion == null
                    || !esTipoOperacion(operacion, "CREAR_RELACION")
                    || tieneTexto(operacion.claseAsociacion())
                    || !tieneTexto(operacion.claseOrigen())
                    || !tieneTexto(operacion.claseDestino())) {

                resultado.add(operacion);
                continue;
            }

            boolean existeAsignacionClaseAsociacionMismaPareja =
                    operaciones.stream()
                            .filter(otra -> otra != operacion)
                            .anyMatch(otra ->
                                    esOperacionRelacion(otra)
                                            && tieneTexto(otra.claseAsociacion())
                                            && mismaParejaClases(
                                            operacion,
                                            otra
                                    )
                            );

            if (!existeAsignacionClaseAsociacionMismaPareja) {
                resultado.add(operacion);
            }
        }

        return resultado;
    }

    private boolean esOperacionRelacion(OperacionIa operacion) {
        return esTipoOperacion(operacion, "CREAR_RELACION")
                || esTipoOperacion(operacion, "ACTUALIZAR_RELACION")
                || esTipoOperacion(operacion, "ELIMINAR_RELACION");
    }

    private boolean mismaParejaClases(
            OperacionIa primera,
            OperacionIa segunda
    ) {
        if (primera == null
                || segunda == null
                || !tieneTexto(primera.claseOrigen())
                || !tieneTexto(primera.claseDestino())
                || !tieneTexto(segunda.claseOrigen())
                || !tieneTexto(segunda.claseDestino())) {
            return false;
        }

        boolean mismoSentido =
                coincideClase(
                        primera.claseOrigen(),
                        segunda.claseOrigen()
                ) && coincideClase(
                        primera.claseDestino(),
                        segunda.claseDestino()
                );

        boolean sentidoInverso =
                coincideClase(
                        primera.claseOrigen(),
                        segunda.claseDestino()
                ) && coincideClase(
                        primera.claseDestino(),
                        segunda.claseOrigen()
                );

        return mismoSentido || sentidoInverso;
    }

    /*
     * Protege la relación base cuando la IA quiere convertirla en una
     * Association Class.
     *
     * Algunos planes de IA pueden venir así:
     *
     *   1) ELIMINAR_RELACION Venta--Producto
     *   2) CREAR_RELACION / ACTUALIZAR_RELACION Venta--Producto
     *      con claseAsociacion=DetalleVenta
     *
     * Si Venta--Producto ya existe y el paso 2 representa una actualización
     * de esa misma relación, ejecutar primero el paso 1 hace que el paso 2
     * falle con "No se encontró la relación UML indicada".
     *
     * Por eso, cuando existe UNA sola relación base entre las clases y el plan
     * contiene una asignación de clase de asociación para esa misma pareja,
     * descartamos el ELIMINAR_RELACION previo y conservamos la actualización.
     */
    private List<OperacionIa> protegerRelacionBaseClaseAsociacion(
            List<OperacionIa> operaciones,
            ModeloCompletoResponse modeloActual
    ) {
        List<OperacionIa> resultado = new ArrayList<>();

        for (OperacionIa operacion : operaciones) {
            if (operacion == null
                    || !esTipoOperacion(operacion, "ELIMINAR_RELACION")
                    || !tieneTexto(operacion.claseOrigen())
                    || !tieneTexto(operacion.claseDestino())) {

                resultado.add(operacion);
                continue;
            }

            boolean existeAsignacionClaseAsociacionMismaPareja =
                    operaciones.stream()
                            .filter(otra -> otra != operacion)
                            .anyMatch(otra ->
                                    (esTipoOperacion(otra, "CREAR_RELACION")
                                            || esTipoOperacion(
                                            otra,
                                            "ACTUALIZAR_RELACION"
                                    ))
                                            && tieneTexto(
                                            otra.claseAsociacion()
                                    )
                                            && mismaParejaClases(
                                            operacion,
                                            otra
                                    )
                            );

            if (!existeAsignacionClaseAsociacionMismaPareja) {
                resultado.add(operacion);
                continue;
            }

            RelacionDiagramaResponse relacionBase =
                    buscarRelacionUnicaEntreClases(
                            modeloActual,
                            operacion.claseOrigen(),
                            operacion.claseDestino()
                    );

            /*
             * Solo omitimos el borrado cuando realmente existe una única
             * relación base. Si no existe o hay más de una, no alteramos el
             * plan porque podría tratarse de una corrección legítima.
             */
            if (relacionBase == null) {
                resultado.add(operacion);
            }
        }

        return resultado;
    }

    /*
     * Una Association Class NO se representa creando asociaciones normales
     * A--C y B--C. Si el mismo plan contiene una operación que asigna
     * claseAsociacion=C a la relación principal A--B, descartamos cualquier
     * CREAR_RELACION auxiliar A--C o B--C generado por la IA.
     */
    private List<OperacionIa> eliminarRelacionesAuxiliaresClaseAsociacionDelPlan(
            List<OperacionIa> operaciones
    ) {
        List<OperacionIa> resultado = new ArrayList<>();

        for (OperacionIa operacion : operaciones) {
            if (operacion == null
                    || !esTipoOperacion(operacion, "CREAR_RELACION")
                    || tieneTexto(operacion.claseAsociacion())
                    || !tieneTexto(operacion.claseOrigen())
                    || !tieneTexto(operacion.claseDestino())) {

                resultado.add(operacion);
                continue;
            }

            boolean esAuxiliarDeAssociationClass =
                    operaciones.stream()
                            .filter(otra -> otra != operacion)
                            .anyMatch(otra ->
                                    (esTipoOperacion(
                                            otra,
                                            "CREAR_RELACION"
                                    ) || esTipoOperacion(
                                            otra,
                                            "ACTUALIZAR_RELACION"
                                    ))
                                            && tieneTexto(
                                            otra.claseAsociacion()
                                    )
                                            && tieneTexto(
                                            otra.claseOrigen()
                                    )
                                            && tieneTexto(
                                            otra.claseDestino()
                                    )
                                            && esRelacionAuxiliarDeAsignacion(
                                            operacion,
                                            otra
                                    )
                            );

            if (!esAuxiliarDeAssociationClass) {
                resultado.add(operacion);
            }
        }

        return resultado;
    }

    private boolean esRelacionAuxiliarDeAsignacion(
            OperacionIa posibleAuxiliar,
            OperacionIa asignacion
    ) {
        String claseAsociacion =
                asignacion.claseAsociacion();

        String extremoA =
                asignacion.claseOrigen();

        String extremoB =
                asignacion.claseDestino();

        if (!tieneTexto(claseAsociacion)
                || !tieneTexto(extremoA)
                || !tieneTexto(extremoB)) {
            return false;
        }

        return esPareja(
                posibleAuxiliar,
                claseAsociacion,
                extremoA
        ) || esPareja(
                posibleAuxiliar,
                claseAsociacion,
                extremoB
        );
    }

    private boolean esPareja(
            OperacionIa operacion,
            String claseA,
            String claseB
    ) {
        if (operacion == null
                || !tieneTexto(operacion.claseOrigen())
                || !tieneTexto(operacion.claseDestino())) {
            return false;
        }

        boolean mismoSentido =
                coincideClase(
                        operacion.claseOrigen(),
                        claseA
                ) && coincideClase(
                        operacion.claseDestino(),
                        claseB
                );

        boolean sentidoInverso =
                coincideClase(
                        operacion.claseOrigen(),
                        claseB
                ) && coincideClase(
                        operacion.claseDestino(),
                        claseA
                );

        return mismoSentido || sentidoInverso;
    }

    private List<OperacionIa> normalizarTiposOperacionRelacion(
            List<OperacionIa> operaciones
    ) {
        List<OperacionIa> resultado = new ArrayList<>();

        for (OperacionIa operacion : operaciones) {
            if (operacion == null || operacion.tipo() == null) {
                resultado.add(operacion);
                continue;
            }

            String tipoRecibido =
                    operacion.tipo()
                            .trim()
                            .toUpperCase(Locale.ROOT);

            boolean tipoUmlEnCampoOperacion =
                    "ASOCIACION".equals(tipoRecibido)
                            || "AGREGACION".equals(tipoRecibido)
                            || "COMPOSICION".equals(tipoRecibido)
                            || "HERENCIA".equals(tipoRecibido)
                            || "GENERALIZACION".equals(tipoRecibido)
                            || "DEPENDENCIA".equals(tipoRecibido)
                            || "REALIZACION".equals(tipoRecibido);

            if (!tipoUmlEnCampoOperacion) {
                resultado.add(operacion);
                continue;
            }

            ObjectNode nodo =
                    objectMapper.convertValue(
                            operacion,
                            ObjectNode.class
                    );

            nodo.put("tipo", "CREAR_RELACION");

            if (operacion.tipoRelacion() == null
                    || operacion.tipoRelacion().isBlank()) {
                nodo.put(
                        "tipoRelacion",
                        normalizarTipoRelacion(tipoRecibido)
                );
            }

            resultado.add(
                    objectMapper.convertValue(
                            nodo,
                            OperacionIa.class
                    )
            );
        }

        return resultado;
    }

    /*
     * Caso importante para el examen:
     *
     * 1) Ya existe Venta -- Producto como asociación normal.
     * 2) Luego el usuario crea DetalleVenta y pide usarla como
     *    clase de asociación de esa relación.
     *
     * Si la IA responde CREAR_RELACION con claseAsociacion,
     * no debemos duplicar Venta--Producto. Convertimos la operación
     * a ACTUALIZAR_RELACION sobre la relación base existente.
     */
    private List<OperacionIa> normalizarClaseAsociacionSobreRelacionExistente(
            List<OperacionIa> operaciones,
            ModeloCompletoResponse modeloActual
    ) {
        List<OperacionIa> resultado = new ArrayList<>();

        for (OperacionIa operacion : operaciones) {
            if (operacion == null
                    || !esTipoOperacion(operacion, "CREAR_RELACION")
                    || !tieneTexto(operacion.claseAsociacion())
                    || !tieneTexto(operacion.claseOrigen())
                    || !tieneTexto(operacion.claseDestino())) {

                resultado.add(operacion);
                continue;
            }

            RelacionDiagramaResponse relacionExistente =
                    buscarRelacionUnicaEntreClases(
                            modeloActual,
                            operacion.claseOrigen(),
                            operacion.claseDestino()
                    );

            if (relacionExistente == null) {
                resultado.add(operacion);
                continue;
            }

            ObjectNode nodo =
                    objectMapper.convertValue(
                            operacion,
                            ObjectNode.class
                    );

            nodo.put("tipo", "ACTUALIZAR_RELACION");

            String nombreSolicitado =
                    operacion.nombreRelacion();

            if (tieneTexto(relacionExistente.nombre())) {
                nodo.put(
                        "nombreRelacion",
                        relacionExistente.nombre()
                );

                if (tieneTexto(nombreSolicitado)
                        && !relacionExistente.nombre()
                        .equalsIgnoreCase(nombreSolicitado.trim())) {
                    nodo.put(
                            "nuevoNombreRelacion",
                            nombreSolicitado.trim()
                    );
                }
            } else {
                nodo.putNull("nombreRelacion");

                if (tieneTexto(nombreSolicitado)) {
                    nodo.put(
                            "nuevoNombreRelacion",
                            nombreSolicitado.trim()
                    );
                }
            }

            resultado.add(
                    objectMapper.convertValue(
                            nodo,
                            OperacionIa.class
                    )
            );
        }

        return resultado;
    }

    private RelacionDiagramaResponse buscarRelacionUnicaEntreClases(
            ModeloCompletoResponse modelo,
            String nombreOrigen,
            String nombreDestino
    ) {
        List<RelacionDiagramaResponse> candidatas =
                buscarRelacionesEntreClases(
                        modelo,
                        nombreOrigen,
                        nombreDestino
                );

        if (candidatas.size() == 1) {
            return candidatas.get(0);
        }

        /*
         * Si por una ejecución anterior quedó una relación con Association
         * Class y otra relación base, para futuras conversiones preferimos
         * la única relación base. Esto evita tratar el caso como ambiguo.
         */
        List<RelacionDiagramaResponse> relacionesBase =
                candidatas.stream()
                        .filter(relacion ->
                                relacion.claseAsociacionId() == null
                        )
                        .toList();

        return relacionesBase.size() == 1
                ? relacionesBase.get(0)
                : null;
    }

    private List<RelacionDiagramaResponse> buscarRelacionesEntreClases(
            ModeloCompletoResponse modelo,
            String nombreOrigen,
            String nombreDestino
    ) {
        ClaseDiagramaResponse origen =
                buscarClaseOpcional(
                        modelo,
                        nombreOrigen
                );

        ClaseDiagramaResponse destino =
                buscarClaseOpcional(
                        modelo,
                        nombreDestino
                );

        if (origen == null || destino == null) {
            return List.of();
        }

        return modelo.relaciones()
                .stream()
                .filter(relacion -> {
                    boolean mismoSentido =
                            relacion.claseOrigenId().equals(origen.id())
                                    && relacion.claseDestinoId()
                                    .equals(destino.id());

                    boolean sentidoInverso =
                            relacion.claseOrigenId().equals(destino.id())
                                    && relacion.claseDestinoId()
                                    .equals(origen.id());

                    return mismoSentido || sentidoInverso;
                })
                .toList();
    }

    private List<OperacionIa> normalizarTiposAtributosImagen(
            List<OperacionIa> operaciones
    ) {
        List<OperacionIa> resultado = new ArrayList<>();

        for (OperacionIa operacion : operaciones) {
            if (operacion == null
                    || operacion.tipo() == null
                    || !"CREAR_ATRIBUTO".equalsIgnoreCase(
                            operacion.tipo().trim()
                    )
                    || (operacion.tipoDato() != null
                    && !operacion.tipoDato().isBlank())) {

                resultado.add(operacion);
                continue;
            }

            ObjectNode nodo =
                    objectMapper.convertValue(
                            operacion,
                            ObjectNode.class
                    );

            nodo.put(
                    "tipoDato",
                    inferirTipoDatoAtributo(
                            operacion.atributo()
                    )
            );

            resultado.add(
                    objectMapper.convertValue(
                            nodo,
                            OperacionIa.class
                    )
            );
        }

        return resultado;
    }

    private String inferirTipoDatoAtributo(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "String";
        }

        String normalizado =
                nombre.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("á", "a")
                        .replace("é", "e")
                        .replace("í", "i")
                        .replace("ó", "o")
                        .replace("ú", "u");

        if (normalizado.equals("id")
                || normalizado.startsWith("id_")
                || normalizado.endsWith("id")) {
            return "int";
        }

        if (normalizado.contains("fecha")) {
            return "Date";
        }

        if (normalizado.contains("precio")
                || normalizado.contains("monto")
                || normalizado.contains("total")
                || normalizado.contains("subtotal")
                || normalizado.contains("saldo")
                || normalizado.contains("costo")
                || normalizado.contains("importe")) {
            return "Decimal";
        }

        if (normalizado.contains("cantidad")
                || normalizado.contains("stock")
                || normalizado.contains("unidad")
                || normalizado.contains("contador")) {
            return "int";
        }

        if (normalizado.startsWith("es_")
                || normalizado.startsWith("tiene_")
                || normalizado.contains("activo")
                || normalizado.contains("habilitado")
                || normalizado.contains("disponible")) {
            return "Boolean";
        }

        return "String";
    }

    private List<OperacionIa> normalizarClasesAsociacionImagen(
            List<OperacionIa> operaciones,
            ModeloCompletoResponse modeloActual
    ) {
        List<OperacionIa> resultado =
                new ArrayList<>(operaciones);

        boolean normalizado;

        do {
            normalizado = false;

            busqueda:
            for (int i = 0; i < resultado.size(); i++) {
                OperacionIa primera = resultado.get(i);

                if (!esRelacionCandidataAssociationClass(primera)) {
                    continue;
                }

                for (int j = i + 1; j < resultado.size(); j++) {
                    OperacionIa segunda = resultado.get(j);

                    if (!esRelacionCandidataAssociationClass(segunda)) {
                        continue;
                    }

                    String claseAsociacion =
                            claseCompartidaUnica(
                                    primera,
                                    segunda
                            );

                    if (claseAsociacion == null) {
                        continue;
                    }

                    String extremoPrimera =
                            otroExtremo(
                                    primera,
                                    claseAsociacion
                            );

                    String extremoSegunda =
                            otroExtremo(
                                    segunda,
                                    claseAsociacion
                            );

                    if (extremoPrimera == null
                            || extremoSegunda == null
                            || extremoPrimera.equalsIgnoreCase(
                                    extremoSegunda
                            )) {
                        continue;
                    }

                    if (contarRelacionesConClase(
                            resultado,
                            claseAsociacion
                    ) != 2) {
                        continue;
                    }

                    if (existeRelacionDirectaEnPlan(
                            resultado,
                            extremoPrimera,
                            extremoSegunda,
                            i,
                            j
                    )) {
                        continue;
                    }

                    String nombrePrimera =
                            nombreRelacionVisible(primera);

                    String nombreSegunda =
                            nombreRelacionVisible(segunda);

                    if (tieneTexto(nombrePrimera)
                            && tieneTexto(nombreSegunda)
                            && !nombrePrimera.equalsIgnoreCase(
                                    nombreSegunda
                            )) {
                        continue;
                    }

                    OperacionIa relacionConNombre =
                            tieneTexto(nombrePrimera)
                                    ? primera
                                    : tieneTexto(nombreSegunda)
                                    ? segunda
                                    : null;

                    if (relacionConNombre == null) {
                        continue;
                    }

                    if (!pareceClaseAsociativa(
                            claseAsociacion,
                            extremoPrimera,
                            extremoSegunda,
                            resultado,
                            modeloActual
                    )) {
                        continue;
                    }

                    OperacionIa otraRelacion =
                            relacionConNombre == primera
                                    ? segunda
                                    : primera;

                    String origen =
                            otroExtremo(
                                    relacionConNombre,
                                    claseAsociacion
                            );

                    String destino =
                            otroExtremo(
                                    otraRelacion,
                                    claseAsociacion
                            );

                    String multiplicidadOrigen =
                            multiplicidadEnExtremo(
                                    relacionConNombre,
                                    origen
                            );

                    String multiplicidadDestino =
                            multiplicidadEnExtremo(
                                    otraRelacion,
                                    destino
                            );

                    String nombre =
                            nombreRelacionVisible(
                                    relacionConNombre
                            );

                    String tipoRelacion =
                            textoPreferido(
                                    relacionConNombre.tipoRelacion(),
                                    textoPreferido(
                                            otraRelacion.tipoRelacion(),
                                            "ASOCIACION"
                                    )
                            );

                    OperacionIa relacionPrincipal =
                            crearOperacionRelacion(
                                    "CREAR_RELACION",
                                    origen,
                                    destino,
                                    claseAsociacion,
                                    tipoRelacion,
                                    multiplicidadOrigen,
                                    multiplicidadDestino,
                                    nombre
                            );

                    List<OperacionIa> reemplazos =
                            new ArrayList<>();

                    if (esTipoOperacion(
                            primera,
                            "ACTUALIZAR_RELACION"
                    )) {
                        reemplazos.add(
                                crearOperacionEliminarRelacion(
                                        primera
                                )
                        );
                    }

                    if (esTipoOperacion(
                            segunda,
                            "ACTUALIZAR_RELACION"
                    )) {
                        reemplazos.add(
                                crearOperacionEliminarRelacion(
                                        segunda
                                )
                        );
                    }

                    resultado.remove(j);
                    resultado.remove(i);
                    resultado.addAll(reemplazos);
                    resultado.add(relacionPrincipal);

                    normalizado = true;
                    break busqueda;
                }
            }
        } while (normalizado);

        return resultado;
    }

    private boolean esRelacionCandidataAssociationClass(
            OperacionIa operacion
    ) {
        if (operacion == null
                || operacion.tipo() == null
                || operacion.claseOrigen() == null
                || operacion.claseDestino() == null) {
            return false;
        }

        if (!esTipoOperacion(
                operacion,
                "CREAR_RELACION"
        ) && !esTipoOperacion(
                operacion,
                "ACTUALIZAR_RELACION"
        )) {
            return false;
        }

        if (operacion.claseAsociacion() != null
                && !operacion.claseAsociacion().isBlank()) {
            return false;
        }

        String tipo =
                normalizarTipoRelacion(
                        textoPreferido(
                                operacion.tipoRelacion(),
                                "ASOCIACION"
                        )
                );

        return "ASOCIACION".equalsIgnoreCase(tipo);
    }

    private boolean esTipoOperacion(
            OperacionIa operacion,
            String tipoEsperado
    ) {
        return operacion != null
                && operacion.tipo() != null
                && operacion.tipo().trim()
                .equalsIgnoreCase(tipoEsperado);
    }

    private String claseCompartidaUnica(
            OperacionIa primera,
            OperacionIa segunda
    ) {
        String[] extremosPrimera = {
                primera.claseOrigen(),
                primera.claseDestino()
        };

        String[] extremosSegunda = {
                segunda.claseOrigen(),
                segunda.claseDestino()
        };

        String compartida = null;

        for (String a : extremosPrimera) {
            if (!tieneTexto(a)) {
                continue;
            }

            for (String b : extremosSegunda) {
                if (!tieneTexto(b)
                        || !a.trim().equalsIgnoreCase(
                                b.trim()
                        )) {
                    continue;
                }

                if (compartida != null
                        && !compartida.equalsIgnoreCase(
                                a.trim()
                        )) {
                    return null;
                }

                compartida = a.trim();
            }
        }

        return compartida;
    }

    private String otroExtremo(
            OperacionIa operacion,
            String claseCompartida
    ) {
        if (!tieneTexto(claseCompartida)) {
            return null;
        }

        if (tieneTexto(operacion.claseOrigen())
                && operacion.claseOrigen().trim()
                .equalsIgnoreCase(
                        claseCompartida.trim()
                )) {
            return tieneTexto(operacion.claseDestino())
                    ? operacion.claseDestino().trim()
                    : null;
        }

        if (tieneTexto(operacion.claseDestino())
                && operacion.claseDestino().trim()
                .equalsIgnoreCase(
                        claseCompartida.trim()
                )) {
            return tieneTexto(operacion.claseOrigen())
                    ? operacion.claseOrigen().trim()
                    : null;
        }

        return null;
    }

    private int contarRelacionesConClase(
            List<OperacionIa> operaciones,
            String nombreClase
    ) {
        int cantidad = 0;

        for (OperacionIa operacion : operaciones) {
            if (!esRelacionCandidataAssociationClass(operacion)) {
                continue;
            }

            if (coincideClase(
                    operacion.claseOrigen(),
                    nombreClase
            ) || coincideClase(
                    operacion.claseDestino(),
                    nombreClase
            )) {
                cantidad++;
            }
        }

        return cantidad;
    }

    private boolean existeRelacionDirectaEnPlan(
            List<OperacionIa> operaciones,
            String claseA,
            String claseB,
            int indiceIgnoradoA,
            int indiceIgnoradoB
    ) {
        for (int indice = 0;
             indice < operaciones.size();
             indice++) {

            if (indice == indiceIgnoradoA
                    || indice == indiceIgnoradoB) {
                continue;
            }

            OperacionIa operacion =
                    operaciones.get(indice);

            if (!esRelacionCandidataAssociationClass(
                    operacion
            )) {
                continue;
            }

            boolean mismoSentido =
                    coincideClase(
                            operacion.claseOrigen(),
                            claseA
                    ) && coincideClase(
                            operacion.claseDestino(),
                            claseB
                    );

            boolean sentidoInverso =
                    coincideClase(
                            operacion.claseOrigen(),
                            claseB
                    ) && coincideClase(
                            operacion.claseDestino(),
                            claseA
                    );

            if (mismoSentido || sentidoInverso) {
                return true;
            }
        }

        return false;
    }

    private boolean pareceClaseAsociativa(
            String candidata,
            String extremoA,
            String extremoB,
            List<OperacionIa> operaciones,
            ModeloCompletoResponse modeloActual
    ) {
        if (!claseTieneAtributos(
                candidata,
                operaciones,
                modeloActual
        )) {
            return false;
        }

        if (claseTieneIdentificador(
                candidata,
                operaciones,
                modeloActual
        )) {
            return false;
        }

        return claseTieneIdentificador(
                extremoA,
                operaciones,
                modeloActual
        ) && claseTieneIdentificador(
                extremoB,
                operaciones,
                modeloActual
        );
    }

    private boolean claseTieneAtributos(
            String nombreClase,
            List<OperacionIa> operaciones,
            ModeloCompletoResponse modeloActual
    ) {
        for (OperacionIa operacion : operaciones) {
            if ((esTipoOperacion(
                    operacion,
                    "CREAR_ATRIBUTO"
            ) || esTipoOperacion(
                    operacion,
                    "ACTUALIZAR_ATRIBUTO"
            )) && coincideClase(
                    operacion.clase(),
                    nombreClase
            )) {
                return true;
            }
        }

        ClaseDiagramaResponse clase =
                buscarClaseOpcional(
                        modeloActual,
                        nombreClase
                );

        return clase != null
                && clase.atributos() != null
                && !clase.atributos().isEmpty();
    }

    private boolean claseTieneIdentificador(
            String nombreClase,
            List<OperacionIa> operaciones,
            ModeloCompletoResponse modeloActual
    ) {
        for (OperacionIa operacion : operaciones) {
            if (!(esTipoOperacion(
                    operacion,
                    "CREAR_ATRIBUTO"
            ) || esTipoOperacion(
                    operacion,
                    "ACTUALIZAR_ATRIBUTO"
            ))) {
                continue;
            }

            if (!coincideClase(
                    operacion.clase(),
                    nombreClase
            )) {
                continue;
            }

            if (Boolean.TRUE.equals(
                    operacion.identificador()
            ) || pareceNombreIdentificador(
                    operacion.atributo()
            )) {
                return true;
            }
        }

        ClaseDiagramaResponse clase =
                buscarClaseOpcional(
                        modeloActual,
                        nombreClase
                );

        if (clase == null || clase.atributos() == null) {
            return false;
        }

        return clase.atributos()
                .stream()
                .anyMatch(atributo ->
                        Boolean.TRUE.equals(
                                atributo.identificador()
                        ) || pareceNombreIdentificador(
                                atributo.nombre()
                        )
                );
    }

    private boolean pareceNombreIdentificador(String nombre) {
        if (!tieneTexto(nombre)) {
            return false;
        }

        String normalizado =
                nombre.trim()
                        .toLowerCase(Locale.ROOT);

        return normalizado.equals("id")
                || normalizado.startsWith("id_")
                || normalizado.endsWith("id");
    }

    private String nombreRelacionVisible(
            OperacionIa operacion
    ) {
        return textoPreferido(
                operacion.nuevoNombreRelacion(),
                operacion.nombreRelacion()
        );
    }

    private String multiplicidadEnExtremo(
            OperacionIa operacion,
            String nombreClase
    ) {
        if (coincideClase(
                operacion.claseOrigen(),
                nombreClase
        )) {
            return operacion.multiplicidadOrigen();
        }

        if (coincideClase(
                operacion.claseDestino(),
                nombreClase
        )) {
            return operacion.multiplicidadDestino();
        }

        return null;
    }

    private OperacionIa crearOperacionRelacion(
            String tipoOperacion,
            String claseOrigen,
            String claseDestino,
            String claseAsociacion,
            String tipoRelacion,
            String multiplicidadOrigen,
            String multiplicidadDestino,
            String nombreRelacion
    ) {
        ObjectNode nodo =
                objectMapper.createObjectNode();

        nodo.put("tipo", tipoOperacion);
        nodo.put("claseOrigen", claseOrigen);
        nodo.put("claseDestino", claseDestino);
        nodo.put("claseAsociacion", claseAsociacion);
        nodo.put("tipoRelacion", tipoRelacion);
        ponerTextoNullable(
                nodo,
                "multiplicidadOrigen",
                multiplicidadOrigen
        );
        ponerTextoNullable(
                nodo,
                "multiplicidadDestino",
                multiplicidadDestino
        );
        ponerTextoNullable(
                nodo,
                "nombreRelacion",
                nombreRelacion
        );

        return objectMapper.convertValue(
                nodo,
                OperacionIa.class
        );
    }

    private OperacionIa crearOperacionEliminarRelacion(
            OperacionIa original
    ) {
        ObjectNode nodo =
                objectMapper.createObjectNode();

        nodo.put("tipo", "ELIMINAR_RELACION");
        nodo.put(
                "claseOrigen",
                original.claseOrigen()
        );
        nodo.put(
                "claseDestino",
                original.claseDestino()
        );
        ponerTextoNullable(
                nodo,
                "nombreRelacion",
                nombreRelacionVisible(original)
        );

        return objectMapper.convertValue(
                nodo,
                OperacionIa.class
        );
    }

    private boolean coincideClase(
            String a,
            String b
    ) {
        return tieneTexto(a)
                && tieneTexto(b)
                && a.trim().equalsIgnoreCase(
                        b.trim()
                );
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String aJsonSeguro(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception ex) {
            return String.valueOf(valor);
        }
    }

    private String resumirModelo(
            ModeloCompletoResponse modelo
    ) {
        if (modelo == null) {
            return "null";
        }

        StringBuilder resumen =
                new StringBuilder();

        resumen.append("version=")
                .append(modelo.version())
                .append(", clases=")
                .append(modelo.clases() != null
                        ? modelo.clases().size()
                        : 0)
                .append(", relaciones=")
                .append(modelo.relaciones() != null
                        ? modelo.relaciones().size()
                        : 0);

        if (modelo.relaciones() == null
                || modelo.relaciones().isEmpty()) {
            return resumen.toString();
        }

        resumen.append(" [");

        for (int i = 0; i < modelo.relaciones().size(); i++) {
            RelacionDiagramaResponse relacion =
                    modelo.relaciones().get(i);

            ClaseDiagramaResponse origen =
                    modelo.clases()
                            .stream()
                            .filter(clase ->
                                    clase.id().equals(
                                            relacion.claseOrigenId()
                                    )
                            )
                            .findFirst()
                            .orElse(null);

            ClaseDiagramaResponse destino =
                    modelo.clases()
                            .stream()
                            .filter(clase ->
                                    clase.id().equals(
                                            relacion.claseDestinoId()
                                    )
                            )
                            .findFirst()
                            .orElse(null);

            ClaseDiagramaResponse claseAsociacion =
                    relacion.claseAsociacionId() == null
                            ? null
                            : modelo.clases()
                            .stream()
                            .filter(clase ->
                                    clase.id().equals(
                                            relacion.claseAsociacionId()
                                    )
                            )
                            .findFirst()
                            .orElse(null);

            if (i > 0) {
                resumen.append("; ");
            }

            resumen.append(
                            origen != null
                                    ? origen.nombre()
                                    : relacion.claseOrigenId()
                    )
                    .append("->")
                    .append(
                            destino != null
                                    ? destino.nombre()
                                    : relacion.claseDestinoId()
                    )
                    .append(" tipo=")
                    .append(relacion.tipo())
                    .append(" mult=")
                    .append(relacion.multiplicidadOrigen())
                    .append("->")
                    .append(relacion.multiplicidadDestino())
                    .append(" assocClass=")
                    .append(
                            claseAsociacion != null
                                    ? claseAsociacion.nombre()
                                    : relacion.claseAsociacionId()
                    );
        }

        resumen.append("]");

        return resumen.toString();
    }

    private ChatIaResponse ejecutarPlan(
            String codigoSesion,
            String clienteId,
            PlanIa plan,
            ModeloCompletoResponse modeloActual
    ) {
        List<String> acciones = new ArrayList<>();

        log.info(
                "[IA-TRACE] EJECUTAR PLAN inicio. operaciones={}, modelo={}",
                plan.operaciones() != null ? plan.operaciones().size() : 0,
                resumirModelo(modeloActual)
        );

        if (plan.operaciones() != null) {
            int indice = 0;

            for (OperacionIa operacionOriginal : plan.operaciones()) {
                indice++;

                log.info(
                        "[IA-TRACE] OP {} ORIGINAL: {}",
                        indice,
                        aJsonSeguro(operacionOriginal)
                );

                OperacionIa operacion =
                        normalizarOperacionContraModeloActual(
                                operacionOriginal,
                                modeloActual
                        );

                log.info(
                        "[IA-TRACE] OP {} CONTRA MODELO ACTUAL: {}",
                        indice,
                        aJsonSeguro(operacion)
                );

                boolean omitirDuplicada =
                        debeOmitirCreacionDuplicada(
                                operacion,
                                modeloActual
                        );

                boolean omitirAuxiliar =
                        esCreacionAuxiliarDeClaseAsociacionExistente(
                                operacion,
                                modeloActual
                        );

                if (omitirDuplicada || omitirAuxiliar) {
                    log.info(
                            "[IA-TRACE] OP {} OMITIDA. duplicada={}, auxiliarAssociationClass={}, modelo={}",
                            indice,
                            omitirDuplicada,
                            omitirAuxiliar,
                            resumirModelo(modeloActual)
                    );
                    continue;
                }

                OperacionColaborativaRequest solicitud =
                        convertirOperacion(operacion, clienteId, modeloActual);

                log.info(
                        "[IA-TRACE] OP {} SOLICITUD: {}",
                        indice,
                        aJsonSeguro(solicitud)
                );

                OperacionColaborativaResponse respuesta =
                        operacionColaborativaService.aplicarOperacion(
                                codigoSesion,
                                solicitud
                        );

                modeloActual = respuesta.modelo();

                log.info(
                        "[IA-TRACE] OP {} APLICADA. MODELO DESPUES: {}",
                        indice,
                        resumirModelo(modeloActual)
                );

                acciones.add(describirOperacion(operacion));
                publicarOperacion(codigoSesion, respuesta);
            }
        }

        log.info(
                "[IA-TRACE] EJECUTAR PLAN fin. acciones={}, modeloFinal={}",
                acciones,
                resumirModelo(modeloActual)
        );

        return new ChatIaResponse(
                plan.mensaje(),
                List.copyOf(acciones),
                modeloActual
        );
    }

    /*
     * Última barrera defensiva antes de aplicar una operación.
     *
     * Si la IA pide CREAR_RELACION con claseAsociacion pero ya existe
     * una relación base entre esas clases, la operación debe ser una
     * actualización, no una nueva relación paralela.
     *
     * Se hace aquí (además de las normalizaciones previas) porque
     * modeloActual cambia después de cada operación del mismo plan.
     */
    private OperacionIa normalizarOperacionContraModeloActual(
            OperacionIa operacion,
            ModeloCompletoResponse modeloActual
    ) {
        if (operacion == null
                || !esTipoOperacion(operacion, "CREAR_RELACION")
                || !tieneTexto(operacion.claseAsociacion())
                || !tieneTexto(operacion.claseOrigen())
                || !tieneTexto(operacion.claseDestino())) {
            return operacion;
        }

        RelacionDiagramaResponse relacionExistente =
                buscarRelacionPreferenteEntreClases(
                        modeloActual,
                        operacion.claseOrigen(),
                        operacion.claseDestino(),
                        operacion.claseAsociacion()
                );

        if (relacionExistente == null) {
            return operacion;
        }

        ObjectNode nodo =
                objectMapper.convertValue(
                        operacion,
                        ObjectNode.class
                );

        nodo.put("tipo", "ACTUALIZAR_RELACION");

        /*
         * Para identificar sin ambigüedad la relación actual, usamos su
         * nombre real si lo tiene. Si no tiene nombre, dejamos null y la
         * resolución posterior usará extremos + clase de asociación/base.
         */
        if (tieneTexto(relacionExistente.nombre())) {
            nodo.put(
                    "nombreRelacion",
                    relacionExistente.nombre()
            );

            if (tieneTexto(operacion.nombreRelacion())
                    && !relacionExistente.nombre()
                    .equalsIgnoreCase(
                            operacion.nombreRelacion().trim()
                    )) {
                nodo.put(
                        "nuevoNombreRelacion",
                        operacion.nombreRelacion().trim()
                );
            }
        } else {
            nodo.putNull("nombreRelacion");

            if (tieneTexto(operacion.nombreRelacion())) {
                nodo.put(
                        "nuevoNombreRelacion",
                        operacion.nombreRelacion().trim()
                );
            }
        }

        return objectMapper.convertValue(
                nodo,
                OperacionIa.class
        );
    }

    /*
     * Busca una relación entre dos clases. Si hay varias, para una
     * Association Class prioriza:
     *
     * 1) la que ya usa esa misma clase de asociación (operación idempotente);
     * 2) si no, la única relación base sin clase de asociación.
     */
    private RelacionDiagramaResponse buscarRelacionPreferenteEntreClases(
            ModeloCompletoResponse modelo,
            String nombreOrigen,
            String nombreDestino,
            String nombreClaseAsociacion
    ) {
        List<RelacionDiagramaResponse> candidatas =
                buscarRelacionesEntreClases(
                        modelo,
                        nombreOrigen,
                        nombreDestino
                );

        if (candidatas.isEmpty()) {
            return null;
        }

        if (candidatas.size() == 1) {
            return candidatas.get(0);
        }

        ClaseDiagramaResponse claseAsociacion =
                buscarClaseOpcional(
                        modelo,
                        nombreClaseAsociacion
                );

        if (claseAsociacion != null) {
            List<RelacionDiagramaResponse> conMismaClaseAsociacion =
                    candidatas.stream()
                            .filter(relacion ->
                                    relacion.claseAsociacionId() != null
                                            && relacion.claseAsociacionId()
                                            .equals(claseAsociacion.id())
                            )
                            .toList();

            if (conMismaClaseAsociacion.size() == 1) {
                return conMismaClaseAsociacion.get(0);
            }
        }

        List<RelacionDiagramaResponse> relacionesBase =
                candidatas.stream()
                        .filter(relacion ->
                                relacion.claseAsociacionId() == null
                        )
                        .toList();

        return relacionesBase.size() == 1
                ? relacionesBase.get(0)
                : null;
    }

    /*
     * Idempotencia para comandos repetidos:
     *
     * Si C ya es clase de asociación de A--B, nunca permitimos que una
     * respuesta posterior de la IA cree asociaciones normales A--C o B--C.
     */
    private boolean esCreacionAuxiliarDeClaseAsociacionExistente(
            OperacionIa operacion,
            ModeloCompletoResponse modelo
    ) {
        if (operacion == null
                || !esTipoOperacion(operacion, "CREAR_RELACION")
                || tieneTexto(operacion.claseAsociacion())
                || !tieneTexto(operacion.claseOrigen())
                || !tieneTexto(operacion.claseDestino())) {
            return false;
        }

        ClaseDiagramaResponse origen =
                buscarClaseOpcional(
                        modelo,
                        operacion.claseOrigen()
                );

        ClaseDiagramaResponse destino =
                buscarClaseOpcional(
                        modelo,
                        operacion.claseDestino()
                );

        if (origen == null || destino == null) {
            return false;
        }

        for (RelacionDiagramaResponse relacion : modelo.relaciones()) {
            if (relacion.claseAsociacionId() == null) {
                continue;
            }

            UUID claseAsociacionId =
                    relacion.claseAsociacionId();

            boolean origenEsClaseAsociacion =
                    origen.id().equals(claseAsociacionId);

            boolean destinoEsClaseAsociacion =
                    destino.id().equals(claseAsociacionId);

            if (!origenEsClaseAsociacion
                    && !destinoEsClaseAsociacion) {
                continue;
            }

            UUID otroExtremo =
                    origenEsClaseAsociacion
                            ? destino.id()
                            : origen.id();

            boolean perteneceALaRelacionPrincipal =
                    otroExtremo.equals(
                            relacion.claseOrigenId()
                    ) || otroExtremo.equals(
                            relacion.claseDestinoId()
                    );

            if (perteneceALaRelacionPrincipal) {
                return true;
            }
        }

        return false;
    }

    private boolean debeOmitirCreacionDuplicada(
            OperacionIa operacion,
            ModeloCompletoResponse modelo
    ) {
        if (operacion == null || operacion.tipo() == null) {
            return false;
        }

        String tipo = operacion.tipo().trim().toUpperCase(Locale.ROOT);

        return switch (tipo) {
            case "CREAR_CLASE" -> existeClase(modelo, operacion.clase());
            case "CREAR_ATRIBUTO" -> existeAtributo(
                    modelo,
                    operacion.clase(),
                    operacion.atributo()
            );
            case "CREAR_RELACION" -> existeRelacionEquivalente(modelo, operacion);
            default -> false;
        };
    }

    private boolean existeClase(
            ModeloCompletoResponse modelo,
            String nombre
    ) {
        if (nombre == null || nombre.isBlank()) {
            return false;
        }

        String buscado = nombre.trim();
        return modelo.clases()
                .stream()
                .anyMatch(clase -> clase.nombre().equalsIgnoreCase(buscado));
    }

    private boolean existeAtributo(
            ModeloCompletoResponse modelo,
            String nombreClase,
            String nombreAtributo
    ) {
        if (nombreClase == null || nombreClase.isBlank()
                || nombreAtributo == null || nombreAtributo.isBlank()) {
            return false;
        }

        String claseBuscada = nombreClase.trim();
        String atributoBuscado = nombreAtributo.trim();

        return modelo.clases()
                .stream()
                .filter(clase -> clase.nombre().equalsIgnoreCase(claseBuscada))
                .flatMap(clase -> clase.atributos().stream())
                .anyMatch(atributo -> atributo.nombre().equalsIgnoreCase(atributoBuscado));
    }

    private boolean existeRelacionEquivalente(
            ModeloCompletoResponse modelo,
            OperacionIa operacion
    ) {
        if (operacion.claseOrigen() == null || operacion.claseOrigen().isBlank()
                || operacion.claseDestino() == null || operacion.claseDestino().isBlank()) {
            return false;
        }

        ClaseDiagramaResponse origen = buscarClaseOpcional(modelo, operacion.claseOrigen());
        ClaseDiagramaResponse destino = buscarClaseOpcional(modelo, operacion.claseDestino());

        if (origen == null || destino == null) {
            return false;
        }

        String tipoEsperado = normalizarTipoRelacion(
                textoPreferido(operacion.tipoRelacion(), "ASOCIACION")
        );

        ClaseDiagramaResponse claseAsociacionEsperada =
                buscarClaseOpcional(
                        modelo,
                        operacion.claseAsociacion()
                );

        if (operacion.claseAsociacion() != null
                && !operacion.claseAsociacion().isBlank()
                && claseAsociacionEsperada == null) {
            return false;
        }

        return modelo.relaciones()
                .stream()
                .anyMatch(relacion -> {
                    boolean mismoSentido =
                            relacion.claseOrigenId().equals(origen.id())
                                    && relacion.claseDestinoId().equals(destino.id());

                    boolean sentidoInverso =
                            relacion.claseOrigenId().equals(destino.id())
                                    && relacion.claseDestinoId().equals(origen.id());

                    if (!mismoSentido && !sentidoInverso) {
                        return false;
                    }

                    String tipoActual = normalizarTipoRelacion(relacion.tipo());
                    if (!tipoActual.equalsIgnoreCase(tipoEsperado)) {
                        return false;
                    }

                    if (!coincideTextoOpcional(
                            operacion.nombreRelacion(),
                            relacion.nombre()
                    )) {
                        return false;
                    }

                    if (claseAsociacionEsperada != null
                            && (relacion.claseAsociacionId() == null
                            || !relacion.claseAsociacionId()
                                    .equals(claseAsociacionEsperada.id()))) {
                        return false;
                    }

                    if (mismoSentido) {
                        return coincideTextoOpcional(
                                operacion.multiplicidadOrigen(),
                                relacion.multiplicidadOrigen()
                        ) && coincideTextoOpcional(
                                operacion.multiplicidadDestino(),
                                relacion.multiplicidadDestino()
                        );
                    }

                    return coincideTextoOpcional(
                            operacion.multiplicidadOrigen(),
                            relacion.multiplicidadDestino()
                    ) && coincideTextoOpcional(
                            operacion.multiplicidadDestino(),
                            relacion.multiplicidadOrigen()
                    );
                });
    }

    private ClaseDiagramaResponse buscarClaseOpcional(
            ModeloCompletoResponse modelo,
            String nombre
    ) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }

        String buscado = nombre.trim();
        return modelo.clases()
                .stream()
                .filter(clase -> clase.nombre().equalsIgnoreCase(buscado))
                .findFirst()
                .orElse(null);
    }

    private boolean coincideTextoOpcional(
            String esperado,
            String actual
    ) {
        if (esperado == null || esperado.isBlank()) {
            return true;
        }

        return actual != null
                && actual.trim().equalsIgnoreCase(esperado.trim());
    }

    private OperacionColaborativaRequest convertirOperacion(
            OperacionIa operacion,
            String clienteId,
            ModeloCompletoResponse modelo
    ) {
        String tipo = obligatorio(
                operacion.tipo(),
                "La IA no indicó el tipo de operación"
        ).toUpperCase(Locale.ROOT);

        ObjectNode datos = objectMapper.createObjectNode();
        TipoOperacionColaborativa tipoColaborativo;

        switch (tipo) {
            case "CREAR_CLASE" -> {
                tipoColaborativo = TipoOperacionColaborativa.CREAR_CLASE;
                datos.put(
                        "nombre",
                        obligatorio(
                                operacion.clase(),
                                "Falta el nombre de la clase a crear"
                        )
                );

                double[] posicion = siguientePosicion(modelo);
                datos.put("posicionX", posicion[0]);
                datos.put("posicionY", posicion[1]);
            }
            case "RENOMBRAR_CLASE" -> {
                tipoColaborativo = TipoOperacionColaborativa.RENOMBRAR_CLASE;
                ClaseDiagramaResponse clase = buscarClase(modelo, operacion.clase());
                datos.put("claseId", clase.id().toString());
                datos.put(
                        "nombre",
                        obligatorio(
                                operacion.nuevoNombre(),
                                "Falta el nuevo nombre de la clase"
                        )
                );
            }
            case "ELIMINAR_CLASE" -> {
                tipoColaborativo = TipoOperacionColaborativa.ELIMINAR_CLASE;
                ClaseDiagramaResponse clase = buscarClase(modelo, operacion.clase());
                datos.put("claseId", clase.id().toString());
            }
            case "CREAR_ATRIBUTO" -> {
                tipoColaborativo = TipoOperacionColaborativa.CREAR_ATRIBUTO;
                ClaseDiagramaResponse clase = buscarClase(modelo, operacion.clase());
                datos.put("claseId", clase.id().toString());
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
                        Boolean.TRUE.equals(operacion.permiteNulo())
                );
                datos.put(
                        "identificador",
                        Boolean.TRUE.equals(operacion.identificador())
                );
            }
            case "ACTUALIZAR_ATRIBUTO" -> {
                tipoColaborativo = TipoOperacionColaborativa.ACTUALIZAR_ATRIBUTO;
                ClaseDiagramaResponse clase = buscarClase(modelo, operacion.clase());
                AtributoDiagramaResponse atributo = buscarAtributo(clase, operacion.atributo());
                datos.put("atributoId", atributo.id().toString());
                datos.put("claseId", clase.id().toString());
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
                                : Boolean.TRUE.equals(atributo.permiteNulo())
                );
                datos.put(
                        "identificador",
                        operacion.identificador() != null
                                ? operacion.identificador()
                                : Boolean.TRUE.equals(atributo.identificador())
                );
            }
            case "ELIMINAR_ATRIBUTO" -> {
                tipoColaborativo = TipoOperacionColaborativa.ELIMINAR_ATRIBUTO;
                ClaseDiagramaResponse clase = buscarClase(modelo, operacion.clase());
                AtributoDiagramaResponse atributo = buscarAtributo(clase, operacion.atributo());
                datos.put("atributoId", atributo.id().toString());
                datos.put("claseId", clase.id().toString());
            }
            case "CREAR_RELACION" -> {
                tipoColaborativo = TipoOperacionColaborativa.CREAR_RELACION;
                ClaseDiagramaResponse origen = buscarClase(modelo, operacion.claseOrigen());
                ClaseDiagramaResponse destino = buscarClase(modelo, operacion.claseDestino());
                datos.put("claseOrigenId", origen.id().toString());
                datos.put("claseDestinoId", destino.id().toString());
                datos.put(
                        "tipo",
                        normalizarTipoRelacion(
                                textoPreferido(operacion.tipoRelacion(), "ASOCIACION")
                        )
                );
                ponerTextoNullable(datos, "multiplicidadOrigen", operacion.multiplicidadOrigen());
                ponerTextoNullable(datos, "multiplicidadDestino", operacion.multiplicidadDestino());
                ponerTextoNullable(datos, "nombre", operacion.nombreRelacion());

                if (operacion.claseAsociacion() != null
                        && !operacion.claseAsociacion().isBlank()) {

                    ClaseDiagramaResponse claseAsociacion =
                            buscarClase(
                                    modelo,
                                    operacion.claseAsociacion()
                            );

                    datos.put(
                            "claseAsociacionId",
                            claseAsociacion.id().toString()
                    );
                } else {
                    datos.putNull("claseAsociacionId");
                }
            }
            case "ACTUALIZAR_RELACION" -> {
                tipoColaborativo = TipoOperacionColaborativa.ACTUALIZAR_RELACION;
                RelacionDiagramaResponse relacion = buscarRelacion(modelo, operacion);
                datos.put("relacionId", relacion.id().toString());
                datos.put(
                        "tipo",
                        normalizarTipoRelacion(
                                textoPreferido(operacion.tipoRelacion(), relacion.tipo())
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

                if (operacion.claseAsociacion() != null
                        && !operacion.claseAsociacion().isBlank()) {

                    ClaseDiagramaResponse claseAsociacion =
                            buscarClase(
                                    modelo,
                                    operacion.claseAsociacion()
                            );

                    datos.put(
                            "claseAsociacionId",
                            claseAsociacion.id().toString()
                    );

                } else if (relacion.claseAsociacionId() != null) {

                    datos.put(
                            "claseAsociacionId",
                            relacion.claseAsociacionId().toString()
                    );

                } else {
                    datos.putNull("claseAsociacionId");
                }
            }
            case "ELIMINAR_RELACION" -> {
                tipoColaborativo = TipoOperacionColaborativa.ELIMINAR_RELACION;
                RelacionDiagramaResponse relacion = buscarRelacion(modelo, operacion);
                datos.put("relacionId", relacion.id().toString());
            }
            default -> throw new IllegalArgumentException(
                    "Operación de IA no soportada: " + tipo
            );
        }

        return new OperacionColaborativaRequest(
                UUID.randomUUID(),
                clienteId,
                tipoColaborativo,
                datos
        );
    }

    private String normalizarTipoRelacion(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return "ASOCIACION";
        }

        String normalizado = tipo.trim().toUpperCase(Locale.ROOT);
        return switch (normalizado) {
            case "GENERALIZACION" -> "HERENCIA";
            default -> normalizado;
        };
    }

    private ClaseDiagramaResponse buscarClase(
            ModeloCompletoResponse modelo,
            String nombre
    ) {
        String buscado = obligatorio(nombre, "Falta indicar la clase");

        return modelo.clases()
                .stream()
                .filter(clase -> clase.nombre().equalsIgnoreCase(buscado))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la clase UML: " + buscado
                ));
    }

    private AtributoDiagramaResponse buscarAtributo(
            ClaseDiagramaResponse clase,
            String nombre
    ) {
        String buscado = obligatorio(nombre, "Falta indicar el atributo");

        return clase.atributos()
                .stream()
                .filter(atributo -> atributo.nombre().equalsIgnoreCase(buscado))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el atributo "
                                + buscado
                                + " en la clase "
                                + clase.nombre()
                ));
    }

    private RelacionDiagramaResponse buscarRelacion(
            ModeloCompletoResponse modelo,
            OperacionIa operacion
    ) {
        List<RelacionDiagramaResponse> candidatas = modelo.relaciones()
                .stream()
                .filter(relacion -> coincideNombreRelacion(relacion, operacion.nombreRelacion()))
                .filter(relacion -> coincideExtremos(
                        modelo,
                        relacion,
                        operacion.claseOrigen(),
                        operacion.claseDestino()
                ))
                .toList();

        if (candidatas.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se encontró la relación UML indicada"
            );
        }

        if (candidatas.size() > 1
                && tieneTexto(operacion.claseAsociacion())) {

            ClaseDiagramaResponse claseAsociacion =
                    buscarClaseOpcional(
                            modelo,
                            operacion.claseAsociacion()
                    );

            if (claseAsociacion != null) {
                List<RelacionDiagramaResponse> mismaClaseAsociacion =
                        candidatas.stream()
                                .filter(relacion ->
                                        relacion.claseAsociacionId() != null
                                                && relacion.claseAsociacionId()
                                                .equals(claseAsociacion.id())
                                )
                                .toList();

                if (mismaClaseAsociacion.size() == 1) {
                    return mismaClaseAsociacion.get(0);
                }
            }

            List<RelacionDiagramaResponse> relacionesBase =
                    candidatas.stream()
                            .filter(relacion ->
                                    relacion.claseAsociacionId() == null
                            )
                            .toList();

            if (relacionesBase.size() == 1) {
                return relacionesBase.get(0);
            }
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
                && relacion.nombre().equalsIgnoreCase(nombre.trim());
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

        ClaseDiagramaResponse claseOrigen = buscarClasePorId(modelo, relacion.claseOrigenId());
        ClaseDiagramaResponse claseDestino = buscarClasePorId(modelo, relacion.claseDestinoId());

        if (claseOrigen == null || claseDestino == null) {
            return false;
        }

        boolean mismoSentido =
                (origen == null
                        || origen.isBlank()
                        || claseOrigen.nombre()
                        .equalsIgnoreCase(origen.trim()))
                        && (destino == null
                        || destino.isBlank()
                        || claseDestino.nombre()
                        .equalsIgnoreCase(destino.trim()));

        boolean sentidoInverso =
                (origen == null
                        || origen.isBlank()
                        || claseDestino.nombre()
                        .equalsIgnoreCase(origen.trim()))
                        && (destino == null
                        || destino.isBlank()
                        || claseOrigen.nombre()
                        .equalsIgnoreCase(destino.trim()));

        return mismoSentido || sentidoInverso;
    }

    private ClaseDiagramaResponse buscarClasePorId(
            ModeloCompletoResponse modelo,
            UUID claseId
    ) {
        return modelo.clases()
                .stream()
                .filter(clase -> clase.id().equals(claseId))
                .findFirst()
                .orElse(null);
    }

    private double[] siguientePosicion(ModeloCompletoResponse modelo) {
        int indice = modelo.clases().size();
        int columna = indice % 4;
        int fila = indice / 4;

        return new double[]{
                80.0 + columna * 280.0,
                80.0 + fila * 220.0
        };
    }

    private void publicarOperacion(
            String codigoSesion,
            OperacionColaborativaResponse respuesta
    ) {
        String codigoNormalizado = codigoSesion.trim().toUpperCase(Locale.ROOT);

        messagingTemplate.convertAndSend(
                "/topic/sesiones/" + codigoNormalizado + "/operaciones",
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

        datos.put(campo, valor.trim());
    }

    private String obligatorio(
            String valor,
            String mensaje
    ) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
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
            return candidato.isBlank() ? null : candidato.trim();
        }

        return respaldo;
    }

    private String describirOperacion(OperacionIa operacion) {
        return switch (operacion.tipo()) {
            case "CREAR_CLASE" -> "Clase creada: " + operacion.clase();
            case "RENOMBRAR_CLASE" ->
                    "Clase renombrada: " + operacion.clase() + " → " + operacion.nuevoNombre();
            case "ELIMINAR_CLASE" -> "Clase eliminada: " + operacion.clase();
            case "CREAR_ATRIBUTO" ->
                    "Atributo creado: " + operacion.clase() + "." + operacion.atributo();
            case "ACTUALIZAR_ATRIBUTO" ->
                    "Atributo actualizado: " + operacion.clase() + "." + operacion.atributo();
            case "ELIMINAR_ATRIBUTO" ->
                    "Atributo eliminado: " + operacion.clase() + "." + operacion.atributo();
            case "CREAR_RELACION" ->
                    "Relación creada: " + operacion.claseOrigen() + " → " + operacion.claseDestino();
            case "ACTUALIZAR_RELACION" -> "Relación actualizada";
            case "ELIMINAR_RELACION" -> "Relación eliminada";
            default -> operacion.tipo();
        };
    }
}
