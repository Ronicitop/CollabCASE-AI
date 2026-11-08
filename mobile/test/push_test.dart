import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:collabcase_mobile/models.dart';
import 'package:collabcase_mobile/push_service.dart';
import 'package:collabcase_mobile/repository.dart';

class FakeTopics implements TopicClient {
  final active = <String>{};
  bool fail = false;
  @override
  Future<void> subscribe(String topic) async {
    if (fail) throw Exception('sin red');
    active.add(topic);
  }

  @override
  Future<void> unsubscribe(String topic) async {
    if (fail) throw Exception('sin red');
    active.remove(topic);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const id = '00000000-0000-0000-0000-000000000001';
  const room = '00000000-0000-0000-0000-000000000002';
  setUp(() => SharedPreferences.setMockInitialValues({}));
  test('valida destino de avisos externos', () {
    expect(PushTarget.parse({'proyectoId': 'http://externo'}), isNull);
    final target = PushTarget.parse({
      'proyectoId': id,
      'sesionId': room,
      'mensajeId': room,
    })!;
    expect(target.proyectoId, id);
    expect(target.sesionId, room);
    expect(target.mensajeId, room);
    expect(
      PushTarget.parse({'proyectoId': id, 'sesionId': '../../'})!.sesionId,
      isNull,
    );
  });
  test(
    'seguir, dejar de seguir offline y reconciliar después de reiniciar',
    () async {
      final prefs = await SharedPreferences.getInstance();
      final repo = Repository(prefs);
      final client = FakeTopics();
      final sync = TopicSync(repo, client);
      await repo.seguir(id, true);
      await sync.sync();
      expect(client.active, contains('proyecto_$id'));
      client.fail = true;
      await repo.seguir(id, false);
      await expectLater(sync.sync(), throwsException);
      client.fail = false;
      await TopicSync(repo, client).sync();
      expect(client.active, isEmpty);
      repo.dispose();
    },
  );
}
