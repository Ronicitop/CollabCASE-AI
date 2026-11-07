package com.collabcase.colaboracion.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class MensajeSalaDTO {
    private UUID id;
    private UUID sesionId;
    private String remitenteNombre;
    private String tipoEmisor; // USUARIO, IA, SISTEMA
    private String contenido;
    private LocalDateTime fechaHora;

    public MensajeSalaDTO() {}

    public MensajeSalaDTO(UUID id, UUID sesionId, String remitenteNombre, String tipoEmisor, String contenido, LocalDateTime fechaHora) {
        this.id = id;
        this.sesionId = sesionId;
        this.remitenteNombre = remitenteNombre;
        this.tipoEmisor = tipoEmisor;
        this.contenido = contenido;
        this.fechaHora = fechaHora;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSesionId() { return sesionId; }
    public void setSesionId(UUID sesionId) { this.sesionId = sesionId; }
    public String getRemitenteNombre() { return remitenteNombre; }
    public void setRemitenteNombre(String remitenteNombre) { this.remitenteNombre = remitenteNombre; }
    public String getTipoEmisor() { return tipoEmisor; }
    public void setTipoEmisor(String tipoEmisor) { this.tipoEmisor = tipoEmisor; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
}