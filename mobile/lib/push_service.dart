import 'dart:async';
import 'dart:convert';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'config.dart';
import 'models.dart';
import 'repository.dart';

@pragma('vm:entry-point')
Future<void> firebaseBackground(RemoteMessage message) async {
  await Firebase.initializeApp();
  // Android muestra el payload notification; no duplicarlo con otro aviso local.
}

abstract interface class TopicClient {
  Future<void> subscribe(String topic);
  Future<void> unsubscribe(String topic);
}

class FirebaseTopics implements TopicClient {
  @override
  Future<void> subscribe(String topic) =>
      FirebaseMessaging.instance.subscribeToTopic(topic);
  @override
  Future<void> unsubscribe(String topic) =>
      FirebaseMessaging.instance.unsubscribeFromTopic(topic);
}

/// Serializa cambios; recuerda bajas pendientes incluso tras cerrar la aplicación.
class TopicSync {
  final Repository repository;
  final TopicClient client;
  Future<void> _tail = Future.value();
  TopicSync(this.repository, this.client);
  Future<void> sync() {
    final result = _tail.then((_) async {
      final key = 'push_topics:${repository.baseUrl}';
      final actual = (repository.prefs.getStringList(key) ?? []).toSet();
      final desired = repository.seguidos;
      for (final id in actual.difference(desired)) {
        await client.unsubscribe('proyecto_$id');
        actual.remove(id);
        await repository.prefs.setStringList(key, actual.toList());
      }
      // Re-suscribe también los existentes para recuperar rotaciones de token.
      for (final id in desired) {
        await client.subscribe('proyecto_$id');
        actual.add(id);
        await repository.prefs.setStringList(key, actual.toList());
      }
    });
    _tail = result.catchError((Object _) {});
    return result;
  }
}

class PushService extends ChangeNotifier {
  final Repository repository;
  final void Function(PushTarget) onOpen;
  final local = FlutterLocalNotificationsPlugin();
  final List<StreamSubscription<dynamic>> _subscriptions = [];
  TopicSync? topics;
  bool _starting = false;
  bool get configured => topics != null;
  String status = 'Push desactivado: falta configurar Firebase.';
  PushService(this.repository, this.onOpen);

  Future<void> start() async {
    if (!AppConfig.firebaseEnabled || _starting || configured) return;
    _starting = true;
    try {
      await Firebase.initializeApp();
      FirebaseMessaging.onBackgroundMessage(firebaseBackground);
      await local.initialize(
        settings: const InitializationSettings(
          android: AndroidInitializationSettings('ic_notification'),
        ),
        onDidReceiveNotificationResponse: (response) {
          final payload = response.payload;
          if (payload != null) _open(jsonDecode(payload) as Json);
        },
      );
      await local
          .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin
          >()
          ?.createNotificationChannel(
            const AndroidNotificationChannel(
              'collabcase_novedades',
              'Novedades de proyectos',
              importance: Importance.high,
            ),
          );
      topics = TopicSync(repository, FirebaseTopics());
      _subscriptions.add(
        FirebaseMessaging.onMessageOpenedApp.listen((m) => _open(m.data)),
      );
      _subscriptions.add(
        FirebaseMessaging.onMessage.listen((m) async {
          final target = PushTarget.parse(m.data);
          if (target == null ||
              !repository.seguidos.contains(target.proyectoId)) {
            return;
          }
          try {
            await local.show(
              id: m.messageId.hashCode & 0x7fffffff,
              title: m.notification?.title ?? 'Novedades de CollabCASE AI',
              body:
                  m.notification?.body ??
                  'Hay novedades en un proyecto que sigues.',
              notificationDetails: const NotificationDetails(
                android: AndroidNotificationDetails(
                  'collabcase_novedades',
                  'Novedades de proyectos',
                  importance: Importance.high,
                  priority: Priority.high,
                ),
              ),
              payload: jsonEncode(m.data),
            );
          } catch (_) {
            status = 'No se pudo mostrar el aviso. Revisa los permisos.';
            notifyListeners();
          }
        }),
      );
      _subscriptions.add(
        FirebaseMessaging.instance.onTokenRefresh.listen((_) => sync()),
      );
      repository.addListener(_changed);
      final initial = await FirebaseMessaging.instance.getInitialMessage();
      if (initial != null) _open(initial.data);
      final launch = await local.getNotificationAppLaunchDetails();
      final payload = launch?.notificationResponse?.payload;
      if (launch?.didNotificationLaunchApp == true && payload != null) {
        _open(jsonDecode(payload) as Json);
      }
      await refreshPermission();
    } catch (_) {
      status =
          'Firebase no disponible. La consulta de proyectos sigue funcionando.';
      notifyListeners();
    } finally {
      _starting = false;
    }
  }

  void _open(Json data) {
    final target = PushTarget.parse(data);
    if (target != null && repository.seguidos.contains(target.proyectoId)) {
      onOpen(target);
    }
  }

  void _changed() {
    unawaited(sync());
  }

  Future<void> requestPermission() async {
    if (!configured) {
      await start();
      if (!configured) return;
    }
    try {
      await FirebaseMessaging.instance.requestPermission();
      await refreshPermission();
    } catch (_) {
      status = 'No se pudo solicitar permiso.';
      notifyListeners();
    }
  }

  Future<void> refreshPermission() async {
    await sync();
  }

  Future<void> sync() async {
    if (!configured) return;
    try {
      await topics!.sync();
      final permission = await FirebaseMessaging.instance
          .getNotificationSettings();
      status =
          permission.authorizationStatus == AuthorizationStatus.authorized ||
              permission.authorizationStatus == AuthorizationStatus.provisional
          ? 'Push listo para los proyectos que sigues.'
          : 'Notificaciones sin permiso. Actívalas desde este botón o en ajustes de Android.';
    } catch (_) {
      status = 'Suscripciones pendientes. Se reintentarán al volver a la app.';
    }
    notifyListeners();
  }

  @override
  void dispose() {
    repository.removeListener(_changed);
    for (final sub in _subscriptions) {
      unawaited(sub.cancel());
    }
    super.dispose();
  }
}
