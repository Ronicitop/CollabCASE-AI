import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'models.dart';

const _ink = Color(0xff1a192b);
const _line = Color(0xffb1b1b7);

TextPainter _text(
  String value, {
  double size = 13,
  bool bold = false,
  double width = 220,
  TextAlign align = TextAlign.left,
}) => TextPainter(
  text: TextSpan(
    text: value,
    style: TextStyle(
      color: _ink,
      fontSize: size,
      height: 1.3,
      fontWeight: bold ? FontWeight.w700 : FontWeight.w400,
    ),
  ),
  textDirection: TextDirection.ltr,
  textAlign: align,
)..layout(maxWidth: width);

List<Json> _attributes(Json node) {
  final values = List<Json>.from(
    node['atributos'] as List? ?? [],
  ).indexed.toList();
  values.sort((a, b) {
    final order =
        (b.$2['identificador'] == true ? 1 : 0) -
        (a.$2['identificador'] == true ? 1 : 0);
    return order == 0 ? a.$1.compareTo(b.$1) : order;
  });
  return values.map((e) => e.$2).toList();
}

class Diagram extends StatefulWidget {
  final Modelo modelo;
  const Diagram({super.key, required this.modelo});
  @override
  State<Diagram> createState() => _DiagramState();
}

class _DiagramState extends State<Diagram> with AutomaticKeepAliveClientMixin {
  final controller = TransformationController();
  late DiagramLayout layout = DiagramLayout(widget.modelo);
  Size? viewport;
  bool needsFit = true;
  @override
  bool get wantKeepAlive => true;
  @override
  void didUpdateWidget(covariant Diagram oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.modelo != widget.modelo) {
      layout = DiagramLayout(widget.modelo);
      needsFit = true;
    }
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  void fit() {
    final view = viewport;
    if (view == null || view.isEmpty) return;
    final bounds = layout.contentBounds.inflate(28);
    final scale = math.min(
      1.2,
      math.min(view.width / bounds.width, view.height / bounds.height),
    );
    controller.value = Matrix4.identity()
      ..setTranslationRaw(
        view.width / 2 - bounds.center.dx * scale,
        view.height / 2 - bounds.center.dy * scale,
        0,
      )
      ..scaleByDouble(scale, scale, 1, 1);
  }

