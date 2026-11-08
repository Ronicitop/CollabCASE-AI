import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:collabcase_mobile/diagram.dart';
import 'package:collabcase_mobile/models.dart';
import 'package:collabcase_mobile/repository.dart';
import 'package:collabcase_mobile/screens.dart';

Json example({double x = 4000, double y = 9000, String tipo = 'ASOCIACION'}) =>
    {
      'version': 1,
      'clases': <Json>[
        {
          'id': 'cliente',
          'nombre': 'cliente',
          'posicionX': x,
          'posicionY': y,
          'atributos': [
            {'nombre': 'correo', 'tipoDato': 'str', 'permiteNulo': true},
          ],
        },
        {
          'id': 'recibo',
          'nombre': 'recibo',
          'posicionX': x + 408,
          'posicionY': y,
          'atributos': [
            {'nombre': 'nombre', 'tipoDato': 'str'},
          ],
        },
      ],
      'relaciones': <Json>[
        {
          'claseOrigenId': 'cliente',
          'claseDestinoId': 'recibo',
          'tipo': tipo,
          'nombre': 'producto',
          'multiplicidadOrigen': '1',
          'multiplicidadDestino': '*',
        },
      ],
    };

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('ambas multiplicidades y el nombre quedan fuera de las cajas', () {
    final layout = DiagramLayout(Modelo.fromJson(example()));
    final labels = layout.edges.single.labels;
    expect(
      labels.map((label) => label.value),
      containsAll(['1', '*', 'producto']),
    );
    for (final label in labels) {
      expect(
        layout.boxes.values.any((box) => box.overlaps(label.bounds)),
        isFalse,
        reason:
            'La etiqueta ${label.value} no debe quedar tapada por una clase',
      );
    }
    for (var i = 1; i < layout.edges.single.points.length; i++) {
      final delta =
          layout.edges.single.points[i] - layout.edges.single.points[i - 1];
      expect(
        delta.dx == 0 || delta.dy == 0,
        isTrue,
        reason: 'Conectores ortogonales como la web',
      );
    }
    expect(
      layout.nodes['cliente']!.rows.single.text!.toPlainText(),
      'correo: str',
    );
  });

  test('el encuadre ignora el espacio vacío anterior al diagrama', () {
    final positive = DiagramLayout(Modelo.fromJson(example()));
    final negative = DiagramLayout(
      Modelo.fromJson(example(x: -8000, y: -3000)),
    );
    expect(positive.boxes, negative.boxes);
    expect(positive.contentBounds, negative.contentBounds);
  });

  test('realización y dependencia usan discontinuas; normaliza tipos web', () {
    expect(
      DiagramLayout(
        Modelo.fromJson(example(tipo: 'REALIZATION')),
      ).edges.single.dashed,
      isTrue,
    );
    expect(
      DiagramLayout(
        Modelo.fromJson(example(tipo: 'DEPENDENCIA')),
      ).edges.single.dashed,
      isTrue,
    );
    expect(normalizarTipoRelacion(' generalización '), 'HERENCIA');
    expect(normalizarTipoRelacion('COMPOSITION'), 'COMPOSICION');
    expect(normalizarTipoRelacion('AGGREGATION'), 'AGREGACION');
    expect(
      DiagramLayout(Modelo.fromJson(example())).edges.single.dashed,
      isFalse,
    );
  });

  testWidgets('ajusta al abrir, amplía con botones y respeta el área segura', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(390, 740));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final model = Modelo.fromJson(example());
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(390, 740),
            padding: EdgeInsets.only(bottom: 30),
          ),
          child: Scaffold(body: Diagram(modelo: model)),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final viewer = tester.widget<InteractiveViewer>(
      find.byType(InteractiveViewer),
    );
    final controller = viewer.transformationController!;
    final layout = DiagramLayout(model);
    final matrix = controller.value.clone();
    final topLeft = MatrixUtils.transformPoint(
      matrix,
      layout.contentBounds.topLeft,
    );
    final bottomRight = MatrixUtils.transformPoint(
      matrix,
      layout.contentBounds.bottomRight,
    );
    final area = tester.getSize(find.byType(InteractiveViewer));
    expect(topLeft.dx, greaterThanOrEqualTo(0));
    expect(topLeft.dy, greaterThanOrEqualTo(0));
    expect(bottomRight.dx, lessThanOrEqualTo(area.width));
    expect(bottomRight.dy, lessThanOrEqualTo(area.height));
    expect((topLeft.dx + bottomRight.dx) / 2, closeTo(area.width / 2, 1));
    final scale = matrix.getMaxScaleOnAxis();
    await tester.tap(find.byTooltip('Acercar'));
    await tester.pump();
    expect(controller.value.getMaxScaleOnAxis(), greaterThan(scale));
    await tester.tap(find.text('Ajustar vista'));
    await tester.pump();
    expect(controller.value.getMaxScaleOnAxis(), closeTo(scale, 0.001));
    expect(
      tester.getBottomRight(find.byTooltip('Acercar')).dy,
      lessThanOrEqualTo(710),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'arrastrar mueve el lienzo; solo tocar las pestañas cambia de vista y conserva el zoom',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final repo = Repository(
        await SharedPreferences.getInstance(),
        client: MockClient(
          (_) async => http.Response(jsonEncode(example()), 200),
        ),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: ModelScreen(repository: repo, proyectoId: 'proyecto'),
        ),
      );
      await tester.pumpAndSettle();
      final tabContext = tester.element(find.byType(TabBar));
      final tabs = DefaultTabController.of(tabContext);
      final viewer = tester.widget<InteractiveViewer>(
        find.byType(InteractiveViewer),
      );
      final controller = viewer.transformationController!;
      await tester.tap(find.byTooltip('Acercar'));
      await tester.pump();
      final before = controller.value.clone();
      await tester.drag(find.byType(InteractiveViewer), const Offset(-180, 60));
      await tester.pumpAndSettle();
      expect(tabs.index, 0);
      expect(controller.value, isNot(before));
      final moved = controller.value.clone();
      await tester.drag(find.byType(InteractiveViewer), const Offset(90, -100));
      await tester.pumpAndSettle();
      expect(tabs.index, 0);
      expect(controller.value, isNot(moved));
      final saved = controller.value.clone();
      await tester.tap(find.text('Contenido'));
      await tester.pumpAndSettle();
      expect(tabs.index, 1);
      expect(find.textContaining('Versión 1'), findsOneWidget);
      await tester.tap(find.text('Diagrama'));
      await tester.pumpAndSettle();
      expect(tabs.index, 0);
      expect(controller.value, saved);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      repo.dispose();
    },
  );

  testWidgets(
    'renderiza autorrelaciones, clase de asociación y nombres largos',
    (tester) async {
      final json = example();
      final classes = json['clases'] as List;
      classes.add({
        'id': 'detalle',
        'nombre': 'Detalle del vínculo de asociación',
        'posicionX': 4100,
        'posicionY': 9300,
        'atributos': [],
      });
      (json['relaciones'] as List<Json>).addAll(<Json>[
        {
          'claseOrigenId': 'cliente',
          'claseDestinoId': 'cliente',
          'tipo': 'HERENCIA',
        },
        {
          'claseOrigenId': 'cliente',
          'claseDestinoId': 'recibo',
          'tipo': 'REALIZACION',
          'claseAsociacionId': 'detalle',
        },
      ]);
      final model = Modelo.fromJson(json);
      final layout = DiagramLayout(model);
      expect(layout.edges.last.associationEnd, isNotNull);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: Diagram(modelo: model)),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    },
  );
}
