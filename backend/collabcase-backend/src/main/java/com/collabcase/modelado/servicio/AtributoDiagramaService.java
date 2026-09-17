package com.collabcase.modelado.servicio;

import com.collabcase.modelado.dominio.AtributoDiagrama;
import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.repositorio.AtributoDiagramaRepository;
import com.collabcase.modelado.repositorio.ClaseDiagramaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AtributoDiagramaService {

    private final AtributoDiagramaRepository atributoDiagramaRepository;
    private final ClaseDiagramaRepository claseDiagramaRepository;

    public AtributoDiagramaService(
            AtributoDiagramaRepository atributoDiagramaRepository,
            ClaseDiagramaRepository claseDiagramaRepository) {

        this.atributoDiagramaRepository = atributoDiagramaRepository;
        this.claseDiagramaRepository = claseDiagramaRepository;
    }

    public AtributoDiagrama crear(
            UUID claseId,
            AtributoDiagrama datosAtributo) {

        ClaseDiagrama clase = claseDiagramaRepository.findById(claseId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Clase de diagrama no encontrada con id: " + claseId
                        )
                );

        if (atributoDiagramaRepository
                .findByClaseIdAndNombre(claseId, datosAtributo.getNombre())
                .isPresent()) {

            throw new IllegalStateException(
                    "Ya existe un atributo con el nombre: "
                            + datosAtributo.getNombre()
            );
        }

        AtributoDiagrama atributo = AtributoDiagrama.builder()
                .clase(clase)
                .nombre(datosAtributo.getNombre())
                .tipoDato(datosAtributo.getTipoDato())
                .permiteNulo(datosAtributo.getPermiteNulo())
                .identificador(datosAtributo.getIdentificador())
                .build();

        return atributoDiagramaRepository.save(atributo);
    }

    public List<AtributoDiagrama> listarPorClase(UUID claseId) {
        return atributoDiagramaRepository.findByClaseId(claseId);
    }

    public AtributoDiagrama buscarPorId(UUID id) {
        return atributoDiagramaRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Atributo de diagrama no encontrado con id: " + id
                        )
                );
    }

    public AtributoDiagrama actualizar(
            UUID id,
            AtributoDiagrama datosAtributo) {

        AtributoDiagrama atributo = buscarPorId(id);

        if (!atributo.getNombre().equals(datosAtributo.getNombre())
                && atributoDiagramaRepository
                .findByClaseIdAndNombre(
                        atributo.getClase().getId(),
                        datosAtributo.getNombre()
                )
                .isPresent()) {

            throw new IllegalStateException(
                    "Ya existe un atributo con el nombre: "
                            + datosAtributo.getNombre()
            );
        }

        atributo.setNombre(datosAtributo.getNombre());
        atributo.setTipoDato(datosAtributo.getTipoDato());
        atributo.setPermiteNulo(datosAtributo.getPermiteNulo());
        atributo.setIdentificador(datosAtributo.getIdentificador());

        return atributoDiagramaRepository.save(atributo);
    }

    public void eliminar(UUID id) {
        AtributoDiagrama atributo = buscarPorId(id);
        atributoDiagramaRepository.delete(atributo);
    }
}