  void zoom(double factor) {
    final view = viewport;
    if (view == null) return;
    final current = controller.value.getMaxScaleOnAxis();
    final next = (current * factor).clamp(0.001, 4.0);
    final center = Offset(view.width / 2, view.height / 2);
    final scene = controller.toScene(center);
    controller.value = Matrix4.identity()
      ..setTranslationRaw(
        center.dx - scene.dx * next,
        center.dy - scene.dy * next,
        0,
      )
      ..scaleByDouble(next, next, 1, 1);
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    if (layout.boxes.isEmpty) {
      return const Center(child: Text('El modelo aún no tiene clases.'));
    }
    return SafeArea(
      top: false,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final nextViewport = constraints.biggest;
          if (viewport != nextViewport || needsFit) {
            viewport = nextViewport;
            needsFit = false;
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (mounted) fit();
            });
          }
          return Stack(
            children: [
              const Positioned.fill(
                child: CustomPaint(painter: _GridPainter()),
              ),
              Positioned.fill(
                child: InteractiveViewer(
                  key: const ValueKey('uml-viewport'),
                  alignment: Alignment.topLeft,
                  transformationController: controller,
                  constrained: false,
                  boundaryMargin: const EdgeInsets.all(double.infinity),
                  minScale: 0.001,
                  maxScale: 4,
                  child: Semantics(
                    label:
                        'Diagrama UML de solo lectura. Arrastra para mover y pellizca para ampliar.',
                    child: RepaintBoundary(
                      child: CustomPaint(
                        size: layout.size,
                        painter: UmlPainter(widget.modelo, layout),
                      ),
                    ),
                  ),
                ),
              ),
              Positioned(
                left: 12,
                bottom: 12,
                child: Material(
                  elevation: 2,
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        onPressed: () => zoom(1.3),
                        icon: const Icon(Icons.add),
                        tooltip: 'Acercar',
                      ),
                      IconButton(
                        onPressed: () => zoom(1 / 1.3),
                        icon: const Icon(Icons.remove),
                        tooltip: 'Alejar',
                      ),
                      TextButton.icon(
                        onPressed: fit,
                        icon: const Icon(Icons.fit_screen),
                        label: const Text('Ajustar vista'),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _GridPainter extends CustomPainter {
  const _GridPainter();
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawColor(Colors.white, BlendMode.src);
    final paint = Paint()..color = const Color(0xffd6dae1);
    for (double x = 12; x < size.width; x += 20) {
      for (double y = 12; y < size.height; y += 20) {
        canvas.drawCircle(Offset(x, y), 0.7, paint);
      }
    }
  }

  @override
  bool shouldRepaint(covariant _GridPainter oldDelegate) => false;
}

class UmlNode {
  final Rect box;
  final TextPainter title;
  final List<TextPainter> rows;
  final List<bool> keys;
  final double header;
  UmlNode(this.box, this.title, this.rows, this.keys, this.header);
}

class UmlLabel {
  final String value;
  final TextPainter painter;
  final Rect bounds;
  UmlLabel(this.value, this.painter, this.bounds);
}

String normalizarTipoRelacion(String? value) {
  var type = (value ?? 'ASOCIACION').trim().toUpperCase();
  for (final entry in {
    'Á': 'A',
    'É': 'E',
    'Í': 'I',
    'Ó': 'O',
    'Ú': 'U',
  }.entries) {
    type = type.replaceAll(entry.key, entry.value);
  }
  return const {
        'ASSOCIATION': 'ASOCIACION',
        'AGGREGATION': 'AGREGACION',
        'COMPOSITION': 'COMPOSICION',
        'GENERALIZATION': 'HERENCIA',
        'GENERALIZACION': 'HERENCIA',
        'INHERITANCE': 'HERENCIA',
        'REALIZATION': 'REALIZACION',
        'DEPENDENCY': 'DEPENDENCIA',
      }[type] ??
      type;
}

class UmlEdge {
  final String tipo;
  final List<Offset> points;
  final Offset sourceNormal, targetNormal;
  final List<UmlLabel> labels;
  final Offset? associationEnd;
  UmlEdge(
    this.tipo,
    this.points,
    this.sourceNormal,
    this.targetNormal,
    this.labels,
    this.associationEnd,
  );
  bool get dashed => tipo == 'DEPENDENCIA' || tipo == 'REALIZACION';
  Offset get middle {
    var remaining = 0.0;
    for (var i = 1; i < points.length; i++) {
      remaining += (points[i] - points[i - 1]).distance / 2;
    }
    for (var i = 1; i < points.length; i++) {
      final segment = points[i] - points[i - 1];
      if (remaining <= segment.distance && segment.distance > 0) {
        return points[i - 1] + segment * (remaining / segment.distance);
      }
      remaining -= segment.distance;
    }
    return points.first;
  }
}

class DiagramLayout {
  final boxes = <String, Rect>{};
  final nodes = <String, UmlNode>{};
  final edges = <UmlEdge>[];
  Rect contentBounds = const Rect.fromLTWH(0, 0, 1, 1);
  Size size = const Size(400, 400);
  DiagramLayout(Modelo model) {
    if (model.clases.isEmpty) return;
    double coordinate(Json node, String key) {
      final value = (node[key] as num?)?.toDouble() ?? 0;
      return value.isFinite ? value : 0;
    }

    final minX = model.clases
        .map((c) => coordinate(c, 'posicionX'))
        .reduce(math.min);
    final minY = model.clases
        .map((c) => coordinate(c, 'posicionY'))
        .reduce(math.min);
    for (final c in model.clases) {
      final attributes = _attributes(c);
      final title = _text(
        '${c['nombre']}',
        size: 16,
        bold: true,
        align: TextAlign.center,
      );
      final header = math.max(42.0, title.height + 20);
      final rows = attributes.isEmpty
          ? [_text('Sin atributos')]
          : attributes
                .map(
                  (a) => _text(
                    '${a['nombre']}: ${a['tipoDato']}',
                    width: a['identificador'] == true ? 202 : 220,
                  ),
                )
                .toList();
      final rowHeight = rows.fold(0.0, (total, row) => total + row.height + 5);
      final rect = Rect.fromLTWH(
        coordinate(c, 'posicionX') - minX + 80,
        coordinate(c, 'posicionY') - minY + 80,
        240,
        header + 8 + rowHeight + 7,
      );
      final id = c['id'] as String;
      boxes[id] = rect;
      nodes[id] = UmlNode(
        rect,
        title,
        rows,
        attributes.map((a) => a['identificador'] == true).toList(),
        header,
      );
    }
    contentBounds = boxes.values.reduce((a, b) => a.expandToInclude(b));
    for (final relation in model.relaciones) {
      final from = boxes[relation['claseOrigenId']],
          to = boxes[relation['claseDestinoId']];
      if (from == null || to == null) continue;
      var a = boundary(from, to.center), b = boundary(to, from.center);
      var sourceNormal = normal(from, a), targetNormal = normal(to, b);
      List<Offset> points;
      if (from.center == to.center) {
        a = from.topCenter;
        b = to.centerRight;
        sourceNormal = const Offset(0, -1);
        targetNormal = const Offset(1, 0);
        points = [
          a,
          a + const Offset(0, -40),
          Offset(to.right + 40, a.dy - 40),
          Offset(to.right + 40, b.dy),
          b,
        ];
      } else {
        var distance = 28.0;
        if (sourceNormal == -targetNormal) {
          final gap = sourceNormal.dx != 0
              ? (a.dx - b.dx).abs()
              : (a.dy - b.dy).abs();
          distance = math.min(distance, gap / 2);
        }
        final exit = a + sourceNormal * distance,
            entry = b + targetNormal * distance;
        if (sourceNormal.dx != 0 && targetNormal.dx != 0) {
          final x = sourceNormal == targetNormal
              ? (sourceNormal.dx > 0
                    ? math.max(exit.dx, entry.dx) + 28
                    : math.min(exit.dx, entry.dx) - 28)
              : (exit.dx + entry.dx) / 2;
          points = [a, exit, Offset(x, exit.dy), Offset(x, entry.dy), entry, b];
        } else if (sourceNormal.dy != 0 && targetNormal.dy != 0) {
          final y = sourceNormal == targetNormal
              ? (sourceNormal.dy > 0
                    ? math.max(exit.dy, entry.dy) + 28
                    : math.min(exit.dy, entry.dy) - 28)
              : (exit.dy + entry.dy) / 2;
          points = [a, exit, Offset(exit.dx, y), Offset(entry.dx, y), entry, b];
        } else {
          points = [
            a,
            exit,
            sourceNormal.dx != 0
                ? Offset(entry.dx, exit.dy)
                : Offset(exit.dx, entry.dy),
            entry,
            b,
          ];
        }
      }
      final labels = <UmlLabel>[];
      final type = normalizarTipoRelacion(relation['tipo'] as String?);
      final edge = UmlEdge(
        type,
        points,
        sourceNormal,
        targetNormal,
        labels,
        null,
      );
      for (final (value, point, direction) in [
        (relation['multiplicidadOrigen'], a, sourceNormal),
        (relation['multiplicidadDestino'], b, targetNormal),
      ]) {
        if (value != null && '$value'.trim().isNotEmpty) {
          final perpendicular = Offset(-direction.dy, direction.dx);
          labels.add(
            _label(
              '$value',
              point + direction * 22 + perpendicular * 14,
              perpendicular,
            ),
          );
        }
      }
      final name = (relation['nombre'] as String? ?? '').trim();
      if (name.isNotEmpty) {
        labels.add(
          _label(
            name,
            edge.middle + const Offset(0, -16),
            const Offset(0, -1),
            size: 12,
          ),
        );
      }
      final association = boxes[relation['claseAsociacionId']];
      edges.add(
        UmlEdge(
          type,
          points,
          sourceNormal,
          targetNormal,
          labels,
          association == null ? null : boundary(association, edge.middle),
        ),
      );
      for (final point in points) {
        contentBounds = contentBounds.expandToInclude(
          Rect.fromCircle(center: point, radius: 2),
        );
      }
      for (final label in labels) {
        contentBounds = contentBounds.expandToInclude(label.bounds);
      }
    }
    size = Size(
      math.max(1, contentBounds.right + 80),
      math.max(1, contentBounds.bottom + 80),
    );
  }

  UmlLabel _label(
    String value,
    Offset center,
    Offset away, {
    double size = 13,
  }) {
    final painter = _text(value, bold: true, size: size, width: 260);
    var rect = Rect.fromCenter(
      center: center,
      width: painter.width + 10,
      height: painter.height + 4,
    );
    // Las etiquetas se dibujan fuera de las cajas, incluso si la relación llega por la izquierda.
    for (
      var i = 0;
      i < 40 && boxes.values.any((box) => box.inflate(2).overlaps(rect));
      i++
    ) {
      rect = rect.shift(away * 8);
    }
    return UmlLabel(value, painter, rect);
  }

  static Offset boundary(Rect rect, Offset target) {
    final d = target - rect.center;
    if (d.distance < 0.01) return rect.topCenter;
    final scale =
        1 /
        math.max(d.dx.abs() / (rect.width / 2), d.dy.abs() / (rect.height / 2));
    return rect.center + d * scale;
  }

  static Offset normal(Rect rect, Offset point) {
    final sides = [
      (rect.left - point.dx).abs(),
      (rect.right - point.dx).abs(),
      (rect.top - point.dy).abs(),
      (rect.bottom - point.dy).abs(),
    ];
    final side = sides.indexOf(sides.reduce(math.min));
    return const [
      Offset(-1, 0),
      Offset(1, 0),
      Offset(0, -1),
      Offset(0, 1),
    ][side];
  }
}

class UmlPainter extends CustomPainter {
  final Modelo model;
  final DiagramLayout layout;
  UmlPainter(this.model, this.layout);

  void _path(
    Canvas canvas,
    List<Offset> points,
    Paint paint, {
    bool dashed = false,
  }) {
    final path = Path()..moveTo(points.first.dx, points.first.dy);
    for (final point in points.skip(1)) {
      path.lineTo(point.dx, point.dy);
    }
    if (!dashed) {
      canvas.drawPath(path, paint);
      return;
    }
    for (final metric in path.computeMetrics()) {
      for (double d = 0; d < metric.length; d += 14) {
        canvas.drawPath(
          metric.extractPath(d, math.min(d + 8, metric.length)),
          paint,
        );
      }
    }
  }

  @override
  void paint(Canvas canvas, Size size) {
    final stroke = Paint()
      ..color = _line
      ..strokeWidth = 1.5
      ..style = PaintingStyle.stroke;
    for (final edge in layout.edges) {
      _path(canvas, edge.points, stroke, dashed: edge.dashed);
      final a = edge.points.first, b = edge.points.last;
      final input = -edge.targetNormal, output = edge.sourceNormal;
      final perpIn = Offset(-input.dy, input.dx),
          perpOut = Offset(-output.dy, output.dx);
      List<Offset>? marker;
      if (edge.tipo == 'HERENCIA' || edge.tipo == 'REALIZACION') {
        marker = [
          b,
          b - input * 18 + perpIn * 10,
          b - input * 18 - perpIn * 10,
        ];
      } else if (edge.tipo == 'AGREGACION' || edge.tipo == 'COMPOSICION') {
        marker = [
          a,
          a + output * 10 + perpOut * 7,
          a + output * 20,
          a + output * 10 - perpOut * 7,
        ];
      } else if (edge.tipo == 'DEPENDENCIA') {
        _path(canvas, [
          b - input * 14 + perpIn * 7,
          b,
          b - input * 14 - perpIn * 7,
        ], stroke);
      }
      if (marker != null) {
        final path = Path()..moveTo(marker.first.dx, marker.first.dy);
        for (final point in marker.skip(1)) {
          path.lineTo(point.dx, point.dy);
        }
        path.close();
        canvas.drawPath(
          path,
          Paint()..color = edge.tipo == 'COMPOSICION' ? _line : Colors.white,
        );
        canvas.drawPath(path, stroke);
      }
      if (edge.associationEnd != null) {
        _path(
          canvas,
          [edge.middle, edge.associationEnd!],
          stroke,
          dashed: true,
        );
      }
    }
    for (final node in layout.nodes.values) {
      final box = node.box;
      final outline = RRect.fromRectAndRadius(box, const Radius.circular(3));
      canvas.drawRRect(outline, Paint()..color = Colors.white);
      canvas.drawRRect(
        outline,
        Paint()
          ..color = _ink
          ..strokeWidth = 1
          ..style = PaintingStyle.stroke,
      );
      node.title.paint(
        canvas,
        Offset(box.center.dx - node.title.width / 2, box.top + 10),
      );
      canvas.drawLine(
        Offset(box.left + 10, box.top + node.header),
        Offset(box.right - 10, box.top + node.header),
        Paint()
          ..color = const Color(0xffd1d5db)
          ..strokeWidth = 1,
      );
      var y = box.top + node.header + 8;
      for (var i = 0; i < node.rows.length; i++) {
        final key = i < node.keys.length && node.keys[i];
        if (key) {
          final paint = Paint()
            ..color = _ink
            ..strokeWidth = 1.5
            ..style = PaintingStyle.stroke;
          final center = Offset(box.left + 15, y + 7);
          canvas.drawCircle(center, 3, paint);
          canvas.drawLine(
            center + const Offset(3, 0),
            center + const Offset(10, 0),
            paint,
          );
          canvas.drawLine(
            center + const Offset(8, 0),
            center + const Offset(8, 3),
            paint,
          );
        }
        node.rows[i].paint(canvas, Offset(box.left + 10 + (key ? 18 : 0), y));
        y += node.rows[i].height + 5;
      }
    }
    // Como EdgeLabelRenderer en la web, las etiquetas quedan sobre las aristas y los nodos.
    for (final edge in layout.edges) {
      for (final label in edge.labels) {
        canvas.drawRRect(
          RRect.fromRectAndRadius(label.bounds, const Radius.circular(4)),
          Paint()..color = const Color(0xf2ffffff),
        );
        label.painter.paint(canvas, label.bounds.topLeft + const Offset(5, 2));
      }
    }
  }

  @override
  bool shouldRepaint(covariant UmlPainter oldDelegate) =>
      oldDelegate.layout != layout;
}
