package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.RelacionDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RelacionDiagramaRepository extends JpaRepository<RelacionDiagrama, UUID> {

    List<RelacionDiagrama> findByModeloId(UUID modeloId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RelacionDiagrama r
             where r.claseOrigen.id = :claseId
                or r.claseDestino.id = :claseId
            """)
    int eliminarPorClaseId(
            @Param("claseId") UUID claseId
    );
}
