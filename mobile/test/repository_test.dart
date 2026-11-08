import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:collabcase_mobile/repository.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const id = '00000000-0000-0000-0000-000000000001';
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'el historial compartido se guarda por proyecto y se recupera sin conexión',
    () async {
      var offline = false;
      final repo = Repository(
        await SharedPreferences.getInstance(),
        client: MockClient((request) async {
          expect(request.url.path, '/api/sala/proyecto/$id/mensajes');
          if (offline) throw http.ClientException('sin red');
          return http.Response(
            jsonEncode([
              {
                'id': 'primero',
                'sesionId': 'sesion-antigua',
                'contenido': 'Anterior',
              },
              {
                'id': 'segundo',
                'sesionId': 'sesion-nueva',
                'contenido': 'Nuevo',
              },
            ]),
            200,
          );
        }),
      );
      final online = await repo.mensajesProyecto(id);
      expect(online.valor.map((m) => m.id), ['primero', 'segundo']);
      offline = true;
      final cached = await repo.mensajesProyecto(id);
      expect(cached.desdeCache, true);
      expect(cached.valor.map((m) => m.id), ['primero', 'segundo']);
      repo.dispose();
    },
  );

  test(
    'consulta API UTF-8, guarda copia y recupera datos al fallar red',
    () async {
      bool offline = false;
      final repo = Repository(
        await SharedPreferences.getInstance(),
        client: MockClient((request) async {
          expect(
            request.url.toString(),
            'http://192.168.0.9:8080/api/proyectos',
          );
          if (offline) throw http.ClientException('sin red');
          return http.Response.bytes(
            utf8.encode(
              jsonEncode([
                {'id': id, 'nombre': 'Diseño UML'},
              ]),
            ),
            200,
          );
        }),
      );
      final online = await repo.proyectos();
      expect(online.desdeCache, false);
      expect(online.valor.single.nombre, 'Diseño UML');
      offline = true;
      final cached = await repo.proyectos();
      expect(cached.desdeCache, true);
      expect(cached.guardadoEn, online.guardadoEn);
      expect(cached.valor.single.id, id);
      repo.dispose();
    },
  );

  test('404 invalida copia previa y no presenta proyecto eliminado', () async {
    int status = 200;
    final repo = Repository(
      await SharedPreferences.getInstance(),
      client: MockClient(
        (_) async => http.Response(
          status == 200 ? jsonEncode({'id': id, 'nombre': 'Proyecto'}) : '{}',
          status,
        ),
      ),
    );
    await repo.proyecto(id);
    status = 404;
    await expectLater(repo.proyecto(id), throwsA(isA<ApiException>()));
    status = 503;
    await expectLater(repo.proyecto(id), throwsA(isA<ApiException>()));
    repo.dispose();
  });

  test('sin conexión ni caché informa error y no inventa proyectos', () async {
    final repo = Repository(
      await SharedPreferences.getInstance(),
      client: MockClient((_) async => throw http.ClientException('offline')),
    );
    await expectLater(repo.proyectos(), throwsException);
    repo.dispose();
  });

  test('seguimiento persiste y las cachés se aíslan por servidor', () async {
    final prefs = await SharedPreferences.getInstance();
    final first = Repository(prefs);
    await first.seguir(id, true);
    final reopened = Repository(prefs);
    expect(reopened.seguidos, contains(id));
    final other = Repository(prefs, baseUrl: 'https://otro.example');
    expect(other.seguidos, isEmpty);
    await reopened.seguir(id, false);
    expect(first.seguidos, isEmpty);
    first.dispose();
    reopened.dispose();
    other.dispose();
  });
}
