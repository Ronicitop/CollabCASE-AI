import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'config.dart';
import 'models.dart';

class Consulta<T> {
  final T valor;
  final DateTime guardadoEn;
  final bool desdeCache;
  Consulta(this.valor, this.guardadoEn, this.desdeCache);
}

class ApiException implements Exception {
  final int status;
  ApiException(this.status);
  @override
  String toString() => status == 404
      ? 'El recurso ya no está disponible.'
      : 'No se pudo consultar el servidor ($status).';
}

class Repository extends ChangeNotifier {
  final SharedPreferences prefs;
  final http.Client client;
  final String baseUrl;
  Repository(
    this.prefs, {
    http.Client? client,
    String baseUrl = AppConfig.apiUrl,
  }) : client = client ?? http.Client(),
       baseUrl = baseUrl.replaceAll(RegExp(r'/+$'), '');

  String get _prefix => 'collabcase:$baseUrl:';
  Set<String> get seguidos =>
      (prefs.getStringList('${_prefix}seguidos') ?? []).toSet();
  Future<void> seguir(String id, bool valor) async {
    final ids = seguidos;
    valor ? ids.add(id) : ids.remove(id);
    await prefs.setStringList('${_prefix}seguidos', ids.toList());
    notifyListeners();
  }

  Future<Consulta<T>> _get<T>(String path, T Function(dynamic) decode) async {
    final key = '$_prefix$path';
    dynamic json;
    try {
      final response = await client
          .get(
            Uri.parse('$baseUrl$path'),
            headers: {'Accept': 'application/json'},
          )
          .timeout(const Duration(seconds: 12));
      if (response.statusCode != 200) throw ApiException(response.statusCode);
      json = jsonDecode(utf8.decode(response.bodyBytes));
      final value = decode(json);
      final now = DateTime.now();
      // A storage failure must not turn a successful HTTP response into an offline result.
      try {
        await prefs.setString(
          key,
          jsonEncode({'date': now.toIso8601String(), 'data': json}),
        );
      } catch (_) {
        /* Consulta válida, sin copia local disponible. */
      }
      return Consulta(value, now, false);
    } catch (error) {
      if (error is ApiException && error.status >= 400 && error.status < 500) {
        await prefs.remove(key);
        rethrow;
      }
      final cached = prefs.getString(key);
      if (cached != null) {
        try {
          final record = jsonDecode(cached) as Json;
          return Consulta(
            decode(record['data']),
            DateTime.parse(record['date'] as String),
            true,
          );
        } catch (_) {
          await prefs.remove(key);
        }
      }
      if (error is ApiException) rethrow;
      throw Exception(
        'No se pudo conectar y no hay una copia guardada. Revisa el Wi-Fi y vuelve a intentar.',
      );
    }
  }

  Future<Consulta<List<Proyecto>>> proyectos() => _get(
    '/api/proyectos',
    (json) => (json as List).map((v) => Proyecto.fromJson(v as Json)).toList(),
  );
  Future<Consulta<Proyecto>> proyecto(String id) =>
      _get('/api/proyectos/$id', (v) => Proyecto.fromJson(v as Json));
  Future<Consulta<Modelo>> modelo(String id) => _get(
    '/api/modelos-diagrama/proyecto/$id/completo',
    (v) => Modelo.fromJson(v as Json),
  );
  Future<Consulta<List<Mensaje>>> mensajesProyecto(String proyectoId) => _get(
    '/api/sala/proyecto/$proyectoId/mensajes',
    (json) => (json as List).map((v) => Mensaje.fromJson(v as Json)).toList(),
  );

  @override
  void dispose() {
    client.close();
    super.dispose();
  }
}
