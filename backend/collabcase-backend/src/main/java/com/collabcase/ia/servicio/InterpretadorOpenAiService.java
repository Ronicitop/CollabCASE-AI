package com.collabcase.ia.servicio;

import com.collabcase.ia.dto.PlanIa;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Service
public class InterpretadorOpenAiService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelo;
    private final String baseUrl;

    public InterpretadorOpenAiService(
            ObjectMapper objectMapper,
            @Value("${AZURE_OPENAI_API_KEY:}") String apiKey,
            @Value("${AZURE_OPENAI_DEPLOYMENT:collabcase-gpt}") String modelo,
            @Value("${AZURE_OPENAI_BASE_URL:https://collabcase-openai-2026-05452.openai.azure.com/openai/v1/responses}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public PlanIa interpretar(
            String mensajeUsuario,
            ModeloCompletoResponse modeloActual
    ) {
        validarConfiguracion();

        ObjectNode cuerpo = crearBaseRequest();
        String contexto = objectMapper.writeValueAsString(modeloActual);

        cuerpo.put(
                "input",
                "RESPONDE ÚNICAMENTE CON JSON VÁLIDO.\n\n"
                        + "MODELO UML ACTUAL:\n"
                        + contexto
                        + "\n\nSOLICITUD DEL USUARIO:\n"
                        + mensajeUsuario
        );

        return ejecutarRequest(cuerpo);
    }

    public PlanIa interpretarImagen(
            MultipartFile archivo,
            String mensajeUsuario,
            ModeloCompletoResponse modeloActual
    ) {
        validarConfiguracion();

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debes enviar una imagen del diagrama UML"
            );
        }

        try {
            String contexto = objectMapper.writeValueAsString(modeloActual);
            String mimeType = contentTypeSeguro(archivo.getContentType());
            String base64 = Base64.getEncoder().encodeToString(archivo.getBytes());
            String mensaje = (mensajeUsuario == null || mensajeUsuario.isBlank())
                    ? "Interpreta el diagrama de clases de la imagen y genera las operaciones UML necesarias."
                    : mensajeUsuario.trim();

            ObjectNode cuerpo = crearBaseRequest();
            ArrayNode input = objectMapper.createArrayNode();
            ObjectNode mensajeUsuarioNodo = objectMapper.createObjectNode();
            mensajeUsuarioNodo.put("role", "user");

            ArrayNode contenido = objectMapper.createArrayNode();

            ObjectNode texto = objectMapper.createObjectNode();
            texto.put("type", "input_text");
            texto.put(
                    "text",
                    "RESPONDE ÚNICAMENTE CON JSON VÁLIDO.\n\n"
                            + "MODELO UML ACTUAL:\n"
                            + contexto
                            + "\n\nINSTRUCCIÓN DEL USUARIO:\n"
                            + mensaje
                            + "\n\n"
                            + "Analiza primero la TOPOLOGÍA VISUAL completa de la imagen y solo después compárala con el modelo actual. "
                            + "La imagen es la fuente principal para decidir qué clases están realmente conectadas, cuántos conectores existen y qué símbolo UML tiene cada conector. "
                            + "ANTES de generar operaciones, recorre visualmente cada conector desde un extremo hasta el otro, incluso si tiene varios giros ortogonales de 90 grados. "
                            + "No unas dos líneas solo porque se cruzan: un cruce sin punto de unión no crea una conexión UML. Tampoco pierdas una relación por tener varios segmentos o dobleces. "
                            + "CLASIFICACIÓN VISUAL OBLIGATORIA: triángulo hueco con línea continua = HERENCIA; triángulo hueco con línea discontinua = REALIZACION; "
                            + "diamante NEGRO/relleno = COMPOSICION; diamante BLANCO/hueco = AGREGACION; línea continua ordinaria, con o sin flecha simple de navegabilidad = ASOCIACION; "
                            + "línea discontinua con flecha abierta = DEPENDENCIA. Una flecha simple de asociación NUNCA debe confundirse con el triángulo hueco de herencia. "
                            + "El texto semántico del conector (por ejemplo contiene, posee, origen, destino) NO decide el tipo UML: el símbolo gráfico manda. "
                            + "ORIENTACIÓN PARA COLLABCASE: en COMPOSICION o AGREGACION, claseOrigen DEBE ser la clase pegada al diamante y claseDestino el otro extremo. "
                            + "En HERENCIA o REALIZACION, claseDestino DEBE ser la clase pegada al triángulo hueco y claseOrigen la clase del extremo opuesto. "
                            + "Asocia multiplicidadOrigen con el extremo de claseOrigen y multiplicidadDestino con el extremo de claseDestino; no las intercambies al cambiar la orientación. "
                            + "CONSERVA RELACIONES MÚLTIPLES: si existen dos o más conectores visualmente distintos entre las mismas clases, genera una operación CREAR_RELACION por cada conector. "
                            + "No fusiones conectores paralelos ni elimines uno solo porque comparten claseOrigen y claseDestino. Nombres distintos como origen y destino representan relaciones distintas. "
                            + "Los textos escritos junto, encima o cerca de un conector son nombres de relación si son legibles; cópialos exactamente en nombreRelacion y vincúlalos al conector visual correcto. "
                            + "Si un nombre no es legible, déjalo sin nombre; nunca lo sustituyas por ASOCIACION, COMPOSICION ni otro nombre técnico inventado. "
                            + "Si detectas clases, atributos, identificadores, multiplicidades o relaciones UML, conviértelos en operaciones válidas. "
                            + "REGLA CRÍTICA PARA ASSOCIATION CLASS: cuando una clase C está conectada por una línea discontinua al PUNTO MEDIO de una asociación sólida entre las clases A y B, "
                            + "los extremos de la relación principal son A y B; C NO es un extremo. Debes representar UNA SOLA relación A-B con claseAsociacion=C. "
                            + "NO crees A-C ni B-C por esa línea discontinua, salvo que exista además otro conector UML independiente claramente visible. "
                            + "Si el modelo actual contiene por error relaciones auxiliares A-C o B-C y la imagen muestra claramente una Association Class, "
                            + "genera primero ELIMINAR_RELACION para esas relaciones auxiliares y después CREAR_RELACION entre A y B con claseAsociacion=C. "
                            + "IGNORA POR COMPLETO LOS MÉTODOS/OPERACIONES de las clases (por ejemplo guardar(), buscar(), modificar()); no generes operaciones para ellos. "
                            + "Si un atributo es legible pero la imagen NO muestra su tipo de dato, DEBES inferir un tipo razonable y NUNCA devolver CREAR_ATRIBUTO con tipoDato vacío o nulo. "
                            + "Heurísticas de inferencia cuando el tipo no está escrito: nombres que empiezan por id_ o terminan en Id -> int; "
                            + "cantidad, stock, unidades, contador -> int; fecha o nombres que contienen fecha -> Date; "
                            + "precio, monto, total, subtotal, saldo, costo, importe -> Decimal; "
                            + "campos claramente lógicos como activo, habilitado, disponible -> Boolean; "
                            + "nombre, descripcion, dirección/direccion, teléfono/telefono y demás texto -> String. "
                            + "Para cualquier otro atributo cuyo tipo no pueda determinarse con seguridad, usa String como fallback técnico para que el modelo pueda crearse. "
                            + "Si un atributo comienza por id_, normalmente márcalo como identificador=true. "
                            + "La edición desde imagen debe ser AUTÓNOMA: no pidas al usuario que escriba tipos, relaciones ni multiplicidades si pueden inferirse de la imagen. "
                            + "Antes de responder, verifica que cada línea visible haya sido contabilizada exactamente una vez y que tipo, nombre y multiplicidades correspondan al mismo conector. "
                            + "Si la imagen es ambigua en aspectos no esenciales, conserva solo lo seguro y usa las inferencias anteriores para los tipos faltantes."
            );
            contenido.add(texto);

            ObjectNode imagen = objectMapper.createObjectNode();
            imagen.put("type", "input_image");
            imagen.put("image_url", "data:" + mimeType + ";base64," + base64);
            imagen.put("detail", "high");
            contenido.add(imagen);

            mensajeUsuarioNodo.set("content", contenido);
            input.add(mensajeUsuarioNodo);
            cuerpo.set("input", input);

            return ejecutarRequest(cuerpo);

        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo leer la imagen UML",
                    ex
            );
        }
    }

    private void validarConfiguracion() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar AZURE_OPENAI_API_KEY para usar la IA"
            );
        }
    }

    private ObjectNode crearBaseRequest() {
        ObjectNode cuerpo = objectMapper.createObjectNode();
        cuerpo.put("model", modelo);
        cuerpo.put("store", false);
        cuerpo.put("max_output_tokens", 4000);
        cuerpo.put("instructions", instruccionesSistema());

        ObjectNode text = objectMapper.createObjectNode();
        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_object");
        text.set("format", format);
        cuerpo.set("text", text);
        return cuerpo;
    }

    private PlanIa ejecutarRequest(ObjectNode cuerpo) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(90))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(cuerpo)
                            )
                    )
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Azure OpenAI respondió HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            JsonNode raiz = objectMapper.readTree(response.body());

            if ("incomplete".equals(texto(raiz, "status"))) {
                JsonNode detalles = raiz.get("incomplete_details");
                String razon = detalles == null ? null : texto(detalles, "reason");

                throw new IllegalStateException(
                        "Azure OpenAI devolvió una respuesta incompleta"
                                + (razon == null ? "" : " (" + razon + ")")
                );
            }

            String textoSalida = extraerTextoSalida(raiz);

            if (textoSalida == null || textoSalida.isBlank()) {
                throw new IllegalStateException(
                        "Azure OpenAI no devolvió un plan de operaciones UML"
                );
            }

            System.out.println("PLAN_IA_JSON=" + textoSalida);

            PlanIa plan = objectMapper.readValue(
                    textoSalida,
                    PlanIa.class
            );

            if (plan.operaciones() == null) {
                System.out.println(
                        "PLAN_IA_PARSEADO mensaje='"
                                + plan.mensaje()
                                + "' operaciones=0"
                );

                return new PlanIa(
                        plan.mensaje(),
                        List.of()
                );
            }

            System.out.println(
                    "PLAN_IA_PARSEADO mensaje='"
                            + plan.mensaje()
                            + "' operaciones="
                            + plan.operaciones().size()
            );

            for (int indice = 0; indice < plan.operaciones().size(); indice++) {
                System.out.println(
                        "PLAN_IA_OPERACION["
                                + indice
                                + "]="
                                + objectMapper.writeValueAsString(
                                        plan.operaciones().get(indice)
                                )
                );
            }

            return plan;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "La solicitud a Azure OpenAI fue interrumpida",
                    ex
            );
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo comunicar con Azure OpenAI",
                    ex
            );
        }
    }

    private String contentTypeSeguro(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }

        String normalizado = contentType.trim().toLowerCase();
        return switch (normalizado) {
            case "image/png", "image/jpeg", "image/jpg", "image/webp" ->
                    normalizado.replace("image/jpg", "image/jpeg");
            default -> "image/jpeg";
        };
    }

    private String extraerTextoSalida(JsonNode raiz) {
        JsonNode output = raiz.get("output");

        if (output == null || !output.isArray()) {
            return null;
        }

        for (JsonNode item : output) {
            if (!"message".equals(texto(item, "type"))) {
                continue;
            }

            JsonNode content = item.get("content");
            if (content == null || !content.isArray()) {
                continue;
            }

            for (JsonNode parte : content) {
                if ("output_text".equals(texto(parte, "type"))) {
                    return texto(parte, "text");
                }
            }
        }

        return null;
    }

    private String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);

        if (valor == null || valor.isNull()) {
            return null;
        }

        return valor.asString();
    }

    private String instruccionesSistema() {
        return """
                Eres el intérprete de comandos UML de CollabCASE AI.
                Devuelve únicamente un objeto JSON válido con esta forma:

                {
                  "mensaje": "resumen breve para el usuario",
                  "operaciones": [
                    {
                      "tipo": "CREAR_CLASE | RENOMBRAR_CLASE | ELIMINAR_CLASE | CREAR_ATRIBUTO | ACTUALIZAR_ATRIBUTO | ELIMINAR_ATRIBUTO | CREAR_RELACION | ACTUALIZAR_RELACION | ELIMINAR_RELACION",
                      "clase": null,
                      "nuevoNombre": null,
                      "atributo": null,
                      "nuevoNombreAtributo": null,
                      "tipoDato": null,
                      "permiteNulo": null,
                      "identificador": null,
                      "claseOrigen": null,
                      "claseDestino": null,
                      "claseAsociacion": null,
                      "tipoRelacion": null,
                      "multiplicidadOrigen": null,
                      "multiplicidadDestino": null,
                      "nombreRelacion": null,
                      "nuevoNombreRelacion": null
                    }
                  ]
                }

                Reglas:
                - Nunca inventes UUID ni IDs internos.
                - Usa nombres exactos de elementos existentes cuando los modifiques.
                - Si el usuario crea una clase con atributos, genera primero CREAR_CLASE y luego CREAR_ATRIBUTO.
                - Si no se especifica el tipo de relación, usa ASOCIACION.
                - Tipos de relación válidos preferidos: ASOCIACION, AGREGACION, COMPOSICION, HERENCIA, DEPENDENCIA, REALIZACION.
                - Las multiplicidades válidas usan formatos como 1, 0..1, * o 1..*.
                - En imágenes, clasifica el tipo por el SÍMBOLO UML, no por el verbo escrito junto a la línea: triángulo hueco + línea continua = HERENCIA; triángulo hueco + línea discontinua = REALIZACION; diamante negro = COMPOSICION; diamante blanco = AGREGACION; línea continua ordinaria con o sin flecha simple = ASOCIACION; línea discontinua con flecha abierta = DEPENDENCIA.
                - Una flecha simple de navegabilidad no es un triángulo de generalización y por sí sola no convierte una asociación en HERENCIA.
                - Para COMPOSICION y AGREGACION, claseOrigen debe ser el extremo donde está el diamante. Para HERENCIA y REALIZACION, claseDestino debe ser el extremo donde está el triángulo hueco.
                - multiplicidadOrigen corresponde siempre a claseOrigen y multiplicidadDestino a claseDestino; si reorientas los extremos para respetar el símbolo, reubica también las multiplicidades correctamente.
                - Traza cada conector completo aunque tenga giros ortogonales. Un cruce de líneas sin punto de unión no conecta relaciones distintas.
                - Si dos clases están unidas por dos o más conectores visualmente independientes, conserva TODOS: genera una relación por conector. No fusiones relaciones paralelas aunque tengan los mismos extremos.
                - Los nombres de relación distintos permiten coexistencia entre relaciones de las mismas clases; por ejemplo, dos conectores con nombres diferentes deben conservarse como dos relaciones diferentes.
                - Antes de finalizar un análisis de imagen, realiza una comprobación de cobertura: cada conector visible debe haberse contabilizado exactamente una vez, salvo las líneas auxiliares de Association Class.
                - REGLA CRÍTICA DE TOPOLOGÍA: no conviertas una línea discontinua de Association Class en una relación normal hacia la clase asociativa.
                - Si una clase C se conecta mediante línea discontinua al punto medio de una asociación sólida A-B, C es claseAsociacion; la relación principal sigue siendo A-B.
                - Para ese patrón genera una única relación principal A-B con claseAsociacion=C. No generes A-C ni B-C salvo que exista además un conector UML independiente claramente visible.
                - Si detectas una Association Class / clase de asociación, crea primero esa clase y sus atributos, y representa después la asociación principal con claseAsociacion igual al nombre exacto de esa clase.
                - Si el modelo actual contiene relaciones auxiliares incorrectas hacia la clase asociativa y la imagen deja claro el patrón A-B + línea discontinua a C, corrige el modelo: elimina las relaciones auxiliares incorrectas y crea la relación principal correcta con claseAsociacion=C.
                - En ELIMINAR_RELACION puedes usar claseOrigen y claseDestino para identificar una relación única aunque no tenga nombre.
                - Conserva exactamente los nombres visibles de las relaciones cuando sean legibles en la imagen (por ejemplo: solicita, pertenece, tiene); no los reemplaces por nombres técnicos inventados.
                - El texto cercano al centro de un conector debe interpretarse como posible nombre de relación; no lo ignores si es legible.
                - En análisis de imágenes IGNORA métodos/operaciones UML dentro de las clases; CollabCASE AI solo modelará clases, atributos y relaciones.
                - En CREAR_ATRIBUTO, tipoDato es OBLIGATORIO. Nunca generes CREAR_ATRIBUTO con tipoDato nulo, vacío u omitido.
                - Si la imagen no muestra el tipo de un atributo, infiérelo: id_* -> int; cantidad/stock/unidades/contador -> int; fecha* -> Date; precio/monto/total/subtotal/saldo/costo/importe -> Decimal; activo/habilitado/disponible -> Boolean; texto descriptivo -> String; cualquier otro caso -> String.
                - Cuando un atributo tenga forma id_*, usa identificador=true salvo evidencia visual en contrario.
                - La edición desde imagen debe funcionar sin instrucciones adicionales del usuario cuando la información visual sea suficiente.
                - No modifiques elementos que el usuario no pidió.
                - Si la solicitud o la imagen es ambigua, imposible o no corresponde al modelado UML, devuelve operaciones vacías o solo operaciones seguras y explícalo en mensaje.
                - Para ACTUALIZAR_ATRIBUTO, atributo es el nombre actual y nuevoNombreAtributo solo se usa si cambia.
                - Para ACTUALIZAR_RELACION, nombreRelacion identifica la relación actual y nuevoNombreRelacion solo se usa si cambia.
                - Omite del JSON los campos que no se utilicen en cada operación; no rellenes campos irrelevantes con null.
                - Mantén el JSON compacto para evitar respuestas innecesariamente largas.
                - Prioriza precisión sobre cantidad de operaciones.
                """;
    }
}
