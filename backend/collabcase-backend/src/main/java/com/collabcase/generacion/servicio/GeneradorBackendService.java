package com.collabcase.generacion.servicio;

import com.collabcase.generacion.dto.ArchivoGeneradoResponse;
import com.collabcase.generacion.dto.VistaPreviaBackendResponse;
import com.collabcase.modelado.dto.AtributoDiagramaResponse;
import com.collabcase.modelado.dto.ClaseDiagramaResponse;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.dto.RelacionDiagramaResponse;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeneradorBackendService {

    private static final String PAQUETE_BASE =
            "com.generado.backend";

    private final ModeloDiagramaService modeloDiagramaService;
    private final MapeadorTipoJava mapeadorTipoJava;
    private final NormalizadorNombreJava normalizadorNombreJava;

    public VistaPreviaBackendResponse generarVistaPrevia(
            UUID proyectoId
    ) {
        List<ArchivoGeneradoResponse> archivos =
                generarArchivos(proyectoId);

        return new VistaPreviaBackendResponse(
                proyectoId,
                archivos.size(),
                archivos
        );
    }

    public List<ArchivoGeneradoResponse> generarArchivos(
            UUID proyectoId
    ) {
        ModeloCompletoResponse modelo =
                modeloDiagramaService.obtenerModeloCompleto(
                        proyectoId
                );

        validarModelo(modelo);

        Map<UUID, ClaseDiagramaResponse> clasesPorId =
                indexarClases(modelo.clases());

        List<ArchivoGeneradoResponse> archivos =
                new ArrayList<>();

        for (ClaseDiagramaResponse clase : modelo.clases()) {
            archivos.addAll(
                    generarArchivosClase(
                            clase,
                            modelo.relaciones(),
                            clasesPorId
                    )
            );
        }

        return List.copyOf(archivos);
    }

    private List<ArchivoGeneradoResponse> generarArchivosClase(
            ClaseDiagramaResponse clase,
            List<RelacionDiagramaResponse> relaciones,
            Map<UUID, ClaseDiagramaResponse> clasesPorId
    ) {
        String nombreClase =
                normalizadorNombreJava.clase(
                        clase.nombre()
                );

        InfoId infoId =
                resolverIdentificador(clase);

        String rutaBase =
                "src/main/java/"
                        + PAQUETE_BASE.replace('.', '/')
                        + "/";

        return List.of(
                new ArchivoGeneradoResponse(
                        rutaBase
                                + "entidad/"
                                + nombreClase
                                + ".java",
                        generarEntidad(
                                clase,
                                nombreClase,
                                infoId,
                                relaciones,
                                clasesPorId
                        )
                ),
                new ArchivoGeneradoResponse(
                        rutaBase
                                + "repositorio/"
                                + nombreClase
                                + "Repository.java",
                        generarRepository(
                                nombreClase,
                                infoId
                        )
                ),
                new ArchivoGeneradoResponse(
                        rutaBase
                                + "servicio/"
                                + nombreClase
                                + "Service.java",
                        generarService(
                                nombreClase,
                                infoId
                        )
                ),
                new ArchivoGeneradoResponse(
                        rutaBase
                                + "controlador/"
                                + nombreClase
                                + "Controller.java",
                        generarController(
                                clase,
                                nombreClase,
                                infoId
                        )
                )
        );
    }

    private String generarEntidad(
            ClaseDiagramaResponse clase,
            String nombreClase,
            InfoId infoId,
            List<RelacionDiagramaResponse> relaciones,
            Map<UUID, ClaseDiagramaResponse> clasesPorId
    ) {
        Set<String> imports =
                new LinkedHashSet<>();

        imports.add("jakarta.persistence.*");
        imports.add("lombok.Getter");
        imports.add("lombok.NoArgsConstructor");
        imports.add("lombok.Setter");

        if (infoId.tecnico()) {
            imports.add("java.util.UUID");
        }

        for (AtributoDiagramaResponse atributo :
                clase.atributos()) {

            String tipoJava =
                    mapeadorTipoJava.mapear(
                            atributo.tipoDato()
                    );

            String importTipo =
                    mapeadorTipoJava.importNecesario(
                            tipoJava
                    );

            if (importTipo != null) {
                imports.add(importTipo);
            }
        }

        List<CampoRelacion> camposRelacion =
                construirCamposRelacion(
                        clase,
                        relaciones,
                        clasesPorId
                );

        if (camposRelacion.stream()
                .anyMatch(CampoRelacion::coleccion)) {
            imports.add("java.util.LinkedHashSet");
            imports.add("java.util.Set");
        }

        StringBuilder codigo =
                new StringBuilder();

        codigo.append("package ")
                .append(PAQUETE_BASE)
                .append(".entidad;\n\n");

        for (String importacion : imports) {
            codigo.append("import ")
                    .append(importacion)
                    .append(";\n");
        }

        codigo.append("\n@Entity\n")
                .append("@Table(name = \"")
                .append(
                        normalizadorNombreJava.tabla(
                                clase.nombre()
                        )
                )
                .append("\")\n")
                .append("@Getter\n")
                .append("@Setter\n")
                .append("@NoArgsConstructor\n")
                .append("public class ")
                .append(nombreClase)
                .append(" {\n\n");

        if (infoId.tecnico()) {
            codigo.append("    // Identificador tecnico porque el modelo no declaro uno.\n")
                    .append("    @Id\n")
                    .append("    @GeneratedValue(strategy = GenerationType.UUID)\n")
                    .append("    @Column(name = \"id_tecnico\", nullable = false)\n")
                    .append("    private UUID idTecnico;\n\n");
        }

        for (AtributoDiagramaResponse atributo :
                clase.atributos()) {

            String tipoJava =
                    mapeadorTipoJava.mapear(
                            atributo.tipoDato()
                    );

            String nombreCampo =
                    normalizadorNombreJava.campo(
                            atributo.nombre()
                    );

            if (Boolean.TRUE.equals(
                    atributo.identificador()
            )) {
                codigo.append("    @Id\n");

                if ("UUID".equals(tipoJava)) {
                    codigo.append("    @GeneratedValue(strategy = GenerationType.UUID)\n");
                } else if ("Long".equals(tipoJava)
                        || "Integer".equals(tipoJava)) {
                    codigo.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
                }
            }

            codigo.append("    @Column(nullable = ")
                    .append(
                            Boolean.TRUE.equals(
                                    atributo.permiteNulo()
                            )
                    )
                    .append(")\n")
                    .append("    private ")
                    .append(tipoJava)
                    .append(" ")
                    .append(nombreCampo)
                    .append(";\n\n");
        }

        for (CampoRelacion campo : camposRelacion) {
            codigo.append(campo.codigo())
                    .append("\n");
        }

        codigo.append("}\n");

        return codigo.toString();
    }

    private List<CampoRelacion> construirCamposRelacion(
            ClaseDiagramaResponse claseActual,
            List<RelacionDiagramaResponse> relaciones,
            Map<UUID, ClaseDiagramaResponse> clasesPorId
    ) {
        List<CampoRelacion> resultado =
                new ArrayList<>();

        Set<String> nombresCampo =
                new LinkedHashSet<>();

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
                throw new IllegalArgumentException(
                        "Una relacion referencia una clase inexistente"
                );
            }

            boolean origenMuchos =
                    esMuchos(
                            relacion.multiplicidadOrigen()
                    );

            boolean destinoMuchos =
                    esMuchos(
                            relacion.multiplicidadDestino()
                    );

            /*
             * Para evitar ciclos JSON y mantener un backend simple y compilable,
             * se genera una relacion JPA unidireccional en el lado propietario:
             *
             * 1 -> *  : @ManyToOne en la clase destino
             * * -> 1  : @ManyToOne en la clase origen
             * 1 -> 1  : @OneToOne en la clase origen
             * * -> *  : @ManyToMany en la clase origen
             */
            if (!origenMuchos && destinoMuchos) {

                if (!claseActual.id().equals(destino.id())) {
                    continue;
                }

                String campo =
                        nombreCampoRelacion(
                                origen,
                                relacion
                        );

                validarCampoRelacionUnico(
                        nombresCampo,
                        campo,
                        claseActual
                );

                boolean obligatorio =
                        esObligatorio(
                                relacion.multiplicidadOrigen()
                        );

                resultado.add(
                        new CampoRelacion(
                                campo,
                                false,
                                generarManyToOne(
                                        origen,
                                        campo,
                                        obligatorio
                                )
                        )
                );

            } else if (origenMuchos && !destinoMuchos) {

                if (!claseActual.id().equals(origen.id())) {
                    continue;
                }

                String campo =
                        nombreCampoRelacion(
                                destino,
                                relacion
                        );

                validarCampoRelacionUnico(
                        nombresCampo,
                        campo,
                        claseActual
                );

                boolean obligatorio =
                        esObligatorio(
                                relacion.multiplicidadDestino()
                        );

                resultado.add(
                        new CampoRelacion(
                                campo,
                                false,
                                generarManyToOne(
                                        destino,
                                        campo,
                                        obligatorio
                                )
                        )
                );

            } else if (!origenMuchos) {

                if (!claseActual.id().equals(origen.id())) {
                    continue;
                }

                String campo =
                        nombreCampoRelacion(
                                destino,
                                relacion
                        );

                validarCampoRelacionUnico(
                        nombresCampo,
                        campo,
                        claseActual
                );

                boolean obligatorio =
                        esObligatorio(
                                relacion.multiplicidadDestino()
                        );

                resultado.add(
                        new CampoRelacion(
                                campo,
                                false,
                                generarOneToOne(
                                        destino,
                                        campo,
                                        obligatorio
                                )
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

                validarCampoRelacionUnico(
                        nombresCampo,
                        campo,
                        claseActual
                );

                resultado.add(
                        new CampoRelacion(
                                campo,
                                true,
                                generarManyToMany(
                                        claseActual,
                                        destino,
                                        campo
                                )
                        )
                );
            }
        }

        return resultado;
    }

    private String generarManyToOne(
            ClaseDiagramaResponse claseReferenciada,
            String campo,
            boolean obligatorio
    ) {
        String tipo =
                normalizadorNombreJava.clase(
                        claseReferenciada.nombre()
                );

        String columna =
                normalizadorNombreJava.tabla(
                        campo
                ) + "_id";

        return """
                    @ManyToOne(optional = %s)
                    @JoinColumn(name = "%s", nullable = %s)
                    private %s %s;
                """.formatted(
                !obligatorio,
                columna,
                !obligatorio,
                tipo,
                campo
        );
    }

    private String generarOneToOne(
            ClaseDiagramaResponse claseReferenciada,
            String campo,
            boolean obligatorio
    ) {
        String tipo =
                normalizadorNombreJava.clase(
                        claseReferenciada.nombre()
                );

        String columna =
                normalizadorNombreJava.tabla(
                        campo
                ) + "_id";

        return """
                    @OneToOne(optional = %s)
                    @JoinColumn(name = "%s", nullable = %s, unique = true)
                    private %s %s;
                """.formatted(
                !obligatorio,
                columna,
                !obligatorio,
                tipo,
                campo
        );
    }

    private String generarManyToMany(
            ClaseDiagramaResponse origen,
            ClaseDiagramaResponse destino,
            String campo
    ) {
        String tipoDestino =
                normalizadorNombreJava.clase(
                        destino.nombre()
                );

        String tablaUnion =
                normalizadorNombreJava.tabla(
                        origen.nombre()
                                + "_"
                                + destino.nombre()
                                + "_"
                                + campo
                );

        String joinOrigen =
                normalizadorNombreJava.tabla(
                        origen.nombre()
                ) + "_id";

        String joinDestino =
                normalizadorNombreJava.tabla(
                        destino.nombre()
                ) + "_id";

        return """
                    @ManyToMany
                    @JoinTable(
                            name = "%s",
                            joinColumns = @JoinColumn(name = "%s"),
                            inverseJoinColumns = @JoinColumn(name = "%s")
                    )
                    private Set<%s> %s = new LinkedHashSet<>();
                """.formatted(
                tablaUnion,
                joinOrigen,
                joinDestino,
                tipoDestino,
                campo
        );
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

    private void validarCampoRelacionUnico(
            Set<String> nombres,
            String campo,
            ClaseDiagramaResponse clase
    ) {
        if (!nombres.add(campo)) {
            throw new IllegalArgumentException(
                    "Dos relaciones de la clase "
                            + clase.nombre()
                            + " producen el mismo campo Java: "
                            + campo
            );
        }
    }

    private boolean esMuchos(
            String multiplicidad
    ) {
        return multiplicidad != null
                && multiplicidad.contains("*");
    }

    private boolean esObligatorio(
            String multiplicidad
    ) {
        if (multiplicidad == null
                || multiplicidad.isBlank()) {
            return false;
        }

        String valor =
                multiplicidad.trim();

        if ("1".equals(valor)
                || "1..1".equals(valor)
                || valor.startsWith("1..")) {
            return true;
        }

        return false;
    }

    private String generarRepository(
            String nombreClase,
            InfoId infoId
    ) {
        String importId =
                generarImportTipoId(
                        infoId.tipoJava()
                );

        return """
                package %s.repositorio;

                import %s.entidad.%s;
                import org.springframework.data.jpa.repository.JpaRepository;
                %s
                public interface %sRepository
                        extends JpaRepository<%s, %s> {
                }
                """.formatted(
                PAQUETE_BASE,
                PAQUETE_BASE,
                nombreClase,
                importId,
                nombreClase,
                nombreClase,
                infoId.tipoJava()
        );
    }

    private String generarService(
            String nombreClase,
            InfoId infoId
    ) {
        String variable =
                normalizadorNombreJava.campo(
                        nombreClase
                );

        String setterId =
                "set"
                        + Character.toUpperCase(
                        infoId.campo().charAt(0)
                )
                        + infoId.campo().substring(1);

        String importId =
                generarImportTipoId(
                        infoId.tipoJava()
                );

        return """
                package %s.servicio;

                import %s.entidad.%s;
                import %s.repositorio.%sRepository;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                %s
                import java.util.List;

                @Service
                @RequiredArgsConstructor
                public class %sService {

                    private final %sRepository repository;

                    public List<%s> listar() {
                        return repository.findAll();
                    }

                    public %s obtener(%s id) {
                        return repository.findById(id)
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "%s no encontrado con id: " + id
                                        )
                                );
                    }

                    public %s crear(%s %s) {
                        return repository.save(%s);
                    }

                    public %s actualizar(
                            %s id,
                            %s %s
                    ) {
                        obtener(id);
                        %s.%s(id);
                        return repository.save(%s);
                    }

                    public void eliminar(%s id) {
                        obtener(id);
                        repository.deleteById(id);
                    }
                }
                """.formatted(
                PAQUETE_BASE,
                PAQUETE_BASE,
                nombreClase,
                PAQUETE_BASE,
                nombreClase,
                importId,
                nombreClase,
                nombreClase,
                nombreClase,
                nombreClase,
                infoId.tipoJava(),
                nombreClase,
                nombreClase,
                nombreClase,
                variable,
                variable,
                nombreClase,
                infoId.tipoJava(),
                nombreClase,
                variable,
                variable,
                setterId,
                variable,
                infoId.tipoJava()
        );
    }

    private String generarController(
            ClaseDiagramaResponse clase,
            String nombreClase,
            InfoId infoId
    ) {
        String variable =
                normalizadorNombreJava.campo(
                        nombreClase
                );

        String ruta =
                normalizadorNombreJava.ruta(
                        clase.nombre()
                );

        String importId =
                generarImportTipoId(
                        infoId.tipoJava()
                );

        return """
                package %s.controlador;

                import %s.entidad.%s;
                import %s.servicio.%sService;
                import lombok.RequiredArgsConstructor;
                import org.springframework.http.HttpStatus;
                import org.springframework.web.bind.annotation.*;
                %s
                import java.util.List;

                @RestController
                @RequestMapping("/api/%s")
                @RequiredArgsConstructor
                public class %sController {

                    private final %sService service;

                    @GetMapping
                    public List<%s> listar() {
                        return service.listar();
                    }

                    @GetMapping("/{id}")
                    public %s obtener(
                            @PathVariable %s id
                    ) {
                        return service.obtener(id);
                    }

                    @PostMapping
                    @ResponseStatus(HttpStatus.CREATED)
                    public %s crear(
                            @RequestBody %s %s
                    ) {
                        return service.crear(%s);
                    }

                    @PutMapping("/{id}")
                    public %s actualizar(
                            @PathVariable %s id,
                            @RequestBody %s %s
                    ) {
                        return service.actualizar(id, %s);
                    }

                    @DeleteMapping("/{id}")
                    @ResponseStatus(HttpStatus.NO_CONTENT)
                    public void eliminar(
                            @PathVariable %s id
                    ) {
                        service.eliminar(id);
                    }
                }
                """.formatted(
                PAQUETE_BASE,
                PAQUETE_BASE,
                nombreClase,
                PAQUETE_BASE,
                nombreClase,
                importId,
                ruta,
                nombreClase,
                nombreClase,
                nombreClase,
                nombreClase,
                infoId.tipoJava(),
                nombreClase,
                nombreClase,
                variable,
                variable,
                nombreClase,
                infoId.tipoJava(),
                nombreClase,
                variable,
                variable,
                infoId.tipoJava()
        );
    }

    private String generarImportTipoId(
            String tipoJava
    ) {
        String importacion =
                mapeadorTipoJava.importNecesario(
                        tipoJava
                );

        if (importacion == null) {
            return "";
        }

        return "import "
                + importacion
                + ";\n";
    }

    private InfoId resolverIdentificador(
            ClaseDiagramaResponse clase
    ) {
        List<AtributoDiagramaResponse> identificadores =
                clase.atributos()
                        .stream()
                        .filter(atributo ->
                                Boolean.TRUE.equals(
                                        atributo.identificador()
                                )
                        )
                        .toList();

        if (identificadores.size() > 1) {
            throw new IllegalArgumentException(
                    "La generacion todavia no soporta claves compuestas. Clase: "
                            + clase.nombre()
            );
        }

        if (identificadores.isEmpty()) {
            return new InfoId(
                    "UUID",
                    "idTecnico",
                    true
            );
        }

        AtributoDiagramaResponse identificador =
                identificadores.get(0);

        String tipoJava =
                mapeadorTipoJava.mapear(
                        identificador.tipoDato()
                );

        return new InfoId(
                tipoJava,
                normalizadorNombreJava.campo(
                        identificador.nombre()
                ),
                false
        );
    }

    private void validarModelo(
            ModeloCompletoResponse modelo
    ) {
        if (modelo.clases() == null
                || modelo.clases().isEmpty()) {
            throw new IllegalArgumentException(
                    "El modelo no contiene clases para generar"
            );
        }

        Set<String> nombresJava =
                new LinkedHashSet<>();

        for (ClaseDiagramaResponse clase :
                modelo.clases()) {

            String nombreJava =
                    normalizadorNombreJava.clase(
                            clase.nombre()
                    );

            if (!nombresJava.add(nombreJava)) {
                throw new IllegalArgumentException(
                        "Dos clases producen el mismo nombre Java: "
                                + nombreJava
                );
            }

            Set<String> camposJava =
                    new LinkedHashSet<>();

            for (AtributoDiagramaResponse atributo :
                    clase.atributos()) {

                mapeadorTipoJava.mapear(
                        atributo.tipoDato()
                );

                String campoJava =
                        normalizadorNombreJava.campo(
                                atributo.nombre()
                        );

                if (!camposJava.add(campoJava)) {
                    throw new IllegalArgumentException(
                            "Dos atributos de la clase "
                                    + clase.nombre()
                                    + " producen el mismo campo Java: "
                                    + campoJava
                    );
                }
            }
        }
    }

    private Map<UUID, ClaseDiagramaResponse> indexarClases(
            List<ClaseDiagramaResponse> clases
    ) {
        Map<UUID, ClaseDiagramaResponse> resultado =
                new LinkedHashMap<>();

        for (ClaseDiagramaResponse clase : clases) {
            resultado.put(
                    clase.id(),
                    clase
            );
        }

        return resultado;
    }

    private record InfoId(
            String tipoJava,
            String campo,
            boolean tecnico
    ) {
    }

    private record CampoRelacion(
            String nombre,
            boolean coleccion,
            String codigo
    ) {
    }
}
