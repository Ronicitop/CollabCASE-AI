package com.collabcase.generacion.servicio;

import com.collabcase.modelado.dto.AtributoDiagramaResponse;
import com.collabcase.modelado.dto.ClaseDiagramaResponse;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.dto.RelacionDiagramaResponse;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeneradorPostmanService {

    private final ModeloDiagramaService modeloDiagramaService;
    private final NormalizadorNombreJava normalizadorNombreJava;
    private final MapeadorTipoJava mapeadorTipoJava;

    public byte[] generarColeccion(UUID proyectoId) {

        ModeloCompletoResponse modelo =
                modeloDiagramaService.obtenerModeloCompleto(
                        proyectoId
                );

        if (modelo.clases() == null
                || modelo.clases().isEmpty()) {
            throw new IllegalArgumentException(
                    "El modelo no contiene clases para generar Postman"
            );
        }

        Map<UUID, ClaseDiagramaResponse> clasesPorId =
                new LinkedHashMap<>();

        for (ClaseDiagramaResponse clase : modelo.clases()) {
            clasesPorId.put(clase.id(), clase);
        }

        String json =
                construirColeccion(
                        proyectoId,
                        modelo,
                        clasesPorId
                );

        return json.getBytes(StandardCharsets.UTF_8);
    }

    private String construirColeccion(
            UUID proyectoId,
            ModeloCompletoResponse modelo,
            Map<UUID, ClaseDiagramaResponse> clasesPorId
    ) {

        StringBuilder json = new StringBuilder();

        json.append("{\n")
                .append("  \"info\": {\n")
                .append("    \"name\": \"CollabCASE AI - Backend ")
                .append(escaparJson(proyectoId.toString()))
                .append("\",\n")
                .append("    \"description\": \"Coleccion generada automaticamente desde el modelo UML canonico de CollabCASE AI.\",\n")
                .append("    \"schema\": \"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"\n")
                .append("  },\n");

        escribirVariables(json, modelo.clases());

        json.append(",\n")
                .append("  \"item\": [\n");

        for (int i = 0; i < modelo.clases().size(); i++) {

            ClaseDiagramaResponse clase =
                    modelo.clases().get(i);

            escribirFolderClase(
                    json,
                    clase,
                    modelo.relaciones(),
                    clasesPorId
            );

            if (i < modelo.clases().size() - 1) {
                json.append(",");
            }

            json.append("\n");
        }

        json.append("  ]\n")
                .append("}\n");

        return json.toString();
    }

    private void escribirVariables(
            StringBuilder json,
            List<ClaseDiagramaResponse> clases
    ) {

        json.append("  \"variable\": [\n")
                .append("    {\n")
                .append("      \"key\": \"baseUrl\",\n")
                .append("      \"value\": \"http://localhost:8081\"\n")
                .append("    }");

        for (ClaseDiagramaResponse clase : clases) {

            json.append(",\n")
                    .append("    {\n")
                    .append("      \"key\": \"")
                    .append(escaparJson(variableId(clase)))
                    .append("\",\n")
                    .append("      \"value\": \"\"\n")
                    .append("    }");
        }

        json.append("\n  ]");
    }

    private void escribirFolderClase(
            StringBuilder json,
            ClaseDiagramaResponse clase,
            List<RelacionDiagramaResponse> relaciones,
            Map<UUID, ClaseDiagramaResponse> clasesPorId
    ) {

        String nombre =
                normalizadorNombreJava.clase(
                        clase.nombre()
                );

        String ruta =
                normalizadorNombreJava.ruta(
                        clase.nombre()
                );

        String variableId =
                variableId(clase);

        String body =
                construirBodyEjemplo(
                        clase,
                        relaciones,
                        clasesPorId
                );

        json.append("    {\n")
                .append("      \"name\": \"")
                .append(escaparJson(nombre))
                .append("\",\n")
                .append("      \"item\": [\n");

        escribirRequestSimple(
                json,
                "Listar",
                "GET",
                "{{baseUrl}}/api/" + ruta,
                null,
                null
        );

        json.append(",\n");

        escribirRequestSimple(
                json,
                "Obtener por ID",
                "GET",
                "{{baseUrl}}/api/"
                        + ruta
                        + "/{{"
                        + variableId
                        + "}}",
                null,
                null
        );

        json.append(",\n");

        escribirRequestSimple(
                json,
                "Crear",
                "POST",
                "{{baseUrl}}/api/" + ruta,
                body,
                scriptGuardarId(
                        clase,
                        variableId
                )
        );

        json.append(",\n");

        escribirRequestSimple(
                json,
                "Actualizar",
                "PUT",
                "{{baseUrl}}/api/"
                        + ruta
                        + "/{{"
                        + variableId
                        + "}}",
                body,
                null
        );

        json.append(",\n");

        escribirRequestSimple(
                json,
                "Eliminar",
                "DELETE",
                "{{baseUrl}}/api/"
                        + ruta
                        + "/{{"
                        + variableId
                        + "}}",
                null,
                null
        );

        json.append("\n")
                .append("      ]\n")
                .append("    }");
    }

    private void escribirRequestSimple(
            StringBuilder json,
            String nombre,
            String metodo,
            String url,
            String body,
            List<String> script
    ) {

        json.append("        {\n")
                .append("          \"name\": \"")
                .append(escaparJson(nombre))
                .append("\",\n");

        if (script != null) {
            json.append("          \"event\": [\n")
                    .append("            {\n")
                    .append("              \"listen\": \"test\",\n")
                    .append("              \"script\": {\n")
                    .append("                \"type\": \"text/javascript\",\n")
                    .append("                \"exec\": [\n");

            for (int i = 0; i < script.size(); i++) {

                json.append("                  \"")
                        .append(escaparJson(script.get(i)))
                        .append("\"");

                if (i < script.size() - 1) {
                    json.append(",");
                }

                json.append("\n");
            }

            json.append("                ]\n")
                    .append("              }\n")
                    .append("            }\n")
                    .append("          ],\n");
        }

        json.append("          \"request\": {\n")
                .append("            \"method\": \"")
                .append(metodo)
                .append("\",\n");

        if (body != null) {
            json.append("            \"header\": [\n")
                    .append("              {\n")
                    .append("                \"key\": \"Content-Type\",\n")
                    .append("                \"value\": \"application/json\"\n")
                    .append("              }\n")
                    .append("            ],\n")
                    .append("            \"body\": {\n")
                    .append("              \"mode\": \"raw\",\n")
                    .append("              \"raw\": \"")
                    .append(escaparJson(body))
                    .append("\",\n")
                    .append("              \"options\": {\n")
                    .append("                \"raw\": {\n")
                    .append("                  \"language\": \"json\"\n")
                    .append("                }\n")
                    .append("              }\n")
                    .append("            },\n");
        } else {
            json.append("            \"header\": [],\n");
        }

        json.append("            \"url\": {\n")
                .append("              \"raw\": \"")
                .append(escaparJson(url))
                .append("\",\n")
                .append("              \"host\": [\"{{baseUrl}}\"],\n")
                .append("              \"path\": [");

        List<String> segmentos =
                segmentosUrl(url);

        for (int i = 0; i < segmentos.size(); i++) {

            json.append("\"")
                    .append(escaparJson(segmentos.get(i)))
                    .append("\"");

            if (i < segmentos.size() - 1) {
                json.append(", ");
            }
        }

        json.append("]\n")
                .append("            }\n")
                .append("          }\n")
                .append("        }");
    }

    private String construirBodyEjemplo(
            ClaseDiagramaResponse clase,
            List<RelacionDiagramaResponse> relaciones,
            Map<UUID, ClaseDiagramaResponse> clasesPorId
    ) {

        List<String> campos = new ArrayList<>();

        InfoId infoId =
                resolverIdentificador(clase);

        for (AtributoDiagramaResponse atributo :
                clase.atributos()) {

            String campo =
                    normalizadorNombreJava.campo(
                            atributo.nombre()
                    );

            boolean esId =
                    Boolean.TRUE.equals(
                            atributo.identificador()
                    );

            if (esId && infoId.generado()) {
                continue;
            }

            campos.add(
                    "  \""
                            + escaparJson(campo)
                            + "\": "
                            + ejemploValor(
                            atributo.tipoDato()
                    )
            );
        }

        campos.addAll(
                construirCamposRelacionPostman(
                        clase,
                        relaciones,
                        clasesPorId
                )
        );

        if (campos.isEmpty()) {
            return "{}";
        }

        return "{\n"
                + String.join(",\n", campos)
                + "\n}";
    }

    private List<String> construirCamposRelacionPostman(
            ClaseDiagramaResponse claseActual,
            List<RelacionDiagramaResponse> relaciones,
            Map<UUID, ClaseDiagramaResponse> clasesPorId
    ) {

        List<String> campos = new ArrayList<>();

        for (RelacionDiagramaResponse relacion : relaciones) {

            ClaseDiagramaResponse origen =
                    clasesPorId.get(
                            relacion.claseOrigenId()
                    );

            ClaseDiagramaResponse destino =
                    clasesPorId.get(
                            relacion.claseDestinoId()
                    );

            if (origen == null || destino == null) {
                continue;
            }

            /*
             * Una Association Class no se representa en Postman como una
             * relacion directa entre origen y destino.
             *
             * La clase de asociacion es la entidad intermedia y debe recibir
             * una referencia hacia cada extremo, igual que el backend
             * generado por GeneradorBackendService.
             */
            if (relacion.claseAsociacionId() != null) {

                ClaseDiagramaResponse claseAsociacion =
                        clasesPorId.get(
                                relacion.claseAsociacionId()
                        );

                if (claseAsociacion == null) {
                    continue;
                }

                if (claseActual.id().equals(
                        relacion.claseAsociacionId()
                )) {

                    String campoOrigen =
                            normalizadorNombreJava.campo(
                                    origen.nombre()
                            );

                    String campoDestino =
                            normalizadorNombreJava.campo(
                                    destino.nombre()
                            );

                    /*
                     * Caso especial: asociacion reflexiva.
                     */
                    if (campoOrigen.equals(campoDestino)) {
                        campoOrigen += "Origen";
                        campoDestino += "Destino";
                    }

                    campos.add(
                            referenciaRelacionConCampo(
                                    origen,
                                    campoOrigen
                            )
                    );

                    campos.add(
                            referenciaRelacionConCampo(
                                    destino,
                                    campoDestino
                            )
                    );
                }

                /*
                 * No generar tambien la relacion normal entre los extremos.
                 * La clase de asociacion ya representa ese vinculo.
                 */
                continue;
            }

            boolean origenMuchos =
                    esMuchos(
                            relacion.multiplicidadOrigen()
                    );

            boolean destinoMuchos =
                    esMuchos(
                            relacion.multiplicidadDestino()
                    );

            if (!origenMuchos && destinoMuchos) {

                if (!claseActual.id().equals(destino.id())) {
                    continue;
                }

                campos.add(
                        referenciaRelacion(
                                origen,
                                relacion
                        )
                );

            } else if (origenMuchos && !destinoMuchos) {

                if (!claseActual.id().equals(origen.id())) {
                    continue;
                }

                campos.add(
                        referenciaRelacion(
                                destino,
                                relacion
                        )
                );

            } else if (!origenMuchos) {

                if (!claseActual.id().equals(origen.id())) {
                    continue;
                }

                campos.add(
                        referenciaRelacion(
                                destino,
                                relacion
                        )
                );

            } else {

                if (!claseActual.id().equals(origen.id())) {
                    continue;
                }

                String campo =
                        nombreCampoRelacion(
                                destino,
                                relacion
                        );

                campos.add(
                        "  \""
                                + escaparJson(campo)
                                + "\": []"
                );
            }
        }

        return campos;
    }

    private String referenciaRelacion(
            ClaseDiagramaResponse claseReferenciada,
            RelacionDiagramaResponse relacion
    ) {

        String campo =
                nombreCampoRelacion(
                        claseReferenciada,
                        relacion
                );

        InfoId idRelacionado =
                resolverIdentificador(
                        claseReferenciada
                );

        return "  \""
                + escaparJson(campo)
                + "\": {\n"
                + "    \""
                + escaparJson(idRelacionado.campo())
                + "\": \"{{"
                + escaparJson(variableId(claseReferenciada))
                + "}}\"\n"
                + "  }";
    }

    private String referenciaRelacionConCampo(
            ClaseDiagramaResponse claseReferenciada,
            String campo
    ) {

        InfoId idRelacionado =
                resolverIdentificador(
                        claseReferenciada
                );

        return "  \""
                + escaparJson(campo)
                + "\": {\n"
                + "    \""
                + escaparJson(idRelacionado.campo())
                + "\": \"{{"
                + escaparJson(variableId(claseReferenciada))
                + "}}\"\n"
                + "  }";
    }

    private String nombreCampoRelacion(
            ClaseDiagramaResponse claseReferenciada,
            RelacionDiagramaResponse relacion
    ) {

        String base =
                claseReferenciada.nombre();

        if (relacion.nombre() != null
                && !relacion.nombre().isBlank()) {
            base += " " + relacion.nombre();
        }

        return normalizadorNombreJava.campo(base);
    }

    private List<String> scriptGuardarId(
            ClaseDiagramaResponse clase,
            String variable
    ) {

        InfoId infoId =
                resolverIdentificador(clase);

        return List.of(
                "pm.test(\"Respuesta exitosa\", function () {",
                "    pm.expect(pm.response.code).to.be.oneOf([200, 201]);",
                "});",
                "const json = pm.response.json();",
                "if (json && json[\""
                        + infoId.campo()
                        + "\"]) {",
                "    pm.collectionVariables.set(\""
                        + variable
                        + "\", json[\""
                        + infoId.campo()
                        + "\"]);",
                "}"
        );
    }

    private InfoId resolverIdentificador(
            ClaseDiagramaResponse clase
    ) {

        List<AtributoDiagramaResponse> ids =
                clase.atributos()
                        .stream()
                        .filter(atributo ->
                                Boolean.TRUE.equals(
                                        atributo.identificador()
                                )
                        )
                        .toList();

        if (ids.size() > 1) {
            throw new IllegalArgumentException(
                    "Postman no soporta claves compuestas. Clase: "
                            + clase.nombre()
            );
        }

        if (ids.isEmpty()) {
            return new InfoId(
                    "idTecnico",
                    true
            );
        }

        AtributoDiagramaResponse id =
                ids.get(0);

        String tipoJava =
                mapeadorTipoJava.mapear(
                        id.tipoDato()
                );

        boolean generado =
                "UUID".equals(tipoJava)
                        || "Long".equals(tipoJava)
                        || "Integer".equals(tipoJava);

        return new InfoId(
                normalizadorNombreJava.campo(
                        id.nombre()
                ),
                generado
        );
    }

    private String variableId(
            ClaseDiagramaResponse clase
    ) {
        return normalizadorNombreJava.campo(
                clase.nombre()
        ) + "Id";
    }

    private boolean esMuchos(
            String multiplicidad
    ) {
        return multiplicidad != null
                && multiplicidad.contains("*");
    }

    private String ejemploValor(
            String tipoDiagrama
    ) {

        String tipoJava =
                mapeadorTipoJava.mapear(
                        tipoDiagrama
                );

        return switch (tipoJava) {
            case "String" -> "\"ejemplo\"";
            case "UUID" ->
                    "\"00000000-0000-0000-0000-000000000001\"";
            case "Integer", "Short", "Byte", "Long" -> "1";
            case "BigDecimal", "Double", "Float" -> "1.0";
            case "Boolean" -> "false";
            case "LocalDate" -> "\"2026-01-01\"";
            case "LocalDateTime" ->
                    "\"2026-01-01T10:00:00\"";
            case "LocalTime" -> "\"10:00:00\"";
            default -> "\"ejemplo\"";
        };
    }

    private List<String> segmentosUrl(
            String url
    ) {

        String sinHost =
                url.replace(
                        "{{baseUrl}}/",
                        ""
                );

        return List.of(
                sinHost.split("/")
        );
    }

    private String escaparJson(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        return texto
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private record InfoId(
            String campo,
            boolean generado
    ) {
    }
}
