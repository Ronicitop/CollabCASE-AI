package com.collabcase.interoperabilidad.servicio;

import com.collabcase.interoperabilidad.dto.ModeloImportadoXmi;
import com.collabcase.interoperabilidad.dto.ModeloImportadoXmi.AtributoImportado;
import com.collabcase.interoperabilidad.dto.ModeloImportadoXmi.ClaseImportada;
import com.collabcase.interoperabilidad.dto.ModeloImportadoXmi.RelacionImportada;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportacionXmiService {

    public ModeloImportadoXmi analizar(MultipartFile archivo) {

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe proporcionar un archivo XMI"
            );
        }

        try (InputStream entrada = archivo.getInputStream()) {

            Document documento =
                    crearDocumentBuilder()
                            .parse(entrada);

            documento.getDocumentElement().normalize();

            return interpretar(documento);

        } catch (ParserConfigurationException
                 | SAXException
                 | IOException ex) {

            throw new IllegalArgumentException(
                    "El archivo no contiene un XMI/XML válido",
                    ex
            );
        }
    }

    private DocumentBuilder crearDocumentBuilder()
            throws ParserConfigurationException {

        DocumentBuilderFactory fabrica =
                DocumentBuilderFactory.newInstance();

        fabrica.setNamespaceAware(true);
        fabrica.setXIncludeAware(false);
        fabrica.setExpandEntityReferences(false);

        fabrica.setFeature(
                XMLConstants.FEATURE_SECURE_PROCESSING,
                true
        );

        fabrica.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );

        fabrica.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        fabrica.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        fabrica.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false
        );

        return fabrica.newDocumentBuilder();
    }

    private ModeloImportadoXmi interpretar(Document documento) {

        List<Element> elementosEmpaquetados =
                buscarElementos(
                        documento.getDocumentElement(),
                        "packagedElement"
                );

        Map<String, String> tiposPorId =
                new LinkedHashMap<>();

        Map<String, String> clasesPorId =
                new LinkedHashMap<>();

        for (Element elemento : elementosEmpaquetados) {

            String tipo =
                    atributoXmi(elemento, "type");

            String id =
                    atributoXmi(elemento, "id");

            String nombre =
                    elemento.getAttribute("name");

            if ("uml:DataType".equals(tipo)
                    && !id.isBlank()
                    && !nombre.isBlank()) {

                tiposPorId.put(id, nombre);
            }

            if (esTipoClase(tipo)
                    && !id.isBlank()
                    && !nombre.isBlank()) {

                clasesPorId.put(id, nombre);
            }
        }

        Map<String, Posicion> posiciones =
                leerPosiciones(documento);

        List<ClaseImportada> clases =
                new ArrayList<>();

        for (Element elemento : elementosEmpaquetados) {

            if (!esTipoClase(
                    atributoXmi(elemento, "type")
            )) {
                continue;
            }

            String claseId =
                    atributoXmi(elemento, "id");

            String nombre =
                    elemento.getAttribute("name");

            List<AtributoImportado> atributos =
                    leerAtributos(
                            elemento,
                            tiposPorId,
                            clasesPorId
                    );

            Posicion posicion =
                    posiciones.get(claseId);

            clases.add(
                    new ClaseImportada(
                            claseId,
                            nombre,
                            posicion == null
                                    ? null
                                    : posicion.x(),
                            posicion == null
                                    ? null
                                    : posicion.y(),
                            atributos
                    )
            );
        }

        Map<String, ConectorEa> conectores =
                leerConectoresEa(documento);

        List<RelacionImportada> relaciones =
                new ArrayList<>();

        for (Element elemento : elementosEmpaquetados) {

            if (!"uml:Association".equals(
                    atributoXmi(elemento, "type")
            )) {
                continue;
            }

            String relacionId =
                    atributoXmi(elemento, "id");

            String nombre =
                    elemento.getAttribute("name");

            ConectorEa conector =
                    conectores.get(relacionId);

            RelacionImportada relacion;

            if (conector != null) {

                /*
                 * Enterprise Architect reexporta como "source"
                 * el extremo que CollabCASE había enviado como destino,
                 * y como "target" el extremo que había enviado como origen.
                 * Para conservar el round-trip usamos:
                 *
                 * target -> origen
                 * source -> destino
                 */
                relacion =
                        new RelacionImportada(
                                relacionId,
                                nombre,
                                resolverTipoRelacion(conector),
                                conector.targetId(),
                                conector.sourceId(),
                                normalizarMultiplicidad(
                                        conector.targetMultiplicidad()
                                ),
                                normalizarMultiplicidad(
                                        conector.sourceMultiplicidad()
                                ),
                                null
                        );

            } else {

                relacion =
                        leerRelacionUmlPura(
                                elemento,
                                relacionId,
                                nombre,
                                null
                        );
            }

            if (relacion != null) {
                relaciones.add(relacion);
            }
        }

        /*
         * Una uml:AssociationClass es simultáneamente una clase y una
         * asociación. La clase ya fue añadida arriba; aquí reconstruimos
         * su relación principal y conservamos la referencia a la propia
         * clase de asociación.
         */
        for (Element elemento : elementosEmpaquetados) {

            if (!"uml:AssociationClass".equals(
                    atributoXmi(elemento, "type")
            )) {
                continue;
            }

            String claseAsociacionId =
                    atributoXmi(elemento, "id");

            String nombreClaseAsociacion =
                    elemento.getAttribute("name");

            ConectorEa conector =
                    buscarConectorClaseAsociacion(
                            conectores,
                            claseAsociacionId
                    );

            RelacionImportada relacion;

            if (conector != null) {

                String nombreRelacion =
                        conector.nombre() == null
                                || conector.nombre().isBlank()
                                ? nombreClaseAsociacion
                                : conector.nombre();

                /*
                 * Enterprise Architect reexporta la Association Class con
                 * source/target invertidos respecto del sentido original
                 * usado por CollabCASE. Aplicamos la misma regla de
                 * round-trip que para las asociaciones normales.
                 */
                relacion =
                        new RelacionImportada(
                                claseAsociacionId,
                                nombreRelacion,
                                "ASOCIACION",
                                conector.targetId(),
                                conector.sourceId(),
                                normalizarMultiplicidad(
                                        conector.targetMultiplicidad()
                                ),
                                normalizarMultiplicidad(
                                        conector.sourceMultiplicidad()
                                ),
                                claseAsociacionId
                        );

            } else {

                relacion =
                        leerRelacionUmlPura(
                                elemento,
                                claseAsociacionId,
                                nombreClaseAsociacion,
                                claseAsociacionId
                        );
            }

            if (relacion != null) {
                relaciones.add(relacion);
            }
        }

        return new ModeloImportadoXmi(
                resolverNombreModelo(
                        documento,
                        elementosEmpaquetados
                ),
                List.copyOf(clases),
                List.copyOf(relaciones)
        );
    }

    private List<AtributoImportado> leerAtributos(
            Element clase,
            Map<String, String> tiposPorId,
            Map<String, String> clasesPorId
    ) {

        List<AtributoImportado> atributos =
                new ArrayList<>();

        for (Element atributo :
                hijosDirectos(
                        clase,
                        "ownedAttribute"
                )) {

            String atributoId =
                    atributoXmi(
                            atributo,
                            "id"
                    );

            String nombre =
                    atributo.getAttribute("name");

            String tipoId =
                    atributo.getAttribute("type");

            if (tipoId.isBlank()) {

                Element tipo =
                        primerHijoDirecto(
                                atributo,
                                "type"
                        );

                if (tipo != null) {
                    tipoId =
                            atributoXmi(
                                    tipo,
                                    "idref"
                            );
                }
            }

            String tipoDato =
                    tiposPorId.get(tipoId);

            if (tipoDato == null) {
                tipoDato =
                        clasesPorId.get(tipoId);
            }

            if (tipoDato == null
                    && !tipoId.isBlank()) {
                tipoDato = tipoId;
            }

            Boolean permiteNulo =
                    leerPermiteNulo(atributo);

            Boolean identificador =
                    leerIdentificador(atributo);

            atributos.add(
                    new AtributoImportado(
                            atributoId,
                            nombre,
                            tipoDato,
                            permiteNulo,
                            identificador
                    )
            );
        }

        return List.copyOf(atributos);
    }

    private Boolean leerPermiteNulo(
            Element atributo
    ) {

        Element lower =
                primerHijoDirecto(
                        atributo,
                        "lowerValue"
                );

        if (lower == null) {
            return null;
        }

        String valor =
                lower.getAttribute("value");

        if (valor.isBlank()) {
            return null;
        }

        return "0".equals(valor.trim());
    }

    private Boolean leerIdentificador(
            Element atributo
    ) {

        String valor =
                atributo.getAttribute("isID");

        if (valor.isBlank()) {
            valor =
                    atributo.getAttribute("isId");
        }

        if (valor.isBlank()) {
            return null;
        }

        return Boolean.parseBoolean(valor);
    }

    private RelacionImportada leerRelacionUmlPura(
            Element asociacion,
            String relacionId,
            String nombre,
            String claseAsociacionXmiId
    ) {

        List<Element> extremos =
                hijosDirectos(
                        asociacion,
                        "ownedEnd"
                );

        if (extremos.size() < 2) {
            return null;
        }

        Element origen =
                extremos.get(0);

        Element destino =
                extremos.get(1);

        return new RelacionImportada(
                relacionId,
                nombre,
                "ASOCIACION",
                leerTipoExtremo(origen),
                leerTipoExtremo(destino),
                leerMultiplicidadExtremo(origen),
                leerMultiplicidadExtremo(destino),
                claseAsociacionXmiId
        );
    }

    private String leerTipoExtremo(
            Element extremo
    ) {

        String tipo =
                extremo.getAttribute("type");

        if (!tipo.isBlank()) {
            return tipo;
        }

        Element elementoTipo =
                primerHijoDirecto(
                        extremo,
                        "type"
                );

        if (elementoTipo == null) {
            return null;
        }

        return atributoXmi(
                elementoTipo,
                "idref"
        );
    }

    private String leerMultiplicidadExtremo(
            Element extremo
    ) {

        Element lower =
                primerHijoDirecto(
                        extremo,
                        "lowerValue"
                );

        Element upper =
                primerHijoDirecto(
                        extremo,
                        "upperValue"
                );

        String inferior =
                lower == null
                        ? null
                        : lower.getAttribute("value");

        String superior =
                upper == null
                        ? null
                        : upper.getAttribute("value");

        return construirMultiplicidad(
                inferior,
                superior
        );
    }

    private Map<String, ConectorEa> leerConectoresEa(
            Document documento
    ) {

        Map<String, ConectorEa> conectores =
                new LinkedHashMap<>();

        for (Element conector :
                buscarElementos(
                        documento.getDocumentElement(),
                        "connector"
                )) {

            String id =
                    atributoXmi(
                            conector,
                            "idref"
                    );

            if (id.isBlank()) {
                continue;
            }

            Element source =
                    primerHijoDirecto(
                            conector,
                            "source"
                    );

            Element target =
                    primerHijoDirecto(
                            conector,
                            "target"
                    );

            if (source == null
                    || target == null) {
                continue;
            }

            Element sourceTipo =
                    primerHijoDirecto(
                            source,
                            "type"
                    );

            Element targetTipo =
                    primerHijoDirecto(
                            target,
                            "type"
                    );

            String sourceMultiplicidad =
                    sourceTipo == null
                            ? null
                            : sourceTipo.getAttribute(
                                    "multiplicity"
                            );

            String targetMultiplicidad =
                    targetTipo == null
                            ? null
                            : targetTipo.getAttribute(
                                    "multiplicity"
                            );

            String sourceAgregacion =
                    sourceTipo == null
                            ? null
                            : sourceTipo.getAttribute(
                                    "aggregation"
                            );

            String targetAgregacion =
                    targetTipo == null
                            ? null
                            : targetTipo.getAttribute(
                                    "aggregation"
                            );

            Element propiedadesExtendidas =
                    primerHijoDirecto(
                            conector,
                            "extendedProperties"
                    );

            String claseAsociacionId =
                    propiedadesExtendidas == null
                            ? null
                            : propiedadesExtendidas.getAttribute(
                                    "associationclass"
                            );

            conectores.put(
                    id,
                    new ConectorEa(
                            atributoXmi(
                                    source,
                                    "idref"
                            ),
                            atributoXmi(
                                    target,
                                    "idref"
                            ),
                            sourceMultiplicidad,
                            targetMultiplicidad,
                            sourceAgregacion,
                            targetAgregacion,
                            conector.getAttribute("name"),
                            claseAsociacionId
                    )
            );
        }

        return conectores;
    }

    private ConectorEa buscarConectorClaseAsociacion(
            Map<String, ConectorEa> conectores,
            String claseAsociacionId
    ) {

        if (claseAsociacionId == null
                || claseAsociacionId.isBlank()) {
            return null;
        }

        for (ConectorEa conector : conectores.values()) {
            if (claseAsociacionId.equals(
                    conector.claseAsociacionId()
            )) {
                return conector;
            }
        }

        return null;
    }

    private boolean esTipoClase(String tipo) {
        return "uml:Class".equals(tipo)
                || "uml:AssociationClass".equals(tipo);
    }

    private String resolverTipoRelacion(
            ConectorEa conector
    ) {

        if ("composite".equalsIgnoreCase(
                conector.sourceAgregacion()
        ) || "composite".equalsIgnoreCase(
                conector.targetAgregacion()
        )) {
            return "COMPOSICION";
        }

        if ("shared".equalsIgnoreCase(
                conector.sourceAgregacion()
        ) || "shared".equalsIgnoreCase(
                conector.targetAgregacion()
        )) {
            return "AGREGACION";
        }

        return "ASOCIACION";
    }

    private Map<String, Posicion> leerPosiciones(
            Document documento
    ) {

        Map<String, Posicion> posiciones =
                new LinkedHashMap<>();

        for (Element diagrams :
                buscarElementos(
                        documento.getDocumentElement(),
                        "diagrams"
                )) {

            for (Element diagram :
                    hijosDirectos(
                            diagrams,
                            "diagram"
                    )) {

                Element elementos =
                        primerHijoDirecto(
                                diagram,
                                "elements"
                        );

                if (elementos == null) {
                    continue;
                }

                for (Element elemento :
                        hijosDirectos(
                                elementos,
                                "element"
                        )) {

                    String subject =
                            elemento.getAttribute(
                                    "subject"
                            );

                    String geometry =
                            elemento.getAttribute(
                                    "geometry"
                            );

                    Posicion posicion =
                            parsearPosicion(geometry);

                    if (!subject.isBlank()
                            && posicion != null) {

                        posiciones.putIfAbsent(
                                subject,
                                posicion
                        );
                    }
                }
            }
        }

        return posiciones;
    }

    private Posicion parsearPosicion(
            String geometry
    ) {

        if (geometry == null
                || geometry.isBlank()
                || !geometry.contains("Left=")
                || !geometry.contains("Top=")) {
            return null;
        }

        Double izquierda =
                leerNumeroGeometry(
                        geometry,
                        "Left"
                );

        Double arriba =
                leerNumeroGeometry(
                        geometry,
                        "Top"
                );

        if (izquierda == null
                || arriba == null) {
            return null;
        }

        return new Posicion(
                izquierda,
                arriba
        );
    }

    private Double leerNumeroGeometry(
            String geometry,
            String clave
    ) {

        String marcador =
                clave + "=";

        int inicio =
                geometry.indexOf(marcador);

        if (inicio < 0) {
            return null;
        }

        inicio += marcador.length();

        int fin =
                geometry.indexOf(
                        ';',
                        inicio
                );

        if (fin < 0) {
            fin = geometry.length();
        }

        try {
            return Double.parseDouble(
                    geometry.substring(
                            inicio,
                            fin
                    ).trim()
            );

        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolverNombreModelo(
            Document documento,
            List<Element> elementosEmpaquetados
    ) {

        for (Element elemento :
                elementosEmpaquetados) {

            if ("uml:Package".equals(
                    atributoXmi(
                            elemento,
                            "type"
                    )
            )) {

                String nombre =
                        elemento.getAttribute("name");

                if (!nombre.isBlank()
                        && !"EA_PrimitiveTypes_Package"
                        .equals(nombre)) {
                    return nombre;
                }
            }
        }

        for (Element modelo :
                buscarElementos(
                        documento.getDocumentElement(),
                        "Model"
                )) {

            String nombre =
                    modelo.getAttribute("name");

            if (!nombre.isBlank()) {
                return nombre;
            }
        }

        return "Modelo importado";
    }

    private String normalizarMultiplicidad(
            String multiplicidad
    ) {

        if (multiplicidad == null
                || multiplicidad.isBlank()) {
            return null;
        }

        String valor =
                multiplicidad.trim()
                        .replace("-1", "*");

        if ("1..1".equals(valor)) {
            return "1";
        }

        if ("0..*".equals(valor)) {
            return "*";
        }

        return valor;
    }

    private String construirMultiplicidad(
            String inferior,
            String superior
    ) {

        if ((inferior == null
                || inferior.isBlank())
                && (superior == null
                || superior.isBlank())) {
            return null;
        }

        String min =
                inferior == null
                        ? ""
                        : inferior.trim();

        String max =
                superior == null
                        ? ""
                        : superior.trim()
                        .replace("-1", "*");

        if (min.equals(max)
                && !min.isBlank()) {
            return min;
        }

        if ("0".equals(min)
                && "*".equals(max)) {
            return "*";
        }

        return min + ".." + max;
    }

    private String atributoXmi(
            Element elemento,
            String nombreLocal
    ) {

        NamedNodeMap atributos =
                elemento.getAttributes();

        for (int i = 0;
             i < atributos.getLength();
             i++) {

            Node atributo =
                    atributos.item(i);

            String local =
                    atributo.getLocalName();

            if (nombreLocal.equals(local)
                    && ("xmi".equals(
                    atributo.getPrefix()
            ) || atributo.getNamespaceURI() != null
                    && atributo.getNamespaceURI()
                    .toLowerCase()
                    .contains("/xmi/"))) {

                return atributo.getNodeValue();
            }
        }

        String directo =
                elemento.getAttribute(
                        "xmi:" + nombreLocal
                );

        return directo == null
                ? ""
                : directo;
    }

    private List<Element> buscarElementos(
            Element raiz,
            String nombreLocal
    ) {

        List<Element> resultado =
                new ArrayList<>();

        recorrer(
                raiz,
                nombreLocal,
                resultado
        );

        return resultado;
    }

    private void recorrer(
            Element actual,
            String nombreLocal,
            List<Element> resultado
    ) {

        if (nombreLocal.equals(
                actual.getLocalName()
        ) || nombreLocal.equals(
                actual.getNodeName()
        )) {
            resultado.add(actual);
        }

        NodeList hijos =
                actual.getChildNodes();

        for (int i = 0;
             i < hijos.getLength();
             i++) {

            Node nodo =
                    hijos.item(i);

            if (nodo instanceof Element elemento) {
                recorrer(
                        elemento,
                        nombreLocal,
                        resultado
                );
            }
        }
    }

    private List<Element> hijosDirectos(
            Element padre,
            String nombreLocal
    ) {

        List<Element> resultado =
                new ArrayList<>();

        NodeList hijos =
                padre.getChildNodes();

        for (int i = 0;
             i < hijos.getLength();
             i++) {

            Node nodo =
                    hijos.item(i);

            if (nodo instanceof Element elemento
                    && (nombreLocal.equals(
                    elemento.getLocalName()
            ) || nombreLocal.equals(
                    elemento.getNodeName()
            ))) {

                resultado.add(elemento);
            }
        }

        return resultado;
    }

    private Element primerHijoDirecto(
            Element padre,
            String nombreLocal
    ) {

        List<Element> hijos =
                hijosDirectos(
                        padre,
                        nombreLocal
                );

        return hijos.isEmpty()
                ? null
                : hijos.get(0);
    }

    private record Posicion(
            Double x,
            Double y
    ) {
    }

    private record ConectorEa(
            String sourceId,
            String targetId,
            String sourceMultiplicidad,
            String targetMultiplicidad,
            String sourceAgregacion,
            String targetAgregacion,
            String nombre,
            String claseAsociacionId
    ) {
    }
}
