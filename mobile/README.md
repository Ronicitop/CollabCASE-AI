# CollabCASE AI para Android

App Flutter de consulta: proyectos, seguimiento, visor UML con zoom, clases, atributos,
relaciones y lectura del chat de cada proyecto. Interfaz en español, Material 3 y copia local de lo
consultado. No modifica modelos ni envía mensajes.

## Chat único por proyecto

Cada proyecto tiene un único historial compartido, aunque se inicien sesiones colaborativas
con códigos nuevos. Los mensajes antiguos se reúnen consultando las sesiones del proyecto;
no hay migración de datos, borrado de chats ni cambio del esquema de PostgreSQL.

La app abre **Chat del proyecto** directamente y guarda su historial por proyecto. Los
avisos de mensajes de sesiones anteriores también abren este chat y destacan el mensaje.
Las cachés antiguas por sesión ya no se consultan; abre el chat una vez con conexión para
guardar la copia completa compartida.

Para aplicar esta corrección actualiza el backend, recarga la web e instala la APK nueva.
Si usas Docker, desde la raíz del proyecto reconstruye las imágenes y actualiza esos servicios:

```powershell
docker compose build backend frontend
docker compose up -d --no-deps backend frontend
```

`docker compose restart` reutiliza la imagen existente. Después de actualizar, recarga ambas
ventanas de la web con Ctrl+F5. Puedes comprobar la versión del chat abriendo
`http://192.168.0.9:8080/api/sala/proyecto/ID_DEL_PROYECTO/mensajes`: debe devolver JSON.

El encabezado actual es **Chat del proyecto & IA Par**. Si un navegador todavía muestra
**Sala de Debate & IA Par**, está usando una versión anterior: abre
`http://192.168.0.9:3000` en ambos navegadores y entra al mismo proyecto. La web incluye
compatibilidad con HTTP local para generar los identificadores de colaboración.
Nginx sirve el HTML, `sw.js` y `registerSW.js` sin caché persistente; los archivos con
nombre versionado mantienen su caché. El registro de la PWA comprueba actualizaciones
con `updateViaCache: 'none'`.

En web, inicia una sesión, envía mensajes, sal e inicia otra: debe aparecer todo lo anterior.
Un segundo participante, incluso con otro código del mismo proyecto, debe leer el mismo
historial y recibir los mensajes nuevos. Las notificaciones conservan su funcionamiento.

El historial se consulta con `GET /api/sala/proyecto/{proyectoId}/mensajes`; las rutas antiguas
por sesión se conservan como adaptadores. Los clientes web actualizados escuchan
`/topic/proyecto/{proyectoId}/mensajes`.

## Visor UML móvil

- Arrastra con un dedo para desplazar el lienzo y usa dos dedos para ampliar.
- Cambia entre **Diagrama** y **Contenido** tocando las pestañas; deslizar el lienzo no cambia de pantalla.
- La vista se centra al abrir. Usa **+**, **−** y **Ajustar vista** para encuadrar el modelo.
- Se conserva el zoom al ir a Contenido y regresar. El botón de actualización sigue disponible arriba.
- Clases, atributos, claves, nombres de relaciones, multiplicidades y símbolos UML siguen el estilo de la web.

## Conexión por Wi-Fi

| Servicio | Dirección inicial |
| --- | --- |
| API Spring Boot | `http://192.168.0.9:8080` |
| Web React mediante Docker | `http://192.168.0.9:3000` |

1. Conecta celular y PC a la misma Wi-Fi. La PC debe conservar `192.168.0.9`.
2. Usa tu backend y PostgreSQL locales como de costumbre, con el código actualizado.
3. En el navegador del celular abre `http://192.168.0.9:8080/api/proyectos`.
   Debe aparecer un arreglo JSON, incluso si está vacío. No requiere Flutter.
4. Si no abre, comprueba IP, puerto 8080, que Spring no esté limitado a `127.0.0.1`,
   aislamiento de clientes Wi-Fi y permiso de entrada del firewall en la red privada.
5. El botón de la app abre la web Docker en el puerto 3000. Si usas Vite, configura
   `WEB_URL` con el puerto 5173 y expón Vite con `--host`.

No uses `localhost`: desde un celular corresponde al propio celular.
Android permite HTTP únicamente a `192.168.0.9` mediante
`android/app/src/main/res/xml/network_security_config.xml`. Si cambia la IP, actualiza
también ese archivo. Azure HTTPS no necesita esa excepción.

