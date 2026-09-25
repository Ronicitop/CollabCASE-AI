package com.collabcase.interoperabilidad.servicio;

import com.collabcase.modelado.dto.AtributoDiagramaResponse;
import com.collabcase.modelado.dto.ClaseDiagramaResponse;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.dto.RelacionDiagramaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportacionXmiService {

    private static final String NS_XMI =
            "http://www.omg.org/spec/XMI/20131001";

    private static final String NS_UML =
            "http://www.omg.org/spec/UML/20131001";

    private final ExportacionModeloService exportacionModeloService;

    public byte[] exportarProyecto(UUID proyectoId) {

        ModeloCompletoResponse modelo =
                exportacionModeloService.obtenerModeloParaExportacion(
                        proyectoId
                );

        try {
            ByteArrayOutputStream salida =
                    new ByteArrayOutputStream();

            XMLStreamWriter xml =
                    XMLOutputFactory.newFactory()
                            .createXMLStreamWriter(
                                    salida,
                                    StandardCharsets.UTF_8.name()
                            );

            escribirDocumento(xml, modelo);

            xml.flush();
            xml.close();

            return salida.toByteArray();

        } catch (XMLStreamException ex) {
            throw new IllegalStateException(
                    "No se pudo generar el archivo XMI",
                    ex
            );
        }
    }

    private void escribirDocumento(
            XMLStreamWriter xml,
            ModeloCompletoResponse modelo
    ) throws XMLStreamException {

        xml.writeStartDocument(
                StandardCharsets.UTF_8.name(),
                "1.0"
        );

        xml.writeStartElement(
                "xmi",
                "XMI",
                NS_XMI
        );

        xml.writeNamespace("xmi", NS_XMI);
        xml.writeNamespace("uml", NS_UML);

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "version",
                "2.5.1"
        );

        String modeloXmiId =
                "CC_MODEL_"
                        + limpiarUuid(modelo.id());

        xml.writeStartElement(
                "uml",
                "Model",
                NS_UML
        );

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "id",
                modeloXmiId
        );

        xml.writeAttribute(
                "name",
                "CollabCASE_" + modelo.proyectoId()
        );

        Map<String, String> tipos =
                recolectarTipos(modelo);

        Map<UUID, ClaseDiagramaResponse> clasesPorId =
                construirMapaClases(modelo);

        Set<UUID> clasesAsociacion =
                obtenerIdsClasesAsociacion(modelo);

        escribirTiposDatos(
                xml,
                tipos
        );

        for (ClaseDiagramaResponse clase : modelo.clases()) {
            if (!clasesAsociacion.contains(clase.id())) {
                escribirClase(
                        xml,
                        clase,
                        tipos,
                        modelo
                );
            }
        }

        for (RelacionDiagramaResponse relacion : modelo.relaciones()) {

            if (relacion.claseAsociacionId() == null) {
                if (!"HERENCIA".equals(normalizarTipoRelacion(relacion.tipo()))) {
                    escribirRelacion(
                            xml,
                            relacion
                    );
                }
                continue;
            }

            ClaseDiagramaResponse claseAsociacion =
                    clasesPorId.get(
                            relacion.claseAsociacionId()
                    );

            if (claseAsociacion == null) {
                throw new IllegalStateException(
                        "La relación "
                                + relacion.id()
                                + " referencia una clase de asociación inexistente"
                );
            }

            escribirClaseAsociacion(
                    xml,
                    relacion,
                    claseAsociacion,
                    tipos
            );
        }

        xml.writeEndElement(); // uml:Model

        escribirExtensionEnterpriseArchitect(
                xml,
                modelo,
                clasesPorId
        );

        xml.writeEndElement(); // xmi:XMI
        xml.writeEndDocument();
    }

    private Map<UUID, ClaseDiagramaResponse> construirMapaClases(
            ModeloCompletoResponse modelo
    ) {

        Map<UUID, ClaseDiagramaResponse> clases =
                new HashMap<>();

        for (ClaseDiagramaResponse clase : modelo.clases()) {
            clases.put(
                    clase.id(),
                    clase
            );
        }

        return clases;
    }

    private Set<UUID> obtenerIdsClasesAsociacion(
            ModeloCompletoResponse modelo
    ) {

        Set<UUID> ids =
                new HashSet<>();

        for (RelacionDiagramaResponse relacion : modelo.relaciones()) {
            if (relacion.claseAsociacionId() == null) {
                continue;
            }

            if (!ids.add(relacion.claseAsociacionId())) {
                throw new IllegalStateException(
                        "Una clase no puede actuar como clase de asociación "
                                + "de más de una relación en esta exportación"
                );
            }
        }

        return ids;
    }

    private Map<String, String> recolectarTipos(
            ModeloCompletoResponse modelo
    ) {

        Map<String, String> tipos =
                new LinkedHashMap<>();

        for (ClaseDiagramaResponse clase : modelo.clases()) {
            for (AtributoDiagramaResponse atributo : clase.atributos()) {

                String tipo = atributo.tipoDato();

                if (tipo == null || tipo.isBlank()) {
                    continue;
                }

                boolean esNombreClase =
                        modelo.clases()
                                .stream()
                                .anyMatch(
                                        claseModelo ->
                                                claseModelo.nombre()
                                                        .equalsIgnoreCase(tipo)
                                );

                if (!esNombreClase) {
                    tipos.putIfAbsent(
                            tipo,
                            idTipoDato(tipo)
                    );
                }
            }
        }

        return tipos;
    }

    private void escribirTiposDatos(
            XMLStreamWriter xml,
            Map<String, String> tipos
    ) throws XMLStreamException {

        for (Map.Entry<String, String> tipo : tipos.entrySet()) {

            xml.writeStartElement("packagedElement");

            xml.writeAttribute(
                    "xmi",
                    NS_XMI,
                    "type",
                    "uml:DataType"
            );

            xml.writeAttribute(
                    "xmi",
                    NS_XMI,
                    "id",
                    tipo.getValue()
            );

            xml.writeAttribute(
                    "name",
                    tipo.getKey()
            );

            xml.writeEndElement();
        }
    }

    private void escribirClase(
            XMLStreamWriter xml,
            ClaseDiagramaResponse clase,
            Map<String, String> tipos,
            ModeloCompletoResponse modelo
    ) throws XMLStreamException {

        xml.writeStartElement("packagedElement");

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "type",
                "uml:Class"
        );

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "id",
                idClase(clase.id())
        );

        xml.writeAttribute(
                "name",
                clase.nombre()
        );

        for (AtributoDiagramaResponse atributo : clase.atributos()) {
            escribirAtributo(
                    xml,
                    atributo,
                    tipos
            );
        }

        /*
         * UML/XMI representa Generalization como un elemento propiedad de la
         * clase específica (la subclase), no como un packagedElement suelto.
         * En CollabCASE el origen es la clase hija y el destino la clase padre.
         */
        for (RelacionDiagramaResponse relacion : modelo.relaciones()) {
            if (relacion.claseAsociacionId() != null) {
                continue;
            }

            if (!"HERENCIA".equals(normalizarTipoRelacion(relacion.tipo()))) {
                continue;
            }

            if (!clase.id().equals(relacion.claseOrigenId())) {
                continue;
            }

            escribirGeneralizacion(
                    xml,
                    relacion
            );
        }

        xml.writeEndElement();
    }

    private void escribirClaseAsociacion(
            XMLStreamWriter xml,
            RelacionDiagramaResponse relacion,
            ClaseDiagramaResponse claseAsociacion,
            Map<String, String> tipos
    ) throws XMLStreamException {

        String claseAsociacionId =
                idClase(claseAsociacion.id());

        String extremoOrigenId =
                idExtremoClaseAsociacion(
                        relacion.id(),
                        "ORIGEN"
                );

        String extremoDestinoId =
                idExtremoClaseAsociacion(
                        relacion.id(),
                        "DESTINO"
                );

        xml.writeStartElement("packagedElement");

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "type",
                "uml:AssociationClass"
        );

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "id",
                claseAsociacionId
        );

        /*
         * UML define AssociationClass como un único elemento que es a la vez
         * clase y asociación. Por eso el nombre UML estándar es el nombre de
         * la clase asociativa. El nombre visible de la relación de CollabCASE
         * se preserva más abajo mediante la extensión de Enterprise Architect.
         */
        xml.writeAttribute(
                "name",
                claseAsociacion.nombre()
        );

        /*
         * IMPORTANTE: memberEnd es un atributo XML del packagedElement.
         * Debe escribirse ANTES de cualquier elemento hijo (ownedAttribute).
         * Si se escribe después de los atributos de la clase, XMLStreamWriter
         * lanza una excepción y la exportación falla precisamente cuando hay
         * una Association Class.
         */
        xml.writeAttribute(
                "memberEnd",
                extremoOrigenId + " " + extremoDestinoId
        );

        for (AtributoDiagramaResponse atributo : claseAsociacion.atributos()) {
            escribirAtributo(
                    xml,
                    atributo,
                    tipos
            );
        }

        escribirExtremoRelacion(
                xml,
                extremoOrigenId,
                claseAsociacionId,
                idClase(relacion.claseOrigenId()),
                relacion.multiplicidadOrigen(),
                "none"
        );

        escribirExtremoRelacion(
                xml,
                extremoDestinoId,
                claseAsociacionId,
                idClase(relacion.claseDestinoId()),
                relacion.multiplicidadDestino(),
                "none"
        );

        xml.writeEndElement();
    }

    private void escribirAtributo(
            XMLStreamWriter xml,
            AtributoDiagramaResponse atributo,
            Map<String, String> tipos
    ) throws XMLStreamException {

        xml.writeStartElement("ownedAttribute");

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "id",
                "CC_ATTR_" + limpiarUuid(atributo.id())
        );

        xml.writeAttribute(
                "name",
                atributo.nombre()
        );

        String tipoReferencia =
                resolverReferenciaTipo(
                        atributo.tipoDato(),
                        tipos
                );

        if (tipoReferencia != null) {
            xml.writeAttribute(
                    "type",
                    tipoReferencia
            );
        }

        if (Boolean.TRUE.equals(atributo.identificador())) {
            xml.writeAttribute(
                    "isID",
                    "true"
            );
        }

        if (Boolean.TRUE.equals(atributo.permiteNulo())) {
            escribirMultiplicidad(
                    xml,
                    "0..1",
                    "CC_ATTR_MULT_" + limpiarUuid(atributo.id())
            );
        }

        xml.writeEndElement();
    }

    private String resolverReferenciaTipo(
            String tipoDato,
            Map<String, String> tipos
    ) {

        if (tipoDato == null || tipoDato.isBlank()) {
            return null;
        }

        String idTipo = tipos.get(tipoDato);

        if (idTipo != null) {
            return idTipo;
        }

        return null;
    }

    private void escribirRelacion(
            XMLStreamWriter xml,
            RelacionDiagramaResponse relacion
    ) throws XMLStreamException {

        String tipo =
                normalizarTipoRelacion(
                        relacion.tipo()
                );

        switch (tipo) {
            case "DEPENDENCIA" ->
                    escribirRelacionDirigida(
                            xml,
                            relacion,
                            "uml:Dependency"
                    );

            case "REALIZACION" ->
                    escribirRelacionDirigida(
                            xml,
                            relacion,
                            "uml:Realization"
                    );

            case "HERENCIA" -> {
                /*
                 * Las generalizaciones se escriben dentro de la clase hija
                 * mediante escribirGeneralizacion(...).
                 */
            }

            case "AGREGACION", "COMPOSICION", "ASOCIACION" ->
                    escribirAsociacion(
                            xml,
                            relacion,
                            tipo
                    );

            default ->
                    escribirAsociacion(
                            xml,
                            relacion,
                            "ASOCIACION"
                    );
        }
    }

    private void escribirAsociacion(
            XMLStreamWriter xml,
            RelacionDiagramaResponse relacion,
            String tipo
    ) throws XMLStreamException {

        String relacionId =
                idRelacion(relacion.id());

        String extremoOrigenId =
                relacionId + "_ORIGEN";

        String extremoDestinoId =
                relacionId + "_DESTINO";

        xml.writeStartElement("packagedElement");

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "type",
                "uml:Association"
        );

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "id",
                relacionId
        );

        escribirNombreRelacion(
                xml,
                relacion
        );

        xml.writeAttribute(
                "memberEnd",
                extremoOrigenId + " " + extremoDestinoId
        );

        /*
         * Compatibilidad con Enterprise Architect:
         * CollabCASE dibuja el diamante en la clase origen. Al importar XMI,
         * Enterprise Architect representa el diamante en el extremo opuesto
         * al ownedEnd que lleva aggregation. Por eso colocamos shared/composite
         * en el ownedEnd DESTINO para que visualmente el diamante quede junto
         * a la clase ORIGEN, tal como se ve en CollabCASE.
         */
        String agregacionDestino =
                switch (tipo) {
                    case "AGREGACION" -> "shared";
                    case "COMPOSICION" -> "composite";
                    default -> "none";
                };

        escribirExtremoRelacion(
                xml,
                extremoOrigenId,
                relacionId,
                idClase(relacion.claseOrigenId()),
                relacion.multiplicidadOrigen(),
                "none"
        );

        escribirExtremoRelacion(
                xml,
                extremoDestinoId,
                relacionId,
                idClase(relacion.claseDestinoId()),
                relacion.multiplicidadDestino(),
                agregacionDestino
        );

        xml.writeEndElement();
    }

    private void escribirRelacionDirigida(
            XMLStreamWriter xml,
            RelacionDiagramaResponse relacion,
            String tipoXmi
    ) throws XMLStreamException {

        xml.writeStartElement("packagedElement");

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "type",
                tipoXmi
        );

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "id",
                idRelacion(relacion.id())
        );

        escribirNombreRelacion(
                xml,
                relacion
        );

        /*
         * Dependency y Realization son DirectedRelationship:
         * origen = client, destino = supplier.
         */
        xml.writeAttribute(
                "client",
                idClase(relacion.claseOrigenId())
        );

        xml.writeAttribute(
                "supplier",
                idClase(relacion.claseDestinoId())
        );

        xml.writeEndElement();
    }

    private void escribirGeneralizacion(
            XMLStreamWriter xml,
            RelacionDiagramaResponse relacion
    ) throws XMLStreamException {

        xml.writeStartElement("generalization");

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "type",
                "uml:Generalization"
        );

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "id",
                idRelacion(relacion.id())
        );

        xml.writeAttribute(
                "general",
                idClase(relacion.claseDestinoId())
        );

        xml.writeEndElement();
    }

    private void escribirNombreRelacion(
            XMLStreamWriter xml,
            RelacionDiagramaResponse relacion
    ) throws XMLStreamException {

        if (relacion.nombre() == null
                || relacion.nombre().isBlank()) {
            return;
        }

        xml.writeAttribute(
                "name",
                relacion.nombre()
        );
    }

    private void escribirExtremoRelacion(
            XMLStreamWriter xml,
            String extremoId,
            String relacionId,
            String claseId,
            String multiplicidad,
            String agregacion
    ) throws XMLStreamException {

        xml.writeStartElement("ownedEnd");

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "id",
                extremoId
        );

        xml.writeAttribute(
                "association",
                relacionId
        );

        xml.writeAttribute(
                "type",
                claseId
        );

        if (agregacion != null
                && !agregacion.isBlank()
                && !"none".equalsIgnoreCase(agregacion)) {

            xml.writeAttribute(
                    "aggregation",
                    agregacion
            );
        }

        escribirMultiplicidad(
                xml,
                multiplicidad,
                extremoId
        );

        xml.writeEndElement();
    }

    private String normalizarTipoRelacion(String tipo) {

        if (tipo == null || tipo.isBlank()) {
            return "ASOCIACION";
        }

        String valor =
                tipo.trim()
                        .toUpperCase(Locale.ROOT);

        return switch (valor) {
            case "ASSOCIATION", "ASOCIACIÓN", "ASOCIACION" ->
                    "ASOCIACION";

            case "AGGREGATION", "AGREGACIÓN", "AGREGACION" ->
                    "AGREGACION";

            case "COMPOSITION", "COMPOSICIÓN", "COMPOSICION" ->
                    "COMPOSICION";

            case "GENERALIZATION",
                 "GENERALIZACIÓN",
                 "GENERALIZACION",
                 "INHERITANCE",
                 "HERENCIA" ->
                    "HERENCIA";

            case "DEPENDENCY", "DEPENDENCIA" ->
                    "DEPENDENCIA";

            case "REALIZATION", "REALIZACIÓN", "REALIZACION" ->
                    "REALIZACION";

            default ->
                    "ASOCIACION";
        };
    }

    private void escribirExtensionEnterpriseArchitect(
            XMLStreamWriter xml,
            ModeloCompletoResponse modelo,
            Map<UUID, ClaseDiagramaResponse> clasesPorId
    ) throws XMLStreamException {

        boolean tieneClasesAsociacion =
                modelo.relaciones()
                        .stream()
                        .anyMatch(
                                relacion ->
                                        relacion.claseAsociacionId() != null
                        );

        if (!tieneClasesAsociacion) {
            return;
        }

        /*
         * Enterprise Architect guarda el nombre independiente del conector
         * y el vínculo gráfico con la AssociationClass dentro de xmi:Extension.
         * Esta sección reproduce los campos esenciales observados en un XMI
         * exportado por EA para que "tiene" y "DetalleVenta" puedan conservar
         * nombres distintos.
         */
        xml.writeStartElement(
                "xmi",
                "Extension",
                NS_XMI
        );

        xml.writeAttribute(
                "extender",
                "Enterprise Architect"
        );

        xml.writeAttribute(
                "extenderID",
                "6.5"
        );

        xml.writeStartElement("elements");

        for (RelacionDiagramaResponse relacion : modelo.relaciones()) {
            if (relacion.claseAsociacionId() == null) {
                continue;
            }

            ClaseDiagramaResponse claseAsociacion =
                    clasesPorId.get(
                            relacion.claseAsociacionId()
                    );

            if (claseAsociacion == null) {
                continue;
            }

            xml.writeStartElement("element");

            xml.writeAttribute(
                    "xmi",
                    NS_XMI,
                    "idref",
                    idClase(claseAsociacion.id())
            );

            xml.writeAttribute(
                    "xmi",
                    NS_XMI,
                    "type",
                    "uml:Class"
            );

            xml.writeAttribute(
                    "name",
                    claseAsociacion.nombre()
            );

            xml.writeAttribute(
                    "scope",
                    "public"
            );

            xml.writeEmptyElement("extendedProperties");
            xml.writeAttribute(
                    "conID",
                    idConectorEa(relacion.id())
            );

            xml.writeStartElement("links");

            xml.writeEmptyElement("Association");

            xml.writeAttribute(
                    "xmi",
                    NS_XMI,
                    "id",
                    idConectorEa(relacion.id())
            );

            xml.writeAttribute(
                    "start",
                    idClase(relacion.claseOrigenId())
            );

            xml.writeAttribute(
                    "end",
                    idClase(relacion.claseDestinoId())
            );

            xml.writeEndElement(); // links
            xml.writeEndElement(); // element
        }

        xml.writeEndElement(); // elements

        xml.writeStartElement("connectors");

        for (RelacionDiagramaResponse relacion : modelo.relaciones()) {
            if (relacion.claseAsociacionId() == null) {
                continue;
            }

            ClaseDiagramaResponse origen =
                    clasesPorId.get(
                            relacion.claseOrigenId()
                    );

            ClaseDiagramaResponse destino =
                    clasesPorId.get(
                            relacion.claseDestinoId()
                    );

            ClaseDiagramaResponse claseAsociacion =
                    clasesPorId.get(
                            relacion.claseAsociacionId()
                    );

            if (origen == null
                    || destino == null
                    || claseAsociacion == null) {
                continue;
            }

            escribirConectorEaClaseAsociacion(
                    xml,
                    relacion,
                    origen,
                    destino,
                    claseAsociacion
            );
        }

        xml.writeEndElement(); // connectors
        xml.writeEndElement(); // xmi:Extension
    }

    private void escribirConectorEaClaseAsociacion(
            XMLStreamWriter xml,
            RelacionDiagramaResponse relacion,
            ClaseDiagramaResponse origen,
            ClaseDiagramaResponse destino,
            ClaseDiagramaResponse claseAsociacion
    ) throws XMLStreamException {

        String nombre =
                relacion.nombre() == null
                        ? ""
                        : relacion.nombre();

        xml.writeStartElement("connector");

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "idref",
                idConectorEa(relacion.id())
        );

        if (!nombre.isBlank()) {
            xml.writeAttribute(
                    "name",
                    nombre
            );
        }

        escribirExtremoConectorEa(
                xml,
                "source",
                origen,
                relacion.multiplicidadOrigen()
        );

        escribirExtremoConectorEa(
                xml,
                "target",
                destino,
                relacion.multiplicidadDestino()
        );

        xml.writeEmptyElement("properties");
        xml.writeAttribute(
                "ea_type",
                "Association"
        );
        xml.writeAttribute(
                "subtype",
                "Class"
        );
        xml.writeAttribute(
                "direction",
                "Unspecified"
        );

        xml.writeEmptyElement("labels");
        xml.writeAttribute(
                "lb",
                valorOBlanco(relacion.multiplicidadOrigen())
        );
        xml.writeAttribute(
                "mt",
                nombre
        );
        xml.writeAttribute(
                "rb",
                valorOBlanco(relacion.multiplicidadDestino())
        );

        xml.writeEmptyElement("extendedProperties");
        xml.writeAttribute(
                "associationclass",
                idClase(claseAsociacion.id())
        );

        xml.writeEndElement(); // connector
    }

    private void escribirExtremoConectorEa(
            XMLStreamWriter xml,
            String elemento,
            ClaseDiagramaResponse clase,
            String multiplicidad
    ) throws XMLStreamException {

        xml.writeStartElement(elemento);

        xml.writeAttribute(
                "xmi",
                NS_XMI,
                "idref",
                idClase(clase.id())
        );

        xml.writeEmptyElement("model");
        xml.writeAttribute(
                "type",
                "Class"
        );
        xml.writeAttribute(
                "name",
                clase.nombre()
        );

        xml.writeEmptyElement("role");
        xml.writeAttribute(
                "visibility",
                "Public"
        );

        xml.writeEmptyElement("type");
        xml.writeAttribute(
                "multiplicity",
                valorOBlanco(multiplicidad)
        );
        xml.writeAttribute(
                "aggregation",
                "none"
        );

        xml.writeEndElement();
    }

    private String valorOBlanco(String valor) {
        return valor == null
                ? ""
                : valor;
    }

    private void escribirMultiplicidad(
            XMLStreamWriter xml,
            String multiplicidad,
            String prefijoId
    ) throws XMLStreamException {

        if (multiplicidad == null
                || multiplicidad.isBlank()) {
            return;
        }

        String texto =
                multiplicidad.trim();

        String inferior;
        String superior;

        if ("*".equals(texto)) {
            inferior = "0";
            superior = "*";
        } else if (texto.contains("..")) {

            String[] partes =
                    texto.split("\\.\\.", 2);

            inferior =
                    partes[0].trim();

            superior =
                    partes[1].trim();

        } else {
            inferior = texto;
            superior = texto;
        }

        if (!inferior.isBlank()) {
            xml.writeStartElement("lowerValue");

            xml.writeAttribute(
                    "xmi",
                    NS_XMI,
                    "type",
                    "uml:LiteralInteger"
            );

            xml.writeAttribute(
                    "xmi",
                    NS_XMI,
                    "id",
                    prefijoId + "_LOWER"
            );

            xml.writeAttribute(
                    "value",
                    inferior
            );

            xml.writeEndElement();
        }

        if (!superior.isBlank()) {
            xml.writeStartElement("upperValue");

            xml.writeAttribute(
                    "xmi",
                    NS_XMI,
                    "type",
                    "uml:LiteralUnlimitedNatural"
            );

            xml.writeAttribute(
                    "xmi",
                    NS_XMI,
                    "id",
                    prefijoId + "_UPPER"
            );

            xml.writeAttribute(
                    "value",
                    superior
            );

            xml.writeEndElement();
        }
    }

    private String idClase(UUID claseId) {
        return "CC_CLASS_" + limpiarUuid(claseId);
    }

    private String idRelacion(UUID relacionId) {
        return "CC_REL_" + limpiarUuid(relacionId);
    }

    private String idExtremoClaseAsociacion(
            UUID relacionId,
            String lado
    ) {
        return "CC_ASSOC_END_"
                + limpiarUuid(relacionId)
                + "_"
                + lado;
    }

    private String idConectorEa(UUID relacionId) {
        return "CC_EA_CONN_"
                + limpiarUuid(relacionId);
    }

    private String idTipoDato(String tipoDato) {

        UUID uuid =
                UUID.nameUUIDFromBytes(
                        tipoDato.toLowerCase(Locale.ROOT)
                                .getBytes(StandardCharsets.UTF_8)
                );

        return "CC_DATATYPE_" + limpiarUuid(uuid);
    }

    private String limpiarUuid(UUID uuid) {
        return uuid.toString()
                .replace("-", "");
    }
}
