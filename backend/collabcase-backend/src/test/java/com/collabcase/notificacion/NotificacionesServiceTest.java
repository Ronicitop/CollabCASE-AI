package com.collabcase.notificacion;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificacionesServiceTest {
    private final EmisorPush emisor = mock(EmisorPush.class);
    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    private final NotificacionesService service = new NotificacionesService(emisor, true, executor);
    private final UUID proyecto = UUID.randomUUID();
    private final TransactionTemplate tx = new TransactionTemplate(new AbstractPlatformTransactionManager() {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object t, TransactionDefinition d) {}
        @Override protected void doCommit(DefaultTransactionStatus s) {}
        @Override protected void doRollback(DefaultTransactionStatus s) {}
    });

    @Test void programaSoloDespuesDeCommitYAgrupaDuranteSesentaSegundos() throws Exception {
        tx.executeWithoutResult(status -> {
            service.modelo(proyecto);
            service.modelo(proyecto);
            verifyNoInteractions(executor, emisor);
        });
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor, times(1)).schedule(task.capture(), eq(60L), eq(TimeUnit.SECONDS));
        task.getValue().run();
        verify(emisor).enviar(new AvisoPush("MODELO", proyecto, null, null));
        service.modelo(proyecto);
        verify(executor, times(2)).schedule(any(Runnable.class), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test void rollbackNoEnviaNiPrograma() {
        tx.executeWithoutResult(status -> {
            service.modelo(proyecto);
            service.mensaje(proyecto, UUID.randomUUID(), UUID.randomUUID());
            service.proyecto(proyecto);
            status.setRollbackOnly();
        });
        verifyNoInteractions(executor, emisor);
    }

    @Test void mensajeIncluyeDestinoYUnFalloNoSePropaga() throws Exception {
        UUID sesion = UUID.randomUUID(), mensaje = UUID.randomUUID();
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(executor).execute(any());
        doThrow(new IllegalStateException("Firebase no disponible")).when(emisor).enviar(any());
        assertDoesNotThrow(() -> tx.executeWithoutResult(s -> service.mensaje(proyecto, sesion, mensaje)));
        var notice = ArgumentCaptor.forClass(AvisoPush.class);
        verify(emisor).enviar(notice.capture());
        assertEquals(sesion.toString(), notice.getValue().datos().get("sesionId"));
        assertEquals(mensaje.toString(), notice.getValue().datos().get("mensajeId"));
    }

    @Test void desactivadoNoProgramaNada() {
        var disabled = new NotificacionesService(emisor, false, executor);
        disabled.modelo(proyecto);
        disabled.proyecto(proyecto);
        disabled.mensaje(proyecto, UUID.randomUUID(), UUID.randomUUID());
        verifyNoInteractions(executor, emisor);
    }
}
