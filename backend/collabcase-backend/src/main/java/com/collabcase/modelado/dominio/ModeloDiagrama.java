package com.collabcase.modelado.dominio;

import com.collabcase.proyecto.dominio.Proyecto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "modelos_diagrama")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModeloDiagrama {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(
            name = "proyecto_id",
            nullable = false,
            unique = true
    )
    private Proyecto proyecto;

    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    public void antesDeGuardar() {

        if (version == null) {
            version = 1L;
        }

        actualizadoEn = LocalDateTime.now();
    }

    @PreUpdate
    public void antesDeActualizar() {
        actualizadoEn = LocalDateTime.now();
    }
}
