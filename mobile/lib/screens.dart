import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'config.dart';
import 'diagram.dart';
import 'models.dart';
import 'push_service.dart';
import 'repository.dart';

String fecha(String value) {
  final parsed = DateTime.tryParse(value);
  if (parsed == null) return value;
  final date = parsed.toLocal();
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(date.day)}/${two(date.month)}/${date.year} ${two(date.hour)}:${two(date.minute)}';
}

class QueryView<T> extends StatefulWidget {
  final Future<Consulta<T>> Function() load;
  final Widget Function(T) builder;
  final bool enablePullToRefresh;
  const QueryView({
    super.key,
    required this.load,
    required this.builder,
    this.enablePullToRefresh = true,
  });
  @override
  State<QueryView<T>> createState() => _QueryViewState<T>();
}

class _QueryViewState<T> extends State<QueryView<T>>
    with WidgetsBindingObserver {
  Consulta<T>? result;
  Object? error;
  bool loading = false;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) refresh();
  }

  Future<void> refresh() async {
    if (loading) return;
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final next = await widget.load();
      if (mounted) setState(() => result = next);
    } catch (e) {
      if (mounted) {
        setState(() {
          error = e;
          result = null;
        });
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      if (loading) const LinearProgressIndicator(minHeight: 3),
      if (result != null)
        Container(
          width: double.infinity,
          color: result!.desdeCache
              ? Colors.amber.shade100
              : Colors.teal.shade50,
          padding: const EdgeInsets.fromLTRB(16, 4, 4, 4),
          child: Row(
            children: [
              Icon(
                result!.desdeCache ? Icons.cloud_off : Icons.cloud_done,
                size: 18,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  '${result!.desdeCache ? "Copia guardada · sin actualizar" : "Consultado"}\n${fecha(result!.guardadoEn.toIso8601String())}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
              IconButton(
                onPressed: loading ? null : refresh,
                icon: const Icon(Icons.refresh),
                tooltip: 'Actualizar',
              ),
            ],
          ),
        ),
      Expanded(
        child: RefreshIndicator(
          onRefresh: refresh,
          notificationPredicate: (notification) =>
              widget.enablePullToRefresh &&
              defaultScrollNotificationPredicate(notification),
          child: error != null
              ? ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(24),
                  children: [
                    const Icon(Icons.wifi_off, size: 48),
                    const SizedBox(height: 16),
                    Text(
                      error.toString().replaceFirst('Exception: ', ''),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 16),
                    FilledButton.icon(
                      onPressed: refresh,
                      icon: const Icon(Icons.refresh),
                      label: const Text('Volver a intentar'),
                    ),
                  ],
                )
              : result != null
              ? widget.builder(result!.valor)
              : ListView(
                  children: const [
                    Padding(
                      padding: EdgeInsets.all(32),
                      child: Center(child: Text('Consultando…')),
                    ),
                  ],
                ),
        ),
      ),
    ],
  );
}

class ProjectsScreen extends StatefulWidget {
  final Repository repository;
  final PushService push;
  const ProjectsScreen({
    super.key,
    required this.repository,
    required this.push,
  });
  @override
  State<ProjectsScreen> createState() => _ProjectsScreenState();
}

class _ProjectsScreenState extends State<ProjectsScreen> {
  String search = '';
  bool onlyFollowed = false;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('CollabCASE AI'),
      actions: [
        IconButton(
          icon: const Icon(Icons.settings_outlined),
          tooltip: 'Ajustes',
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute<void>(
              builder: (_) => SettingsScreen(push: widget.push),
            ),
          ),
        ),
      ],
    ),
    body: ListenableBuilder(
      listenable: widget.repository,
      builder: (context, _) => Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Tus proyectos, donde estés',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 4),
                const Text(
                  'Consulta los modelos y mantente al día con tu equipo.',
                ),
                const SizedBox(height: 16),
                TextField(
                  onChanged: (v) => setState(() => search = v),
                  decoration: const InputDecoration(
                    labelText: 'Buscar proyectos',
                    prefixIcon: Icon(Icons.search),
                  ),
                ),
                const SizedBox(height: 8),
                FilterChip(
                  label: const Text('Solo proyectos seguidos'),
                  selected: onlyFollowed,
                  avatar: const Icon(Icons.star_outline),
                  onSelected: (v) => setState(() => onlyFollowed = v),
                ),
              ],
            ),
          ),
          Expanded(
            child: QueryView<List<Proyecto>>(
              load: widget.repository.proyectos,
              builder: (projects) {
                final filtered = projects
                    .where(
                      (p) =>
                          '${p.nombre} ${p.descripcion}'.toLowerCase().contains(
                            search.toLowerCase(),
                          ) &&
                          (!onlyFollowed ||
                              widget.repository.seguidos.contains(p.id)),
                    )
                    .toList();
                return ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                  children: filtered.isEmpty
                      ? [
                          const EmptyState(
                            'No hay proyectos para mostrar.',
                            Icons.folder_open,
                          ),
                        ]
                      : filtered
                            .map(
                              (p) => Card(
                                child: ListTile(
                                  contentPadding: const EdgeInsets.symmetric(
                                    horizontal: 16,
                                    vertical: 10,
                                  ),
                                  leading: CircleAvatar(
                                    child: Text(
                                      p.nombre.isEmpty
                                          ? 'U'
                                          : p.nombre.characters.first
                                                .toUpperCase(),
                                    ),
                                  ),
                                  title: Text(
                                    p.nombre,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  subtitle: Text(
                                    p.descripcion.isEmpty
                                        ? 'Sin descripción'
                                        : p.descripcion,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  trailing: FollowButton(
                                    repository: widget.repository,
                                    id: p.id,
                                  ),
                                  onTap: () => Navigator.pushNamed(
                                    context,
                                    '/proyecto',
                                    arguments: PushTarget(p.id, null, null),
                                  ),
                                ),
                              ),
                            )
                            .toList(),
                );
              },
            ),
          ),
        ],
      ),
    ),
  );
}