El compose raíz y `.env.example` incluyen las direcciones LAN y sus orígenes CORS.
Si ya tienes `.env`, actualiza allí `VITE_API_URL`, `VITE_WS_URL` y `CORS_ALLOWED_ORIGINS`:
sus valores tienen prioridad. Las variables Vite se incorporan al construir la web;
cambiar `.env` no modifica una imagen frontend ya construida.

## Configuración Flutter

- Validado con Flutter 3.41.4 / Dart 3.11.1.
- Identificador Android: `com.collabcase.mobile`; mínimo API 23; SDK de compilación 36 o superior.
- No requiere abrir Android Studio. `pubspec.lock` fija las dependencias verificadas.
- `lib/config.dart` admite las siguientes opciones `--dart-define` en el proceso que
  utilices para preparar tu versión de prueba en este dispositivo o en otra máquina:

```text
--dart-define=API_URL=http://192.168.0.9:8080
--dart-define=WEB_URL=http://192.168.0.9:3000
--dart-define=FIREBASE_ENABLED=true
```

Ya son los valores por defecto. Para regresar a la nube posteriormente:

```text
--dart-define=API_URL=https://collabcase-ai-backend-2026.azurewebsites.net
--dart-define=WEB_URL=https://stcollabcaseai2026.z14.web.core.windows.net/
```

Son opciones de compilación; las pantallas no necesitan cambios. La caché y el seguimiento
se separan por URL API para no mezclar servidores. No se ha generado APK, ejecutado
`flutter run`, iniciado emuladores ni abierto Android Studio.

## Activar Firebase push

Firebase está activado por defecto en esta copia local. Puedes desactivarlo con
`--dart-define=FIREBASE_ENABLED=false`; las consultas seguirán funcionando.

### Activación realizada en esta PC

- El JSON Android ya está en `mobile/android/app/google-services.json`.
- La cuenta de servicio se movió a `.secrets/firebase-service-account.json`, fuera de la app.
- `.secrets/firebase-push.properties` activa push para Spring al iniciar desde la raíz
  o desde `backend/collabcase-backend/`. La ruta del JSON es absoluta para esta PC.
- `docker-compose.override.yml` activa push y monta la cuenta en solo lectura al usar
  `docker compose` normalmente. Ambos archivos locales están ignorados por Git.
- Reinicia el backend con el código actualizado para aplicar los cambios. Si usas Docker,
  actualiza la imagen del backend. No se ha iniciado ni reconstruido ningún servicio aquí.
- Si arrancas el JAR desde otro directorio, usa `PUSH_ENABLED=true` y
  `GOOGLE_APPLICATION_CREDENTIALS` como se describe abajo.

### Android

