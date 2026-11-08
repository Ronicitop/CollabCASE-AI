typedef Json = Map<String, dynamic>;

class Proyecto {
  final String id, nombre, descripcion, creadoEn, actualizadoEn;
  Proyecto.fromJson(Json json)
    : id = json['id'] as String,
      nombre = json['nombre'] as String,
      descripcion = json['descripcion'] as String? ?? '',
      creadoEn = json['creadoEn'] as String? ?? '',
      actualizadoEn = json['actualizadoEn'] as String? ?? '';
}

class Modelo {
  final List<Json> clases, relaciones;
  final int version;
  Modelo.fromJson(Json json)
    : clases = List<Json>.from(json['clases'] as List? ?? []),
      relaciones = List<Json>.from(json['relaciones'] as List? ?? []),
      version = (json['version'] as num?)?.toInt() ?? 0;
}

class Mensaje {
  final String id, remitente, contenido, fecha, tipo;
  Mensaje.fromJson(Json json)
    : id = json['id'] as String,
      remitente = json['remitenteNombre'] as String? ?? 'Participante',
      contenido = json['contenido'] as String? ?? '',
      fecha = json['fechaHora'] as String? ?? '',
      tipo = json['tipoEmisor'] as String? ?? 'USUARIO';
}

class PushTarget {
  final String proyectoId;
  final String? sesionId, mensajeId;
  PushTarget(this.proyectoId, this.sesionId, this.mensajeId);
  static PushTarget? parse(Map<String, dynamic> data) {
    final id = data['proyectoId'];
    final uuid = RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
    );
    if (id is! String || !uuid.hasMatch(id)) return null;
    String? optionalId(Object? value) =>
        value is String && uuid.hasMatch(value) ? value : null;
    return PushTarget(
      id,
      optionalId(data['sesionId']),
      optionalId(data['mensajeId']),
    );
  }
}
