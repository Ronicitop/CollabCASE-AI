package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.AtributoDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtributoDiagramaRepository extends JpaRepository<AtributoDiagrama, UUID> {

    List<AtributoDiagrama> findByClaseId(UUID claseId);

    Optional<AtributoDiagrama> findByClaseIdAndNombre(
            UUID claseId,
            String nombre
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from AtributoDiagrama a
             where a.clase.id = :claseId
            """)
    int eliminarPorClaseId(
            @Param("claseId") UUID claseId
    );
}
