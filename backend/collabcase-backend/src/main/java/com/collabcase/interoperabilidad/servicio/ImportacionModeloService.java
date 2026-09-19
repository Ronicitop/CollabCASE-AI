package com.collabcase.interoperabilidad.servicio;

import com.collabcase.interoperabilidad.dto.ModeloImportadoXmi;
import com.collabcase.modelado.dto.*;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ImportacionModeloService {

    private final ImportacionXmiService importacionXmiService;
    private final ModeloDiagramaService modeloDiagramaService;

    public ModeloCompletoResponse importarEnProyecto(
            UUID proyectoId,
            MultipartFile archivo
    ) {
        ModeloImportadoXmi importado = importacionXmiService.analizar(archivo);
        ModeloCompletoResponse actual =
                modeloDiagramaService.obtenerModeloCompleto(proyectoId);

        ModeloCompletoRequest request =
                convertir(importado, actual);

        return modeloDiagramaService.guardarModeloCompleto(
                proyectoId,
                request
        );
    }

    private ModeloCompletoRequest convertir(
            ModeloImportadoXmi importado,
            ModeloCompletoResponse actual
    ) {
        if (importado.clases() == null || importado.clases().isEmpty()) {
            throw new IllegalArgumentException(
                    "El archivo XMI no contiene clases UML"
            );
        }

        Map<String, ClaseDiagramaResponse> actualesPorNombre =
                new HashMap<>();

        for (ClaseDiagramaResponse clase : actual.clases()) {
            actualesPorNombre.putIfAbsent(
                    normalizar(clase.nombre()),
                    clase
            );
        }

        List<ClaseDiagramaRequest> clases = new ArrayList<>();
        Set<String> posicionesUsadas = new HashSet<>();

        int indice = 0;

        for (ModeloImportadoXmi.ClaseImportada importada
                : importado.clases()) {

            validarClase(importada);

            ClaseDiagramaResponse existente =
                    actualesPorNombre.get(
                            normalizar(importada.nombre())
                    );

            List<AtributoDiagramaRequest> atributos =
                    convertirAtributos(importada, existente);

            double[] posicion =
                    resolverPosicion(
                            importada,
                            existente,
                            indice,
                            posicionesUsadas
                    );

            clases.add(
                    new ClaseDiagramaRequest(
                            existente == null ? null : existente.id(),
                            importada.xmiId(),
                            importada.nombre(),
                            posicion[0],
                            posicion[1],
                            atributos
                    )
            );

            indice++;
        }

        List<RelacionDiagramaRequest> relaciones =
                new ArrayList<>();

        for (ModeloImportadoXmi.RelacionImportada relacion
                : importado.relaciones()) {

            validarRelacion(relacion);

            relaciones.add(
                    new RelacionDiagramaRequest(
                            null,
                            relacion.claseOrigenXmiId(),
                            relacion.claseDestinoXmiId(),
                            relacion.tipo(),
                            relacion.multiplicidadOrigen(),
                            relacion.multiplicidadDestino(),
                            vacioANull(relacion.nombre())
                    )
            );
        }

        return new ModeloCompletoRequest(
                List.copyOf(clases),
                List.copyOf(relaciones)
        );
    }

    private List<AtributoDiagramaRequest> convertirAtributos(
            ModeloImportadoXmi.ClaseImportada importada,
            ClaseDiagramaResponse existente
    ) {
        Map<String, AtributoDiagramaResponse> actuales =
                new HashMap<>();

        if (existente != null) {
            for (AtributoDiagramaResponse atributo
                    : existente.atributos()) {
                actuales.putIfAbsent(
                        normalizar(atributo.nombre()),
                        atributo
                );
            }
        }

        List<AtributoDiagramaRequest> resultado =
                new ArrayList<>();

        for (ModeloImportadoXmi.AtributoImportado atributo
                : importada.atributos()) {

            if (atributo.nombre() == null
                    || atributo.nombre().isBlank()) {
                throw new IllegalArgumentException(
                        "Existe un atributo sin nombre en la clase "
                                + importada.nombre()
                );
            }

            if (atributo.tipoDato() == null
                    || atributo.tipoDato().isBlank()) {
                throw new IllegalArgumentException(
                        "No se pudo determinar el tipo del atributo "
                                + atributo.nombre()
                );
            }

            AtributoDiagramaResponse actual =
                    actuales.get(
                            normalizar(atributo.nombre())
                    );

            boolean permiteNulo =
                    atributo.permiteNulo() != null
                            ? atributo.permiteNulo()
                            : actual != null
                            && Boolean.TRUE.equals(
                                    actual.permiteNulo()
                            );

            boolean identificador;

            if (atributo.identificador() != null) {
                identificador = atributo.identificador();
            } else if (actual != null) {
                identificador =
                        Boolean.TRUE.equals(
                                actual.identificador()
                        );
            } else {
                identificador = false;
            }

            resultado.add(
                    new AtributoDiagramaRequest(
                            actual == null ? null : actual.id(),
                            atributo.nombre(),
                            atributo.tipoDato(),
                            permiteNulo,
                            identificador
                    )
            );
        }

        return List.copyOf(resultado);
    }

    private double[] resolverPosicion(
            ModeloImportadoXmi.ClaseImportada importada,
            ClaseDiagramaResponse existente,
            int indice,
            Set<String> usadas
    ) {
        Double x = importada.posicionX();
        Double y = importada.posicionY();

        if ((x == null || y == null) && existente != null) {
            x = existente.posicionX();
            y = existente.posicionY();
        }

        if (x != null && y != null) {
            String clave = x + "|" + y;
            if (usadas.add(clave)) {
                return new double[]{x, y};
            }
        }

        double gx = 80.0 + (indice % 3) * 280.0;
        double gy = 80.0 + (indice / 3) * 220.0;

        while (!usadas.add(gx + "|" + gy)) {
            gx += 40.0;
            gy += 40.0;
        }

        return new double[]{gx, gy};
    }

    private void validarClase(
            ModeloImportadoXmi.ClaseImportada clase
    ) {
        if (clase.xmiId() == null || clase.xmiId().isBlank()) {
            throw new IllegalArgumentException(
                    "Existe una clase sin xmi:id"
            );
        }

        if (clase.nombre() == null || clase.nombre().isBlank()) {
            throw new IllegalArgumentException(
                    "Existe una clase sin nombre"
            );
        }

        if (clase.nombre().length() > 100) {
            throw new IllegalArgumentException(
                    "El nombre de clase supera 100 caracteres: "
                            + clase.nombre()
            );
        }
    }

    private void validarRelacion(
            ModeloImportadoXmi.RelacionImportada relacion
    ) {
        if (relacion.claseOrigenXmiId() == null
                || relacion.claseOrigenXmiId().isBlank()
                || relacion.claseDestinoXmiId() == null
                || relacion.claseDestinoXmiId().isBlank()) {
            throw new IllegalArgumentException(
                    "Existe una relación sin clases origen/destino"
            );
        }

        if (relacion.tipo() == null || relacion.tipo().isBlank()) {
            throw new IllegalArgumentException(
                    "Existe una relación sin tipo"
            );
        }
    }

    private String normalizar(String valor) {
        return valor == null
                ? ""
                : valor.trim().toLowerCase(Locale.ROOT);
    }

    private String vacioANull(String valor) {
        return valor == null || valor.isBlank()
                ? null
                : valor.trim();
    }
}
