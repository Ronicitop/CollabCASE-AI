package com.collabcase.notificacion;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class EmisorFirebase implements EmisorPush {
    private final boolean habilitado;
    private final String credentialsPath;
    private FirebaseApp app;

    public EmisorFirebase(@Value("${push.enabled:false}") boolean habilitado,
                          @Value("${push.credentials-path:}") String credentialsPath) {
        this.habilitado = habilitado;
        this.credentialsPath = credentialsPath;
    }

    @Override
    public synchronized void enviar(AvisoPush aviso) throws Exception {
        if (!habilitado) return;
        if (app == null) {
            app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(credenciales()).build(), "collabcase-push");
        }
        boolean mensaje = "MENSAJE".equals(aviso.tipo());
        FirebaseMessaging.getInstance(app).send(Message.builder()
                .setTopic("proyecto_" + aviso.proyectoId())
                .putAllData(aviso.datos())
                .setNotification(Notification.builder()
                        .setTitle(mensaje ? "Nuevo mensaje" : "Proyecto actualizado")
                        .setBody("Hay novedades en un proyecto que sigues.").build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("collabcase_novedades").setIcon("ic_notification").build()).build())
                .build());
    }

    private GoogleCredentials credenciales() throws java.io.IOException {
        if (credentialsPath.isBlank()) return GoogleCredentials.getApplicationDefault();
        try (var stream = Files.newInputStream(Path.of(credentialsPath))) {
            return GoogleCredentials.fromStream(stream);
        }
    }

    @PreDestroy
    public void cerrar() {
        if (app != null) app.delete();
    }
}
