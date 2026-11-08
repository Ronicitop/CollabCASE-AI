import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'models.dart';
import 'push_service.dart';
import 'repository.dart';
import 'screens.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  runApp(CollabCaseApp(repository: Repository(prefs)));
}

class CollabCaseApp extends StatefulWidget {
  final Repository repository;
  final bool enablePush;
  const CollabCaseApp({
    super.key,
    required this.repository,
    this.enablePush = true,
  });
  @override
  State<CollabCaseApp> createState() => _CollabCaseAppState();
}

class _CollabCaseAppState extends State<CollabCaseApp>
    with WidgetsBindingObserver {
  final navigator = GlobalKey<NavigatorState>();
  late final PushService push;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    push = PushService(widget.repository, (target) {
      navigator.currentState?.pushNamed('/proyecto', arguments: target);
    });
    if (widget.enablePush) {
      WidgetsBinding.instance.addPostFrameCallback((_) => push.start());
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && widget.enablePush) {
      push.configured ? push.refreshPermission() : push.start();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    push.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'CollabCASE AI',
    debugShowCheckedModeBanner: false,
    navigatorKey: navigator,
    locale: const Locale('es'),
    supportedLocales: const [Locale('es')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff087f8c)),
      scaffoldBackgroundColor: const Color(0xfff5f8fa),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xff102f40),
        foregroundColor: Colors.white,
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        margin: const EdgeInsets.symmetric(vertical: 6),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      ),
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(),
        filled: true,
      ),
    ),
    home: ProjectsScreen(repository: widget.repository, push: push),
    onGenerateRoute: (settings) {
      if (settings.name == '/proyecto') {
        final target = settings.arguments! as PushTarget;
        return MaterialPageRoute<void>(
          builder: (_) =>
              ProjectScreen(repository: widget.repository, target: target),
          settings: settings,
        );
      }
      return null;
    },
  );
}
