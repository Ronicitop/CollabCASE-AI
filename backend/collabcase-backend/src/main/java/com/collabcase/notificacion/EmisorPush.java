package com.collabcase.notificacion;

public interface EmisorPush {
    void enviar(AvisoPush aviso) throws Exception;
}