class FollowButton extends StatelessWidget {
  final Repository repository;
  final String id;
  const FollowButton({super.key, required this.repository, required this.id});
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: repository,
    builder: (context, _) {
      final following = repository.seguidos.contains(id);
      return IconButton(
        isSelected: following,
        tooltip: following ? 'Dejar de seguir' : 'Seguir proyecto',
        icon: const Icon(Icons.star_outline),
        selectedIcon: const Icon(Icons.star, color: Color(0xffb77b00)),
        onPressed: () async {
          try {
            await repository.seguir(id, !following);
          } catch (_) {
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('No se pudo guardar el seguimiento.'),
                ),
              );
            }
          }
        },
      );
    },
  );
}

class ProjectScreen extends StatefulWidget {
  final Repository repository;
  final PushTarget target;
  const ProjectScreen({
    super.key,
    required this.repository,
    required this.target,
  });
  @override
  State<ProjectScreen> createState() => _ProjectScreenState();
}

class _ProjectScreenState extends State<ProjectScreen> {
  @override
  void initState() {
    super.initState();
    final target = widget.target;
    if (target.mensajeId != null || target.sesionId != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          Navigator.push(
            context,
            MaterialPageRoute<void>(
              builder: (_) => MessagesScreen(
                repository: widget.repository,
                proyectoId: target.proyectoId,
                mensajeId: target.mensajeId,
              ),
            ),
          );
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Detalle del proyecto'),
      actions: [
        FollowButton(
          repository: widget.repository,
          id: widget.target.proyectoId,
        ),
      ],
    ),
    body: QueryView<Proyecto>(
      load: () => widget.repository.proyecto(widget.target.proyectoId),
      builder: (p) => ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(20),
        children: [
          const Icon(
            Icons.account_tree_outlined,
            size: 52,
            color: Color(0xff087f8c),
          ),
          const SizedBox(height: 16),
          Text(p.nombre, style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 12),
          Text(
            p.descripcion.isEmpty
                ? 'Este proyecto aún no tiene descripción.'
                : p.descripcion,
          ),
          const SizedBox(height: 16),
          Text(
            'Creado: ${fecha(p.creadoEn)}\nActualizado: ${fecha(p.actualizadoEn)}',
          ),
          const SizedBox(height: 24),
          Card(
            child: ListTile(
              leading: const Icon(Icons.schema_outlined),
              title: const Text('Diagrama UML'),
              subtitle: const Text('Explorar clases, atributos y relaciones'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute<void>(
                  builder: (_) => ModelScreen(
                    repository: widget.repository,
                    proyectoId: p.id,
                  ),
                ),
              ),
            ),
          ),
          Card(
            child: ListTile(
              leading: const Icon(Icons.forum_outlined),
              title: const Text('Chat del proyecto'),
              subtitle: const Text(
                'Historial compartido de todas las sesiones',
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute<void>(
                  builder: (_) => MessagesScreen(
                    repository: widget.repository,
                    proyectoId: p.id,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 20),
          OutlinedButton.icon(
            onPressed: () => openWeb(context),
            icon: const Icon(Icons.open_in_browser),
            label: const Text('Abrir plataforma web'),
          ),
        ],
      ),
    ),
  );
}

class ModelScreen extends StatelessWidget {
  final Repository repository;
  final String proyectoId;
  const ModelScreen({
    super.key,
    required this.repository,
    required this.proyectoId,
  });
  @override
  Widget build(BuildContext context) => DefaultTabController(
    length: 2,
    child: Scaffold(
      appBar: AppBar(
        title: const Text('Modelo UML'),
        bottom: const TabBar(
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white70,
          tabs: [
            Tab(text: 'Diagrama'),
            Tab(text: 'Contenido'),
          ],
        ),
      ),
      body: QueryView<Modelo>(
        enablePullToRefresh: false,
        load: () => repository.modelo(proyectoId),
        builder: (model) => TabBarView(
          physics: const NeverScrollableScrollPhysics(),
          children: [
            Diagram(modelo: model),
            ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  'Versión ${model.version} · ${model.clases.length} clases · ${model.relaciones.length} relaciones',
                ),
                if (model.clases.isEmpty)
                  const EmptyState(
                    'El modelo aún no tiene clases.',
                    Icons.schema_outlined,
                  ),
                for (final c in model.clases)
                  Card(
                    child: ExpansionTile(
                      title: Text(c['nombre'] as String),
                      initiallyExpanded: true,
                      children: [
                        for (final a in List<Json>.from(
                          c['atributos'] as List? ?? [],
                        ))
                          ListTile(
                            dense: true,
                            leading: Icon(
                              a['identificador'] == true
                                  ? Icons.key
                                  : Icons.label_outline,
                              size: 18,
                            ),
                            title: Text('${a['nombre']}: ${a['tipoDato']}'),
                            subtitle: Text(
                              a['permiteNulo'] == true
                                  ? 'Opcional'
                                  : 'Obligatorio',
                            ),
                          ),
                      ],
                    ),
                  ),
                const SizedBox(height: 16),
                Text(
                  'Relaciones',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                if (model.relaciones.isEmpty) const Text('Sin relaciones.'),
                for (final r in model.relaciones)
                  Card(
                    child: ListTile(
                      title: Text(
                        '${className(model, r['claseOrigenId'])} → ${className(model, r['claseDestinoId'])}',
                      ),
                      subtitle: Text(
                        '${r['tipo']} · ${r['nombre'] ?? ""}\n${r['multiplicidadOrigen'] ?? ""} → ${r['multiplicidadDestino'] ?? ""}${r['claseAsociacionId'] == null ? '' : '\nClase de asociación: ${className(model, r['claseAsociacionId'])}'}',
                      ),
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

String className(Modelo model, dynamic id) =>
    model.clases.where((c) => c['id'] == id).firstOrNull?['nombre']
        as String? ??
    'Clase no disponible';

class MessagesScreen extends StatelessWidget {
  final Repository repository;
  final String proyectoId;
  final String? mensajeId;
  const MessagesScreen({
    super.key,
    required this.repository,
    required this.proyectoId,
    this.mensajeId,
  });
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Chat del proyecto · lectura')),
    body: QueryView<List<Mensaje>>(
      load: () => repository.mensajesProyecto(proyectoId),
      builder: (messages) {
        final selected = messages.where((m) => m.id == mensajeId).firstOrNull;
        Widget card(Mensaje m, {bool highlighted = false}) => Card(
          color: highlighted ? Colors.amber.shade100 : null,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${m.tipo == "IA" ? "✦ " : ""}${m.remitente}',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                SelectableText(m.contenido),
                const SizedBox(height: 8),
                Text(
                  fecha(m.fecha),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        );
        if (messages.isEmpty) {
          return ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: const [
              EmptyState(
                'Todavía no hay mensajes en este proyecto.',
                Icons.chat_bubble_outline,
              ),
            ],
          );
        }
        final extra = selected == null ? 0 : 1;
        return ListView.builder(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          itemCount: messages.length + extra,
          itemBuilder: (context, index) {
            if (extra == 1 && index == 0) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Mensaje del aviso'),
                  card(selected!, highlighted: true),
                  const Divider(),
                  const Text('Historial del proyecto'),
                ],
              );
            }
            return card(messages[index - extra]);
          },
        );
      },
    ),
  );
}

class SettingsScreen extends StatelessWidget {
  final PushService push;
  const SettingsScreen({super.key, required this.push});
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Ajustes')),
    body: ListenableBuilder(
      listenable: push,
      builder: (context, _) => ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Icon(Icons.notifications_active_outlined, size: 48),
          const SizedBox(height: 16),
          Text(
            'Novedades de tus proyectos',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(push.status),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: AppConfig.firebaseEnabled
                ? push.requestPermission
                : null,
            child: const Text('Activar / revisar notificaciones'),
          ),
          const SizedBox(height: 24),
          const Text(
            'La estrella de cada proyecto activa su seguimiento. Los avisos necesitan conexión a Internet.',
          ),
          const Divider(height: 40),
          const Text('Conexión', style: TextStyle(fontWeight: FontWeight.bold)),
          const SelectableText(
            'API: ${AppConfig.apiUrl}\nWeb: ${AppConfig.webUrl}',
          ),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: () => openWeb(context),
            icon: const Icon(Icons.open_in_browser),
            label: const Text('Abrir web'),
          ),
          const SizedBox(height: 24),
          const Text(
            'CollabCASE AI\nConsulta móvil de proyectos y modelos UML · v1.0',
          ),
        ],
      ),
    ),
  );
}

Future<void> openWeb(BuildContext context) async {
  try {
    if (await launchUrl(
      Uri.parse(AppConfig.webUrl),
      mode: LaunchMode.externalApplication,
    )) {
      return;
    }
  } catch (_) {
    /* Informar sin interrumpir la consulta. */
  }
  if (context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('No se pudo abrir el navegador.')),
    );
  }
}

class EmptyState extends StatelessWidget {
  final String text;
  final IconData icon;
  const EmptyState(this.text, this.icon, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(32),
    child: Column(
      children: [
        Icon(icon, size: 48, color: Colors.blueGrey),
        const SizedBox(height: 12),
        Text(text, textAlign: TextAlign.center),
      ],
    ),
  );
}
