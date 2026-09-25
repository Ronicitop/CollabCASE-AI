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

        PlanIa plan =
                interpretadorOpenAiService.interpretar(
                        request.mensaje(),
                        modeloActual
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

        PlanIa plan =
                interpretadorOpenAiService.interpretarImagen(
                        archivo,
                        mensaje,
                        modeloActual
                );

        plan = normalizarPlanImagen(
                plan,
                modeloActual
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
                normalizarTiposAtributosImagen(
                        plan.operaciones()
                );

        operaciones =
                normalizarClasesAsociacionImagen(
                        operaciones,
                        modeloActual
                );

        return new PlanIa(
                plan.mensaje(),
                List.copyOf(operaciones)
        );
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

    private ChatIaResponse ejecutarPlan(
            String codigoSesion,
            String clienteId,
            PlanIa plan,
            ModeloCompletoResponse modeloActual
    ) {
        List<String> acciones = new ArrayList<>();

        if (plan.operaciones() != null) {
            for (OperacionIa operacion : plan.operaciones()) {
                if (debeOmitirCreacionDuplicada(operacion, modeloActual)) {
                    continue;
                }

                OperacionColaborativaRequest solicitud =
                        convertirOperacion(operacion, clienteId, modeloActual);

                OperacionColaborativaResponse respuesta =
                        operacionColaborativaService.aplicarOperacion(
                                codigoSesion,
                                solicitud
                        );

                modeloActual = respuesta.modelo();
                acciones.add(describirOperacion(operacion));
                publicarOperacion(codigoSesion, respuesta);
            }
        }

        return new ChatIaResponse(
                plan.mensaje(),
                List.copyOf(acciones),
                modeloActual
        );
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

        boolean coincideOrigen = origen == null
                || origen.isBlank()
                || claseOrigen.nombre().equalsIgnoreCase(origen.trim());

        boolean coincideDestino = destino == null
                || destino.isBlank()
                || claseDestino.nombre().equalsIgnoreCase(destino.trim());

        return coincideOrigen && coincideDestino;
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
