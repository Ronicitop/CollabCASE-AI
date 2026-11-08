class AppConfig {
  static const apiUrl = String.fromEnvironment(
    'API_URL',
    defaultValue: 'http://192.168.0.9:8080',
  );
  static const webUrl = String.fromEnvironment(
    'WEB_URL',
    defaultValue: 'http://192.168.0.9:3000',
  );
  static const firebaseEnabled = bool.fromEnvironment(
    'FIREBASE_ENABLED',
    defaultValue: true,
  );
}
