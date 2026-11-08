package com.collabcase.proyecto.servicio;

import com.collabcase.colaboracion.dominio.SesionColaborativa;
import com.collabcase.colaboracion.repositorio.SesionColaborativaRepository;
import com.collabcase.modelado.dominio.AtributoDiagrama;
import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.dominio.RelacionDiagrama;
import com.collabcase.modelado.repositorio.AtributoDiagramaRepository;
import com.collabcase.modelado.repositorio.ClaseDiagramaRepository;
import com.collabcase.modelado.repositorio.ModeloDiagramaRepository;
import com.collabcase.modelado.repositorio.RelacionDiagramaRepository;
import com.collabcase.proyecto.dominio.Proyecto;
import com.collabcase.proyecto.repositorio.ProyectoRepository;
import org.springframework.stereotype.Service;
import com.collabcase.notificacion.NotificacionesService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProyectoService {

    private final NotificacionesService notificaciones;

    private final ProyectoRepository proyectoRepository;
    private final SesionColaborativaRepository sesionColaborativaRepository;
    private final ModeloDiagramaRepository modeloDiagramaRepository;
    private final ClaseDiagramaRepository claseDiagramaRepository;
    private final AtributoDiagramaRepository atributoDiagramaRepository;
    private final RelacionDiagramaRepository relacionDiagramaRepository;

    public ProyectoService(
            NotificacionesService notificaciones,
            ProyectoRepository proyectoRepository,
            SesionColaborativaRepository sesionColaborativaRepository,
            ModeloDiagramaRepository modeloDiagramaRepository,
            ClaseDiagramaRepository claseDiagramaRepository,
            AtributoDiagramaRepository atributoDiagramaRepository,
            RelacionDiagramaRepository relacionDiagramaRepository) {

        this.notificaciones = notificaciones;
        this.proyectoRepository = proyectoRepository;
        this.sesionColaborativaRepository = sesionColaborativaRepository;
        this.modeloDiagramaRepository = modeloDiagramaRepository;
        this.claseDiagramaRepository = claseDiagramaRepository;
        this.atributoDiagramaRepository = atributoDiagramaRepository;
        this.relacionDiagramaRepository = relacionDiagramaRepository;
    }

    public Proyecto crear(Proyecto proyecto) {
        var guardado = proyectoRepository.save(proyecto);
        notificaciones.proyecto(guardado.getId());
        return guardado;
    }

    public List<Proyecto> listarTodos() {
        return proyectoRepository.findAll();
    }

    public Proyecto buscarPorId(UUID id) {
        return proyectoRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Proyecto no encontrado con id: " + id
                        )
                );
    }

    public Proyecto actualizar(UUID id, Proyecto datosProyecto) {

        Proyecto proyecto = buscarPorId(id);

        proyecto.setNombre(datosProyecto.getNombre());
        proyecto.setDescripcion(datosProyecto.getDescripcion());

        var guardado = proyectoRepository.save(proyecto);
        notificaciones.proyecto(guardado.getId());
        return guardado;
    }

    @Transactional
    public void eliminar(UUID id) {

        Proyecto proyecto = buscarPorId(id);

        // 1. Eliminar sesiones colaborativas pertenecientes al proyecto.
        List<SesionColaborativa> sesiones =
                sesionColaborativaRepository.findByProyectoId(id);

        sesionColaborativaRepository.deleteAll(sesiones);

        // 2. Buscar el modelo UML del proyecto.
        modeloDiagramaRepository.findByProyectoId(id)
                .ifPresent(modelo -> eliminarModeloCompleto(modelo));

        // 3. Finalmente eliminar el proyecto.
        proyectoRepository.delete(proyecto);
        notificaciones.proyecto(id);
    }

    private void eliminarModeloCompleto(ModeloDiagrama modelo) {

        UUID modeloId = modelo.getId();

        // Las relaciones referencian clases, por eso deben eliminarse primero.
        List<RelacionDiagrama> relaciones =
                relacionDiagramaRepository.findByModeloId(modeloId);

        relacionDiagramaRepository.deleteAll(relaciones);

        // Después eliminamos los atributos de cada clase.
        List<ClaseDiagrama> clases =
                claseDiagramaRepository.findByModeloId(modeloId);

        for (ClaseDiagrama clase : clases) {

            List<AtributoDiagrama> atributos =
                    atributoDiagramaRepository.findByClaseId(clase.getId());

            atributoDiagramaRepository.deleteAll(atributos);
        }

        // Ya sin relaciones ni atributos podemos eliminar las clases.
        claseDiagramaRepository.deleteAll(clases);

        // Finalmente eliminamos el modelo.
        modeloDiagramaRepository.delete(modelo);
    }
}