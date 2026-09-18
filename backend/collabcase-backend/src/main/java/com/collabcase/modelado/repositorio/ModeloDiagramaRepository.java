package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.ModeloDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ModeloDiagramaRepository extends JpaRepository<ModeloDiagrama, UUID> {

    Optional<ModeloDiagrama> findByProyectoId(UUID proyectoId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ModeloDiagrama m
               set m.version = m.version + 1,
                   m.actualizadoEn = CURRENT_TIMESTAMP
             where m.id = :modeloId
            """)
    int incrementarVersion(
            @Param("modeloId") UUID modeloId
    );
}
