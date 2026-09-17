package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.RelacionDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RelacionDiagramaRepository extends JpaRepository<RelacionDiagrama, UUID> {

    List<RelacionDiagrama> findByModeloId(UUID modeloId);
}
