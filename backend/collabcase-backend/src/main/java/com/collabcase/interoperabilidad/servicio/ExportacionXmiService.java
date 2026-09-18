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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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

        escribirTiposDatos(
                xml,
                tipos
        );

        for (ClaseDiagramaResponse clase : modelo.clases()) {
            escribirClase(
                    xml,
                    clase,
                    tipos
            );
        }

        for (RelacionDiagramaResponse relacion : modelo.relaciones()) {
            escribirRelacion(
                    xml,
                    relacion
            );
        }

        xml.writeEndElement(); // uml:Model
        xml.writeEndElement(); // xmi:XMI
        xml.writeEndDocument();
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
            Map<String, String> tipos
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

        String relacionId =
                "CC_REL_" + limpiarUuid(relacion.id());

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

        if (relacion.nombre() != null
                && !relacion.nombre().isBlank()) {

            xml.writeAttribute(
                    "name",
                    relacion.nombre()
            );
        }

        xml.writeAttribute(
                "memberEnd",
                extremoOrigenId + " " + extremoDestinoId
        );

        escribirExtremoRelacion(
                xml,
                extremoOrigenId,
                relacionId,
                idClase(relacion.claseOrigenId()),
                relacion.multiplicidadOrigen()
        );

        escribirExtremoRelacion(
                xml,
                extremoDestinoId,
                relacionId,
                idClase(relacion.claseDestinoId()),
                relacion.multiplicidadDestino()
        );

        xml.writeEndElement();
    }

    private void escribirExtremoRelacion(
            XMLStreamWriter xml,
            String extremoId,
            String relacionId,
            String claseId,
            String multiplicidad
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

        escribirMultiplicidad(
                xml,
                multiplicidad,
                extremoId
        );

        xml.writeEndElement();
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