1. Crea un proyecto en [Firebase Console](https://console.firebase.google.com/).
2. Registra Android con el paquete exacto `com.collabcase.mobile`.
3. Descarga `google-services.json` a `mobile/android/app/google-services.json`.
   Gradle aplica Google Services solamente cuando existe ese archivo.
4. Prepara tu versión de prueba con `--dart-define=FIREBASE_ENABLED=true`.
   No necesitas `firebase_options.dart`: se usan los recursos Android generados desde el JSON.
5. Abre Ajustes en la app y pulsa **Activar / revisar notificaciones**. Acepta el permiso.
   El celular necesita Google Play Services.
6. Sigue un proyecto con la estrella y comprueba el estado push en Ajustes.

### Backend local sin Docker

En Firebase → Configuración del proyecto → Cuentas de servicio → Firebase Admin SDK,
genera una clave privada para el mismo proyecto. Guárdala **fuera del repositorio**.
En la terminal donde iniciarás tu backend configura:

```powershell
$env:PUSH_ENABLED = 'true'
$env:GOOGLE_APPLICATION_CREDENTIALS = 'C:/ruta-privada/firebase-admin.json'
```

Luego inicia el backend actualizado con tu método habitual. La cuenta de servicio
nunca se copia a Flutter. El JSON Android y las claves están ignorados por Git.

### Backend local con Docker

En esta PC el override local automático ya activa push y monta la clave en solo lectura.
Para otra instalación sin ese archivo local, el siguiente override explícito permite configurarlo.
Cuando quieras actualizar tus contenedores, desde la raíz del repositorio:

```powershell
$env:FIREBASE_ADMIN_FILE = 'C:/ruta-privada/firebase-admin.json'
docker compose -f docker-compose.yml -f deploy/local/docker-compose.firebase.yml up -d --build
```

Este comando se proporciona para tu uso posterior y no se ha ejecutado aquí. Conserva tu
configuración PostgreSQL e IA. No necesitas actualizar Azure para las pruebas locales.
Firebase requiere Internet tanto en la PC como en el celular.

### Funcionamiento y límites

- Se usa el tema `proyecto_<UUID>` por proyecto seguido. Los temas reflejan el acceso
  abierto actual; no proporcionan permisos privados ni inicio de sesión.
- Los cambios de contenido del modelo se agrupan en ventanas de 60 segundos desde el
  primer cambio confirmado, incluyendo importaciones e IA. Mover clases no genera avisos.
- Cambiar un proyecto o guardar mensajes humanos y respuestas IA también genera avisos.
  El texto completo del mensaje se consulta al abrir su sala y no viaja en el push.
- El payload contiene `tipo`, `proyectoId` y, para mensajes, `sesionId` y `mensajeId`.
- En primer plano se usa un aviso local; en segundo plano Android muestra el payload FCM.
  Tocar un aviso abre su destino, también desde un inicio en frío.
- Las bajas de suscripciones fallidas se guardan y reintentan al volver a la app con red.
  Hasta completar la baja, Android puede recibir avisos de una suscripción anterior.
- Los avisos son de mejor esfuerzo, sin bandeja ni garantía de entrega. Las ventanas
  pendientes se guardan en memoria del backend y se pierden al reiniciarlo antes del envío.
  Un fallo de Firebase se registra sin impedir guardar el proyecto, modelo o mensaje.
- Cerrar normalmente la app permite probar recepción. **Forzar detención** desde ajustes
  Android bloquea recepción hasta reabrirla; no equivale a un cierre normal.

Referencias: [recepción FCM en Flutter](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages)
y [configuración del Admin SDK](https://firebase.google.com/docs/admin/setup).

## Pruebas ligeras

Desde `mobile/`, sin compilar Android ni iniciar un emulador:

```powershell
flutter pub get
flutter analyze --no-pub
flutter test --no-pub --concurrency=1
```

Pruebas unitarias nuevas del backend, desde `backend/collabcase-backend/`:

```powershell
.\mvnw.cmd '-Dtest=NotificacionesServiceTest,ContenidoModeloTest,SesionesConsultaTest,MensajesPushTest,ChatProyectoTest,ServicioIaHistorialTest' test
```

En `frontend/`, `npm run test:chat` verifica la combinación del historial, los mensajes
WebSocket y las confirmaciones de envío, sin ejecutar el navegador. Las pruebas del chat
del backend usan una base H2 temporal y no modifican tu PostgreSQL.

La prueba de contexto Spring preexistente necesita tu PostgreSQL y configuración habitual;
no forma parte de esa selección aislada.

## Lista de pruebas en tu celular

- [ ] El navegador abre `/api/proyectos` en la IP indicada.
- [ ] La app lista, busca y sigue proyectos; las estrellas persisten al reabrir.
- [ ] El detalle muestra descripción y fechas; el botón abre la web local.
- [ ] El visor permite zoom, desplazamiento y ajustar vista. Contenido muestra clases,
      atributos, tipos, relaciones, multiplicidades y clases de asociación.
- [ ] Consultar el chat no crea nuevas sesiones. Los mensajes son de solo lectura.
- [ ] Consulta proyecto, modelo y mensajes; desconecta Wi-Fi y vuelve a abrirlos:
      aparecen las copias con su fecha. Una consulta sin copia informa error.
- [ ] Recuperar conexión y actualizar muestra los datos actuales.
- [ ] Configura Firebase, permite avisos y sigue un proyecto. Cambia el modelo desde
      la web local: llega un aviso después de aproximadamente 60 segundos.
- [ ] Varios cambios dentro de esa ventana generan un solo aviso de modelo.
- [ ] Mover solamente una clase no genera aviso.
- [ ] Envía un mensaje desde la web: tocar el aviso abre la sala y destaca el mensaje.
- [ ] Prueba recepción con la app abierta, en segundo plano y cerrada normalmente.
- [ ] Dejar de seguir con conexión detiene los avisos futuros del proyecto.
- [ ] Denegar permiso mantiene funcionando la consulta y Ajustes explica el estado.

La compilación Android, las pruebas físicas y el envío real FCM quedan para tu dispositivo
y tus credenciales. Los tests simulados no verifican esos pasos.
