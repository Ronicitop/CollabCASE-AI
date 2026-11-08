import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:collabcase_mobile/main.dart';
import 'package:collabcase_mobile/models.dart';
import 'package:collabcase_mobile/repository.dart';
import 'package:collabcase_mobile/diagram.dart';
import 'package:collabcase_mobile/screens.dart';

void main() {
  const id = '00000000-0000-0000-0000-000000000001';
  const room = '00000000-0000-0000-0000-000000000002';
  setUp(() => SharedPreferences.setMockInitialValues({}));
  testWidgets('muestra proyectos, filtra y persiste seguimiento', (
    tester,
  ) async {
    final repo = Repository(
      await SharedPreferences.getInstance(),
      client: MockClient(
        (request) async => http.Response(
          jsonEncode([
            {
              'id': id,
              'nombre': 'Inventario',
              'descripcion': 'Modelo de prueba',
            },
          ]),
          200,
        ),
      ),
    );
    await tester.pumpWidget(CollabCaseApp(repository: repo, enablePush: false));
    await tester.pumpAndSettle();
    expect(find.text('Inventario'), findsOneWidget);
    await tester.tap(find.byTooltip('Seguir proyecto'));
    await tester.pumpAndSettle();
    expect(repo.seguidos, contains(id));
    await tester.enterText(find.byType(TextField), 'inexistente');
    await tester.pumpAndSettle();
    expect(find.text('No hay proyectos para mostrar.'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    repo.dispose();
  });

  testWidgets('ruta de aviso abre proyecto y mensaje correcto', (tester) async {
    final repo = Repository(
      await SharedPreferences.getInstance(),
      client: MockClient((request) async {
        dynamic data;
        if (request.url.path.endsWith('/mensajes')) {
          expect(request.url.path, '/api/sala/proyecto/$id/mensajes');
          data = [
            {
              'id': room,
              'contenido': 'Mensaje seleccionado',
              'remitenteNombre': 'Equipo',
            },
          ];
        } else if (request.url.path.endsWith('/$id')) {
          data = {'id': id, 'nombre': 'Inventario'};
        } else {
          data = [];
        }
        return http.Response(jsonEncode(data), 200);
      }),
    );
    await tester.pumpWidget(CollabCaseApp(repository: repo, enablePush: false));
    await tester.pumpAndSettle();
    final context = tester.element(find.text('Tus proyectos, donde estés'));
    Navigator.of(
      context,
    ).pushNamed('/proyecto', arguments: PushTarget(id, room, room));
    await tester.pumpAndSettle();
    expect(find.text('Chat del proyecto · lectura'), findsOneWidget);
    expect(find.text('Mensaje del aviso'), findsOneWidget);
    expect(find.text('Mensaje seleccionado'), findsWidgets);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    repo.dispose();
  });

  testWidgets('visor renderiza posiciones negativas y relaciones UML', (
    tester,
  ) async {
    final model = Modelo.fromJson({
      'version': 1,
      'clases': [
        {
          'id': id,
          'nombre': 'Cliente',
          'posicionX': -200,
          'posicionY': -100,
          'atributos': [
            {'nombre': 'id', 'tipoDato': 'Long', 'identificador': true},
          ],
        },
        {
          'id': room,
          'nombre': 'Pedido',
          'posicionX': 300,
          'posicionY': 100,
          'atributos': [],
        },
      ],
      'relaciones': [
        {
          'claseOrigenId': id,
          'claseDestinoId': room,
          'tipo': 'COMPOSICION',
          'multiplicidadOrigen': '1',
          'multiplicidadDestino': '*',
        },
      ],
    });
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: Diagram(modelo: model)),
      ),
    );
    await tester.tap(find.text('Ajustar vista'));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('un proyecto abre un solo chat sin consultar ni crear sesiones', (
    tester,
  ) async {
    final requests = <String>[];
    final repo = Repository(
      await SharedPreferences.getInstance(),
      client: MockClient((request) async {
        requests.add('${request.method} ${request.url.path}');
        final isChat = request.url.path == '/api/sala/proyecto/$id/mensajes';
        return http.Response(
          jsonEncode(
            isChat
                ? [
                    {
                      'id': 'anterior',
                      'contenido': 'De la primera sesión',
                      'sesionId': 'primera',
                    },
                    {
                      'id': 'nuevo',
                      'contenido': 'De la segunda sesión',
                      'sesionId': 'segunda',
                    },
                  ]
                : {'id': id, 'nombre': 'Inventario'},
          ),
          200,
          headers: const {'content-type': 'application/json'},
        );
      }),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: ProjectScreen(
          repository: repo,
          target: PushTarget(id, null, null),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Salas del proyecto'), findsNothing);
    await tester.tap(find.text('Chat del proyecto'));
    await tester.pumpAndSettle();
    expect(find.text('Chat del proyecto · lectura'), findsOneWidget);
    expect(find.text('De la primera sesión'), findsOneWidget);
    expect(find.text('De la segunda sesión'), findsOneWidget);
    expect(requests, [
      'GET /api/proyectos/$id',
      'GET /api/sala/proyecto/$id/mensajes',
    ]);
    await tester.pumpWidget(const SizedBox());
    repo.dispose();
  });
}
