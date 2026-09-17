package com.collabcase.modelado.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "clases_diagrama",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_clase_nombre_modelo",
                        columnNames = {"modelo_id", "nombre"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaseDiagrama {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(
            name = "modelo_id",
            nullable = false
    )
    private ModeloDiagrama modelo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "posicion_x", nullable = false)
    private Double posicionX;

    @Column(name = "posicion_y", nullable = false)
    private Double posicionY;
}
