// SilverChat — Flutter (Android)
// Вкладки: «Чаты» (WebSocket) и «Маркет» (Сильверы + юзернеймы)
// Бэкенд: твой Node-сервер (REST + WS). Без нативных модулей, кроме shared_preferences.
//
// СТАРТ (безопасный):
// - глобальные обработчики ошибок (FlutterError + PlatformDispatcher) —
//   любые JS... любые Dart-ошибки не валят процесс, а показываются/log'ятся
// - все сетевые вызовы: try/catch + таймаут 15 с
// - WebSocket: reconnect с backoff, защита от двойных подключений
// - данные: null-guards на каждом fromJson-потреблении

import 'dart:async';
import 'dart:convert';
import 'dart:io' show WebSocket;
import 'dart:math';
import 'dart:ui' show PlatformDispatcher;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/* ============================================================
   КОНФИГ — поменяй под свой сервер
   ============================================================ */
const String kApiBase = 'https://silverchat-production.up.railway.app';
final String kWsBase = kApiBase.replaceFirst(RegExp(r'^http'), 'ws');

/* ---------------- Тема (Fragment / Telegram) ---------------- */
const Color kBg = Color(0xFF0E1621);
const Color kPanel = Color(0xFF17212B);
const Color kBorder = Color(0xFF243344);
const Color kText = Color(0xFFF5F5F5);
const Color kText2 = Color(0xFF707579);
const Color kAccent = Color(0xFF5288C1);
const Color kAccentBright = Color(0xFF3390EC);
const Color kGold = Color(0xFFF5C242);
const Color kMsgIn = Color(0xFF182533);
const Color kMsgOut = Color(0xFF2B5278);
const Color kGreen = Color(0xFF31A24C);
const Color kRed = Color(0xFFE74C3C);

String _two(int n) => n.toString().padLeft(2, '0');
String fmtTime(Object? iso) {
  final d = DateTime.tryParse(iso?.toString() ?? '');
  if (d == null) return '';
  return '${_two(d.hour)}:${_two(d.minute)}';
}
String fmtNum(Object? n) {
  final v = n is int ? n : (int.tryParse(n?.toString() ?? '0') ?? 0);
  final s = v.abs().toString();
  final buf = StringBuffer();
  for (var i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 3 == 0) buf.write(',');
    buf.write(s[i]);
  }
  return v < 0 ? '-$buf' : buf.toString();
}
String fmtSilver(Object? n) => '💰 ${fmtNum(n)} Silver';

/* ============================================================
   МОДЕЛИ
   ============================================================ */
class User {
  final String username;
  final String nickname;
  final String avatarUrl;
  final int balance;
  final bool isAdmin;
  final bool isDev;
  final bool isPremium;
  final bool isVerified;
  User({
    required this.username,
    required this.nickname,
    required this.avatarUrl,
    required this.balance,
    required this.isAdmin,
    required this.isDev,
    required this.isPremium,
    required this.isVerified,
  });
  factory User.fromJson(Map<String, dynamic> j) => User(
        username: (j['username'] ?? '').toString(),
        nickname: (j['nickname'] ?? j['username'] ?? '').toString(),
        avatarUrl: (j['avatar_url'] ?? '').toString(),
        balance: j['balance_silvers'] is int ? j['balance_silvers'] as int : 0,
        isAdmin: j['is_admin'] == 1 || j['is_admin'] == true,
        isDev: j['is_dev'] == 1 || j['is_dev'] == true,
        isPremium: j['is_premium'] == 1 || j['is_premium'] == true,
        isVerified: j['is_verified'] == 1 || j['is_verified'] == true,
      );
}

class ChatItem {
  final String username;
  final String nickname;
  final String avatarUrl;
  final int unread;
  final String lastText;
  final Object? lastTime;
  final String lastSender;
  final bool lastRead;
  final Object? lastSeen;
  final bool isVerified;
  final bool isPremium;
  ChatItem({
    required this.username,
    required this.nickname,
    required this.avatarUrl,
    required this.unread,
    required this.lastText,
    required this.lastTime,
    required this.lastSender,
    required this.lastRead,
    required this.lastSeen,
    required this.isVerified,
    required this.isPremium,
  });
  factory ChatItem.fromJson(Map<String, dynamic> j) {
    final lm = (j['last_msg'] is Map<String, dynamic>)
        ? j['last_msg'] as Map<String, dynamic>
        : <String, dynamic>{};
    return ChatItem(
      username: (j['username'] ?? '').toString(),
      nickname: (j['nickname'] ?? j['username'] ?? '').toString(),
      avatarUrl: (j['avatar_url'] ?? '').toString(),
      unread: j['unread_count'] is int ? j['unread_count'] as int : 0,
      lastText: (lm['text'] ?? '').toString(),
      lastTime: lm['time'],
      lastSender: (lm['sender'] ?? '').toString(),
      lastRead: lm['is_read'] == 1,
      lastSeen: j['last_seen'],
      isVerified: j['is_verified'] == 1,
      isPremium: j['is_premium'] == 1,
    );
  }
}

class Msg {
  final int id;
  final String type;
  final String sender;
  final String receiver;
  final String text;
  final Object? createdAt;
  final bool isRead;
  Msg({
    required this.id,
    required this.type,
    required this.sender,
    required this.receiver,
    required this.text,
    required this.createdAt,
    required this.isRead,
  });
  factory Msg.fromJson(Map<String, dynamic> j) => Msg(
        id: j['id'] is int ? j['id'] as int : int.tryParse(j['id']?.toString() ?? '0') ?? 0,
        type: (j['type'] ?? 'private').toString(),
        sender: (j['sender'] ?? '').toString(),
        receiver: (j['receiver'] ?? '').toString(),
        text: (j['text'] ?? '').toString(),
        createdAt: j['created_at'],
        isRead: j['is_read'] == 1,
      );
}

class Listing {
  final String tag;
  final int price;
  final String owner;
  Listing({required this.tag, required this.price, required this.owner});
  factory Listing.fromJson(Map<String, dynamic> j) => Listing(
        tag: (j['tag'] ?? '').toString(),
        price: j['price'] is int ? j['price'] as int : (int.tryParse(j['price']?.toString() ?? '0') ?? 0),
        owner: (j['owner'] ?? '').toString(),
      );
}

/* ============================================================
   API-клиент (таймаут 15 с, безопасный парсинг)
   ============================================================ */
class ApiException implements Exception {
  final String message;
  ApiException(this.message);
  @override
  String toString() => message;
}

class Api {
  /// Возвращает декодированный JSON (Map или List — как отдал сервер).
  static Future<dynamic> _req(
    String method,
    String path, {
    String? token,
    Map<String, dynamic>? body,
  }) async {
    final headers = <String, String>{'Content-Type': 'application/json'};
    if (token != null) headers['Authorization'] = 'Bearer $token';
    final req = http.Request(method, Uri.parse('$kApiBase$path'))
      ..headers.addAll(headers);
    if (body != null) req.body = jsonEncode(body);
    final client = http.Client();
    try {
      final streamed = await client
          .send(req)
          .timeout(const Duration(seconds: 15));
      final resp = await http.Response.fromStream(streamed);
      dynamic data = null;
      if (resp.body.isNotEmpty) {
        try {
          data = jsonDecode(resp.body);
        } catch (_) {}
      }
      if (resp.statusCode < 200 || resp.statusCode >= 300) {
        final err = (data is Map<String, dynamic>) ? data['error'] : null;
        throw ApiException((err ?? 'Ошибка ${resp.statusCode}').toString());
      }
      return data;
    } on TimeoutException {
      throw ApiException('Сервер не отвечает (таймаут)');
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException('Нет связи с сервером. Проверь адрес и интернет.');
    } finally {
      client.close();
    }
  }

  static Future<Map<String, dynamic>> _reqMap(
          String method, String path,
          {String? token, Map<String, dynamic>? body}) =>
      _req(method, path, token: token, body: body)
          .then((d) => (d is Map<String, dynamic>) ? d : <String, dynamic>{});

  static Future<Map<String, dynamic>> auth(
          String username, String nickname, String password) =>
      _reqMap('POST', '/api/auth',
          body: {
            'username': username,
            'nickname': nickname,
            'password': password
          });
  static Future<Map<String, dynamic>> me(String token) =>
      _reqMap('GET', '/api/me', token: token);
  static Future<List<dynamic>> myChats(String token) async {
    final d = await _reqMap('GET', '/api/my_chats', token: token);
    final l = d['chats'];
    return (l is List) ? l : <dynamic>[];
  }
  static Future<List<dynamic>> history(String token, String username) async {
    final d =
        await _reqMap('GET', '/api/history/${Uri.encodeComponent(username)}', token: token);
    final l = d['messages'];
    return (l is List) ? l : <dynamic>[];
  }
  static Future<Map<String, dynamic>> send(
          String token, Map<String, dynamic> body) =>
      _reqMap('POST', '/api/messages', token: token, body: body);
  static Future<void> read(String token, String peer) =>
      _reqMap('POST', '/api/read', token: token, body: {'peer': peer});
  // Сильверы / Маркет
  static Future<Map<String, dynamic>> balance(String token) =>
      _reqMap('GET', '/api/balance', token: token);
  static Future<List<dynamic>> market(String token) async {
    // ВНИМАНИЕ: /api/market отдаёт JSON-массив, а не объект
    final l = await _req('GET', '/api/market', token: token);
    return (l is List) ? l : <dynamic>[];
  }
  static Future<Map<String, dynamic>> marketList(
          String token, String tag, int price) =>
      _reqMap('POST', '/api/market/list', token: token, body: {'tag': tag, 'price': price});
  static Future<Map<String, dynamic>> marketUnlist(String token, String tag) =>
      _reqMap('POST', '/api/market/unlist', token: token, body: {'tag': tag});
  static Future<Map<String, dynamic>> marketBuy(String token, String tag) =>
      _reqMap('POST', '/api/market/buy', token: token, body: {'tag': tag});
  static Future<Map<String, dynamic>> grantSilvers(
          String token, String username, int amount) =>
      _reqMap('POST', '/api/admin/grant_silvers',
          token: token, body: {'username': username, 'amount': amount});
}

/* ============================================================
   WebSocket-шина (broadcast, reconnect, защита)
   ============================================================ */
class WsBus {
  WsBus._();
  static final WsBus instance = WsBus._();

  WebSocket? _ws;
  String? _token;
  bool _connecting = false;
  bool _stopped = true;
  int _retry = 1;
  Timer? _retryTimer;

  final _events = StreamController<dynamic>.broadcast();
  final _conn = StreamController<bool>.broadcast();
  Stream<dynamic> get events => _events.stream;
  Stream<bool> get connectionChanges => _conn.stream;
  bool get connected => _ws != null && !_stopped;

  void start(String token) {
    _token = token;
    _stopped = false;
    _connect();
  }

  void stop() {
    _stopped = true;
    _token = null;
    _retryTimer?.cancel();
    _retryTimer = null;
    try {
      _ws?.close();
    } catch (_) {}
    _ws = null;
  }

  void send(Map<String, dynamic> m) {
    try {
      _ws?.add(jsonEncode(m));
    } catch (_) {}
  }

  void _scheduleRetry() {
    if (_stopped || _token == null) return;
    _retryTimer?.cancel();
    _retryTimer = Timer(Duration(seconds: min(15, _retry)), _connect);
    _retry = min(15, _retry * 2);
  }

  Future<void> _connect() async {
    if (_stopped || _connecting || _token == null) return;
    _connecting = true;
    try {
      final ws = await WebSocket.connect(
          Uri.parse('$kWsBase/ws?token=${Uri.encodeComponent(_token!)}'));
      if (_stopped) {
        try {
          await ws.close();
        } catch (_) {}
        return;
      }
      _ws = ws;
      _retry = 1;
      _conn.add(true);
      ws.listen(
        (data) {
          try {
            final d = jsonDecode(data.toString());
            if (d is Map<String, dynamic>) _events.add(d);
          } catch (_) {}
        },
        onDone: () {
          _conn.add(false);
          _scheduleRetry();
        },
        onError: (Object _) {
          try {
            ws.close();
          } catch (_) {}
        },
        cancelOnError: false,
      );
    } catch (_) {
      _conn.add(false);
      _scheduleRetry();
    } finally {
      _connecting = false;
    }
  }
}

/* ============================================================
   Аватар (фото или инициалы)
   ============================================================ */
class AvatarView extends StatelessWidget {
  final String name;
  final String uri;
  final double size;
  const AvatarView({super.key, required this.name, required this.uri, required this.size});

  @override
  Widget build(BuildContext context) {
    final letter = name.isNotEmpty ? name[0].toUpperCase() : '?';
    Widget initials = ColoredBox(
      color: kAccent,
      child: Center(
        child: Text(letter,
            style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w700,
                fontSize: size * 0.38)),
      ),
    );
    Widget child;
    if (uri.isNotEmpty) {
      child = Image.network(
        uri,
        width: size,
        height: size,
        fit: BoxFit.cover,
        frameBuilder: (c, w, i, e) => e ? SizedBox(width: size, height: size) : w(c),
        errorBuilder: (c, e, s) => initials,
      );
    } else {
      child = initials;
    }
    return ClipOval(
      child: SizedBox(
        width: size,
        height: size,
        child: Stack(fit: StackFit.expand,
            children: [const ColoredBox(color: kAccent), child]),
      ),
    );
  }
}

/* ============================================================
   ЛОГИН
   ============================================================ */
class LoginScreen extends StatefulWidget {
  final Future<void> Function(String token, Map<String, dynamic> user) onLoggedIn;
  const LoginScreen({super.key, required this.onLoggedIn});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _username = TextEditingController();
  final _nickname = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _err;

  @override
  void dispose() {
    _username.dispose();
    _nickname.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _err = null;
    });
    try {
      final d = await Api.auth(
        _username.text.trim().replaceFirst(RegExp(r'^@+'), ''),
        _nickname.text.trim(),
        _password.text,
      );
      final token = (d['token'] ?? '').toString();
      final user = d['user'];
      if (token.isEmpty || user is! Map<String, dynamic>) {
        throw ApiException('Некорректный ответ сервера');
      }
      if (!mounted) return;
      await widget.onLoggedIn(token, user);
    } on ApiException catch (e) {
      if (mounted) setState(() => _err = e.message);
    } catch (_) {
      if (mounted) setState(() => _err = 'Не удалось войти');
    }
    if (mounted) setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: kPanel,
                borderRadius: BorderRadius.circular(22),
                border: Border.all(color: kBorder),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 72,
                    height: 72,
                    decoration: const BoxDecoration(
                        color: kAccent, shape: BoxShape.circle),
                    child: const Icon(Icons.send, color: Colors.white, size: 34),
                  ),
                  const SizedBox(height: 12),
                  const Text('SilverChat',
                      style: TextStyle(
                          color: kText, fontSize: 22, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 4),
                  const Text('Чаты + Маркетплейс юзернеймов',
                      style: TextStyle(color: kText2, fontSize: 12)),
                  const SizedBox(height: 14),
                  if (_err != null)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
                      decoration: BoxDecoration(
                          color: const Color(0xFFE74C3C).withOpacity(0.12),
                          borderRadius: BorderRadius.circular(10)),
                      child: Text(_err!,
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: Color(0xFFFF8A7A), fontSize: 12)),
                    ),
                  const SizedBox(height: 10),
                  _field('Юзернейм', _username, '@username', false),
                  _field('Имя для отображения', _nickname, 'Например: Norså', false),
                  _field('Пароль', _password, '••••••••', true),
                  const SizedBox(height: 6),
                  SizedBox(
                    width: double.infinity,
                    height: 46,
                    child: ElevatedButton(
                      onPressed: _busy ? null : _submit,
                      style: ElevatedButton.styleFrom(
                          backgroundColor: kAccentBright,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12))),
                      child: _busy
                          ? const CircularProgressIndicator(
                              strokeWidth: 2, color: Colors.white)
                          : const Text('Войти / Создать аккаунт',
                              style: TextStyle(fontWeight: FontWeight.w700)),
                    ),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                      '🔑 @silver — супер-админ: выдает Сильверы и права.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: kText2, fontSize: 11)),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  TextField _field(String label, TextEditingController c, String hint, bool pass) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextField(
        controller: c,
        obscureText: pass,
        autocorrect: false,
        style: const TextStyle(color: kText, fontSize: 15),
        decoration: InputDecoration(
          labelText: label,
          hintText: hint,
          labelStyle: const TextStyle(color: kText2, fontSize: 11.5),
          hintStyle: const TextStyle(color: kText2, fontSize: 13),
          filled: true,
          fillColor: kBg,
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: kBorder)),
          enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: kBorder)),
          focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: kAccentBright)),
        ),
      ),
    );
  }
}

/* ============================================================
   ROOT — сессия, WS-жизненный цикл
   ============================================================ */
class RootScreen extends StatefulWidget {
  const RootScreen({super.key});
  @override
  State<RootScreen> createState() => _RootScreenState();
}

class _RootScreenState extends State<RootScreen> {
  String? _token;
  User? _user;
  bool _booting = true;

  @override
  void initState() {
    super.initState();
    _boot();
  }

  Future<void> _boot() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final t = prefs.getString('silver_token');
      if (t != null && t.isNotEmpty) {
        try {
          final d = await Api.me(t);
          final u = d['user'];
          if (u is Map<String, dynamic> && u['username'] != null) {
            _token = t;
            _user = User.fromJson(u);
            WsBus.instance.start(t);
            if (mounted) setState(() => _booting = false);
            return;
          }
        } catch (_) {}
        try {
          await prefs.remove('silver_token');
          await prefs.remove('silver_user');
        } catch (_) {}
      }
    } catch (_) {}
    if (mounted) setState(() => _booting = false);
  }

  Future<void> _onLoggedIn(String token, Map<String, dynamic> user) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('silver_token', token);
      await prefs.setString('silver_user', jsonEncode(user));
    } catch (_) {}
    _token = token;
    _user = User.fromJson(user);
    WsBus.instance.start(token);
    if (mounted) setState(() => _booting = false);
  }

  Future<void> _logout() async {
    WsBus.instance.stop();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove('silver_token');
      await prefs.remove('silver_user');
    } catch (_) {}
    if (mounted) setState(() {
      _token = null;
      _user = null;
      _booting = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_booting) {
      return const Scaffold(
          body: Center(child: CircularProgressIndicator(color: kAccent)));
    }
    if (_token == null || _user == null) {
      return LoginScreen(onLoggedIn: _onLoggedIn);
    }
    return MainTabs(
      user: _user!,
      token: _token!,
      onLogout: _logout,
    );
  }
}

/* ============================================================
   ТАБЫ
   ============================================================ */
class MainTabs extends StatefulWidget {
  final User user;
  final String token;
  final VoidCallback onLogout;
  const MainTabs(
      {super.key,
      required this.user,
      required this.token,
      required this.onLogout});

  @override
  State<MainTabs> createState() => _MainTabsState();
}

class _MainTabsState extends State<MainTabs> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: IndexedStack(
          index: _tab,
          children: [
            ChatsListScreen(
                user: widget.user, token: widget.token, onLogout: widget.onLogout),
            MarketScreen(user: widget.user, token: widget.token),
          ],
        ),
      ),
      bottomNavigationBar: Container(
        color: kPanel,
        child: SafeArea(
          child: BottomNavigationBar(
            currentIndex: _tab,
            onTap: (i) => setState(() => _tab = i),
            backgroundColor: kPanel,
            selectedItemColor: kAccentBright,
            unselectedItemColor: kText2,
            type: BottomNavigationBarType.fixed,
            items: const [
              BottomNavigationBarItem(
                  icon: Icon(Icons.chat_bubble_outline), label: 'Чаты'),
              BottomNavigationBarItem(
                  icon: Icon(Icons.storefront_outlined), label: 'Маркет'),
            ],
          ),
        ),
      ),
    );
  }
}

/* ============================================================
   ВКЛАДКА «ЧАТЫ» — список
   ============================================================ */
class ChatsListScreen extends StatefulWidget {
  final User user;
  final String token;
  final VoidCallback onLogout;
  const ChatsListScreen(
      {super.key,
      required this.user,
      required this.token,
      required this.onLogout});

  @override
  State<ChatsListScreen> createState() => _ChatsListScreenState();
}

class _ChatsListScreenState extends State<ChatsListScreen> {
  List<ChatItem> _chats = <ChatItem>[];
  Set<String> _online = <String>{};
  bool _loading = true;
  StreamSubscription<dynamic>? _sub;

  @override
  void initState() {
    super.initState();
    _load();
    _sub = WsBus.instance.events.listen(_onWs);
  }

  void _onWs(dynamic e) {
    if (e is! Map<String, dynamic>) return;
    final t = e['type'];
    if (t == 'presence') {
      final l = e['online_users'];
      if (l is List) {
        setState(() => _online =
            l.map((x) => x.toString()).toSet());
      }
    } else if (t == 'message' || t == 'read_by') {
      _load();
    }
  }

  Future<void> _load() async {
    try {
      final list = await Api.myChats(widget.token);
      if (!mounted) return;
      final items = <ChatItem>[];
      for (final j in list) {
        if (j is Map<String, dynamic>) items.add(ChatItem.fromJson(j));
      }
      setState(() {
        _chats = items;
        _loading = false;
      });
    } catch (e) {
      debugPrint('[SilverChat] myChats: $e');
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Шапка: имя + статус WS + баланс + выход
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          color: kPanel,
          child: Row(
            children: [
              const Text('SilverChat',
                  style: TextStyle(
                      color: kText, fontSize: 19, fontWeight: FontWeight.w800)),
              const SizedBox(width: 8),
              _WsDot(),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: kGold.withOpacity(0.10),
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(color: kGold.withOpacity(0.35)),
                ),
                child: Text(fmtSilver(widget.user.balance),
                    style: const TextStyle(
                        color: kGold, fontSize: 12, fontWeight: FontWeight.w700)),
              ),
              const SizedBox(width: 8),
              IconButton(
                tooltip: 'Выход',
                icon: const Icon(Icons.logout, color: kText2, size: 20),
                onPressed: () async {
                  final ok = await showDialog<bool>(
                    context: context,
                    builder: (c) => AlertDialog(
                      backgroundColor: kPanel,
                      title: const Text('Выйти?',
                          style: TextStyle(color: kText, fontSize: 15)),
                      actions: [
                        TextButton(
                            onPressed: () => Navigator.pop(c, false),
                            child: const Text('Отмена', style: TextStyle(color: kText2))),
                        TextButton(
                            onPressed: () => Navigator.pop(c, true),
                            child: const Text('Выход',
                                style: TextStyle(color: kRed, fontWeight: FontWeight.w700))),
                      ],
                    ),
                  );
                  if (ok == true) widget.onLogout();
                },
              ),
            ],
          ),
        ),
        const Divider(height: 1, color: kBorder),
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator(color: kAccent))
              : _chats.isEmpty
                  ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(32),
                        child: Text(
                          'Пока нет чатов.\nНапиши любому зарегистрированному\nпользователю — чат появится здесь.',
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: kText2, fontSize: 13, height: 1.5),
                        ),
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      itemCount: _chats.length,
                      itemBuilder: (c, i) => _chatTile(_chats[i]),
                    ),
        ),
      ],
    );
  }

  Widget _chatTile(ChatItem c) {
    final isOn = _online.contains(c.username);
    final lastTime = c.lastTime == null ? '' : fmtTime(c.lastTime);
    final preview = c.lastText.isEmpty
        ? '@${c.username}'
        : (c.lastSender == widget.user.username ? 'Вы: ' : '') + c.lastText;
    return InkWell(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => ChatScreen(
              username: c.username,
              nickname: c.nickname,
              avatarUrl: c.avatarUrl,
              token: widget.token,
              me: widget.user.username,
            ),
          ),
        );
      },
      child: Container(
        color: kPanel,
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        child: Row(
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                AvatarView(name: c.nickname, uri: c.avatarUrl, size: 48),
                Positioned(
                  right: 1,
                  bottom: 1,
                  child: Container(
                    width: 12,
                    height: 12,
                    decoration: BoxDecoration(
                        color: isOn ? kGreen : kText2,
                        shape: BoxShape.circle,
                        border: Border.all(color: kPanel, width: 2)),
                  ),
                ),
                if (c.unread > 0)
                  Positioned(
                    right: -4,
                    top: -4,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 5),
                      decoration: BoxDecoration(
                          color: kAccentBright,
                          borderRadius: BorderRadius.circular(999),
                          border: Border.all(color: kPanel, width: 2)),
                      constraints: const BoxConstraints(minWidth: 19),
                      child: Center(
                          child: Text('${c.unread}',
                              style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 10,
                                  fontWeight: FontWeight.w800))),
                    ),
                  ),
              ],
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          c.nickname,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              color: kText, fontSize: 15, fontWeight: FontWeight.w700),
                        ),
                      ),
                      if (c.isVerified) ...[
                        const SizedBox(width: 4),
                        const Icon(Icons.verified, color: kAccentBright, size: 14),
                      ],
                      if (c.isPremium) ...[
                        const SizedBox(width: 4),
                        const Icon(Icons.star, color: kGold, size: 13),
                      ],
                      const Spacer(),
                      Text(lastTime, style: const TextStyle(color: kText2, fontSize: 11)),
                    ],
                  ),
                  const SizedBox(height: 3),
                  Text(preview,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: kText2, fontSize: 13)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _WsDot extends StatefulWidget {
  const _WsDot();
  @override
  State<_WsDot> createState() => _WsDotState();
}

class _WsDotState extends State<_WsDot> {
  bool _on = false;
  StreamSubscription<bool>? _sub;
  @override
  void initState() {
    super.initState();
    _on = WsBus.instance.connected;
    _sub = WsBus.instance.connectionChanges.listen((c) {
      if (mounted) setState(() => _on = c);
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: (_on ? kGreen : kRed).withOpacity(0.13),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: (_on ? kGreen : kRed).withOpacity(0.3)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
              width: 7,
              height: 7,
              decoration:
                  BoxDecoration(color: _on ? kGreen : kRed, shape: BoxShape.circle)),
          const SizedBox(width: 5),
          Text(_on ? 'LIVE' : 'OFF',
              style: TextStyle(
                  color: _on ? kGreen : kRed,
                  fontSize: 10,
                  fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

/* ============================================================
   ПЕРЕПИСКА
   ============================================================ */
class ChatScreen extends StatefulWidget {
  final String username;
  final String nickname;
  final String avatarUrl;
  final String token;
  final String me;
  const ChatScreen(
      {super.key,
      required this.username,
      required this.nickname,
      required this.avatarUrl,
      required this.token,
      required this.me});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final List<Msg> _msgs = <Msg>[];
  final _ctrl = TextEditingController();
  final Set<int> _seen = <int>{};
  bool _typing = false;
  bool _online = false;
  bool _loading = true;
  StreamSubscription<dynamic>? _sub;
  Timer? _typingTimer;

  @override
  void initState() {
    super.initState();
    _load();
    _sub = WsBus.instance.events.listen(_onWs);
  }

  void _onWs(dynamic e) {
    if (e is! Map<String, dynamic>) return;
    final t = e['type'];
    if (t == 'presence') {
      final l = e['online_users'];
      if (l is List) {
        final on = l.map((x) => x.toString()).contains(widget.username);
        if (on != _online) setState(() => _online = on);
      }
    } else if (t == 'typing') {
      final c = e['chat'];
      if (c is Map<String, dynamic> &&
          c['type'] == 'private' &&
          c['id'] == widget.username) {
        setState(() => _typing = true);
        _typingTimer?.cancel();
        _typingTimer = Timer(const Duration(seconds: 4), () {
          if (mounted) setState(() => _typing = false);
        });
      }
    } else if (t == 'message') {
      final m = e['message'];
      if (m is Map<String, dynamic> && m['type'] == 'private') {
        final sender = (m['sender'] ?? '').toString();
        final receiver = (m['receiver'] ?? '').toString();
        if (sender == widget.username || receiver == widget.username) {
          final msg = Msg.fromJson(m);
          if (!_seen.contains(msg.id)) {
            _seen.add(msg.id);
            setState(() => _msgs.add(msg));
            if (sender == widget.username) {
              Api.read(widget.token, widget.username).catch((_) {});
            }
          }
        }
      }
    } else if (t == 'read_by') {
      final by = (e['by'] ?? '').toString();
      if (by == widget.username) _markMineRead();
    }
  }

  void _markMineRead() {
    final next = <Msg>[];
    for (final m in _msgs) {
      if (m.sender == widget.me && m.receiver == widget.username && !m.isRead) {
        next.add(Msg(
            id: m.id,
            type: m.type,
            sender: m.sender,
            receiver: m.receiver,
            text: m.text,
            createdAt: m.createdAt,
            isRead: true));
      } else {
        next.add(m);
      }
    }
    if (mounted) setState(() => _msgs
      ..clear()
      ..addAll(next));
  }

  Future<void> _load() async {
    try {
      final list = await Api.history(widget.token, widget.username);
      if (!mounted) return;
      for (final j in list) {
        if (j is Map<String, dynamic>) {
          final m = Msg.fromJson(j);
          _seen.add(m.id);
          _msgs.add(m);
        }
      }
      setState(() => _loading = false);
    } catch (e) {
      debugPrint('[SilverChat] history: $e');
      if (mounted) setState(() => _loading = false);
    }
    Api.read(widget.token, widget.username).catch((_) {});
  }

  Future<void> _send() async {
    final t = _ctrl.text.trim();
    if (t.isEmpty) return;
    _ctrl.clear();
    try {
      final d = await Api.send(widget.token, {
        'type': 'private',
        'receiver': widget.username,
        'text': t
      });
      final m = d['message'];
      if (m is Map<String, dynamic>) {
        final msg = Msg.fromJson(m);
        _seen.add(msg.id);
        if (mounted) setState(() => _msgs.add(msg));
      }
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Не удалось отправить');
    }
  }

  void _toast(String s) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Text(s, style: const TextStyle(fontSize: 13)),
        backgroundColor: const Color(0xFF2B3543),
        duration: const Duration(seconds: 3),
      ));
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _sub?.cancel();
    _typingTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final status = _typing
        ? 'печатает...'
        : (_online ? 'в сети' : 'не в сети');
    final statusColor = _typing
        ? kAccentBright
        : (_online ? kGreen : kText2);
    return Scaffold(
      backgroundColor: kBg,
      body: SafeArea(
        child: Column(
          children: [
            // Шапка
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              color: kPanel,
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back, color: kAccent, size: 22),
                    onPressed: () => Navigator.pop(context),
                  ),
                  AvatarView(
                      name: widget.nickname, uri: widget.avatarUrl, size: 40),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(widget.nickname,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                                color: kText,
                                fontSize: 15.5,
                                fontWeight: FontWeight.w600)),
                        Text(status,
                            style: TextStyle(color: statusColor, fontSize: 12)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const Divider(height: 1, color: kBorder),
            // Сообщения
            Expanded(
              child: _loading
                  ? const Center(
                      child: CircularProgressIndicator(color: kAccent))
                  : _msgs.isEmpty
                      ? Center(
                          child: Text(
                            'Нет сообщений.\nНапиши первым!',
                            textAlign: TextAlign.center,
                            style: const TextStyle(color: kText2, height: 1.5),
                          ),
                        )
                      : ListView.builder(
                          reverse: true,
                          padding: const EdgeInsets.symmetric(
                              horizontal: 12, vertical: 10),
                          itemCount: _msgs.length,
                          itemBuilder: (c, i) =>
                              _bubble(_msgs[_msgs.length - 1 - i]),
                        ),
            ),
            // Ввод
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              color: kPanel,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: TextField(
                      controller: _ctrl,
                      maxLines: null,
                      minLines: 1,
                      style: const TextStyle(color: kText, fontSize: 15),
                      onSubmitted: (_) => _send(),
                      decoration: InputDecoration(
                        hintText: 'Написать сообщение...',
                        hintStyle: const TextStyle(color: kText2, fontSize: 14),
                        filled: true,
                        fillColor: kBg,
                        contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 11),
                        border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(22),
                            borderSide: const BorderSide(color: kBorder)),
                        enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(22),
                            borderSide: const BorderSide(color: kBorder)),
                        focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(22),
                            borderSide:
                                const BorderSide(color: kAccentBright)),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Material(
                    color: kAccentBright,
                    shape: const CircleBorder(),
                    elevation: 2,
                    child: InkWell(
                      customBorder: const CircleBorder(),
                      onTap: _send,
                      child: const Padding(
                        padding: EdgeInsets.all(11),
                        child: Icon(Icons.send, color: Colors.white, size: 18),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _bubble(Msg m) {
    final out = m.sender == widget.me;
    return Align(
      alignment: out ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(top: 4, bottom: 4),
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.78),
        padding: const EdgeInsets.only(top: 7, bottom: 5, left: 12, right: 11),
        decoration: BoxDecoration(
          color: out ? kMsgOut : kMsgIn,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(15),
            topRight: const Radius.circular(15),
            bottomLeft: Radius.circular(out ? 15 : 5),
            bottomRight: Radius.circular(out ? 5 : 15),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            if (m.text.isNotEmpty)
              Text(m.text,
                  style: const TextStyle(color: kText, fontSize: 15, height: 1.3)),
            const SizedBox(height: 2),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(fmtTime(m.createdAt),
                    style: const TextStyle(color: kText2, fontSize: 10)),
                if (out)
                  Padding(
                    padding: const EdgeInsets.only(left: 4),
                    child: Text(
                      m.isRead ? '✓✓' : '✓',
                      style: TextStyle(
                          color: m.isRead ? kAccentBright : kText2,
                          fontSize: 11,
                          fontWeight: FontWeight.w700),
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/* ============================================================
   ВКЛАДКА «МАРКЕТ»
   ============================================================ */
class MarketScreen extends StatefulWidget {
  final User user;
  final String token;
  const MarketScreen({super.key, required this.user, required this.token});

  @override
  State<MarketScreen> createState() => _MarketScreenState();
}

class _MarketScreenState extends State<MarketScreen> {
  int _balance = 0;
  List<Listing> _listings = <Listing>[];
  bool _loading = true;
  final _tagCtrl = TextEditingController();
  final _priceCtrl = TextEditingController();
  final _adminUserCtrl = TextEditingController();
  final _adminAmountCtrl = TextEditingController();
  StreamSubscription<dynamic>? _sub;

  @override
  void initState() {
    super.initState();
    _balance = widget.user.balance;
    _load();
    _sub = WsBus.instance.events.listen(_onWs);
  }

  void _onWs(dynamic e) {
    if (e is! Map<String, dynamic>) return;
    final t = e['type'];
    if (t == 'market_updated' || t == 'balance_update') _load();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait([
        Api.balance(widget.token),
        Api.market(widget.token),
      ]);
      if (!mounted) return;
      final b = results[0] as Map<String, dynamic>;
      final m = results[1] as List<dynamic>;
      final items = <Listing>[];
      for (final j in m) {
        if (j is Map<String, dynamic>) items.add(Listing.fromJson(j));
      }
      setState(() {
        _balance = b['balance_silvers'] is int ? b['balance_silvers'] as int : _balance;
        _listings = items;
        _loading = false;
      });
    } catch (e) {
      debugPrint('[SilverChat] market load: $e');
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void dispose() {
    _tagCtrl.dispose();
    _priceCtrl.dispose();
    _adminUserCtrl.dispose();
    _adminAmountCtrl.dispose();
    _sub?.cancel();
    super.dispose();
  }

  void _toast(String s, {bool ok = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Text(s, style: const TextStyle(fontSize: 13)),
        backgroundColor: ok ? const Color(0xFF1E3A2A) : const Color(0xFF2B3543),
        duration: const Duration(seconds: 3),
      ));
  }

  Future<void> _doList() async {
    final tag = _tagCtrl.text.trim();
    final price = int.tryParse(_priceCtrl.text.trim());
    if (tag.isEmpty || price == null || price < 1) {
      _toast('Укажи тег и цену');
      return;
    }
    try {
      await Api.marketList(widget.token, tag, price);
      _tagCtrl.clear();
      _priceCtrl.clear();
      _toast('Тег $tag выставлен за $price 💰', ok: true);
      _load();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Не удалось выставить');
    }
  }

  Future<void> _doBuy(String tag) async {
    try {
      final d = await Api.marketBuy(widget.token, tag);
      _toast('Ты купил(а) $tag!', ok: true);
      _load();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Не удалось купить');
    }
  }

  Future<void> _doUnlist(String tag) async {
    try {
      await Api.marketUnlist(widget.token, tag);
      _toast('$tag снят с продажи', ok: true);
      _load();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Не удалось снять');
    }
  }

  Future<void> _doGrant() async {
    final u = _adminUserCtrl.text.trim().replaceFirst(RegExp(r'^@+'), '');
    final a = int.tryParse(_adminAmountCtrl.text.trim());
    if (u.isEmpty || a == null || a < 1) {
      _toast('Укажи username и сумму');
      return;
    }
    try {
      final d = await Api.grantSilvers(widget.token, u, a);
      final bal = d['balance'];
      _toast('Выдано @${u}: $a 💰 (баланс $bal)', ok: true);
      _adminUserCtrl.clear();
      _adminAmountCtrl.clear();
      _load();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Не удалось выдать');
    }
  }

  @override
  Widget build(BuildContext context) {
    final isAdmin = widget.user.isAdmin || widget.user.isDev;
    final myListings =
        _listings.where((l) => l.owner == widget.user.username).toList();
    return _loading
        ? const Center(child: CircularProgressIndicator(color: kAccent))
        : ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // Баланс
              Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: kPanel,
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: kGold.withOpacity(0.35)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('ВАШ БАЛАНС',
                        style: TextStyle(color: kText2, fontSize: 12, fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    Text(fmtSilver(_balance),
                        style: const TextStyle(
                            color: kGold,
                            fontSize: 26,
                            fontWeight: FontWeight.w800)),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              // Мои лоты
              if (myListings.isNotEmpty) ...[
                _sectionTitle('Мои лоты'),
                ...myListings.map(
                  (l) => Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: kPanel,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: kBorder),
                    ),
                    child: Row(
                      children: [
                        _tagChip(l.tag),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text('${fmtNum(l.price)} 💰',
                              style: const TextStyle(
                                  color: kText,
                                  fontWeight: FontWeight.w700,
                                  fontSize: 14)),
                        ),
                        OutlinedButton(
                          onPressed: () => _doUnlist(l.tag),
                          style: OutlinedButton.styleFrom(
                              foregroundColor: kText2,
                              side: const BorderSide(color: kBorder),
                              shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12))),
                          child: const Text('Снять', style: TextStyle(fontSize: 12)),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 6),
              ],
              // Выставить на продажу
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                    color: kPanel,
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(color: kBorder)),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('🏷 Выставить юзернейм на продажу',
                        style: TextStyle(
                            color: kText,
                            fontWeight: FontWeight.w700,
                            fontSize: 14)),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(
                          child: _input(_tagCtrl, '@vip, @boss...'),
                        ),
                        const SizedBox(width: 8),
                        SizedBox(
                          width: 110,
                          child: _input(_priceCtrl, 'Цена 💰', number: true),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    _primaryBtn('Поставить на маркет', _doList),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              // Админ
              if (isAdmin)
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: kPanel,
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(color: kGold.withOpacity(0.5)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('👑 Админ: выдать Сильверы',
                          style: TextStyle(
                              color: kGold,
                              fontWeight: FontWeight.w700,
                              fontSize: 14)),
                      const SizedBox(height: 10),
                      _input(_adminUserCtrl, 'username получателя'),
                      const SizedBox(height: 8),
                      _input(_adminAmountCtrl, 'Сумма в Сильверах', number: true),
                      const SizedBox(height: 10),
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton(
                          onPressed: _doGrant,
                          style: ElevatedButton.styleFrom(
                              backgroundColor: kGold,
                              foregroundColor: const Color(0xFF3A2E00),
                              shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12))),
                          child: const Text('Выдать Сильверы',
                              style: TextStyle(fontWeight: FontWeight.w800)),
                        ),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 16),
              _sectionTitle('Маркет'),
              if (_listings.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Text(
                    'На маркете пока пусто.\nВыставь свой юзернейм выше 👆',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: kText2, height: 1.5, fontSize: 13),
                  ),
                )
              else
                ..._listings.map(
                  (l) => Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: kPanel,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: kBorder),
                    ),
                    child: Row(
                      children: [
                        _tagChip(l.tag),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(l.tag,
                                  style: const TextStyle(
                                      color: kText,
                                      fontWeight: FontWeight.w700,
                                      fontSize: 15)),
                              Text('продаёт @${l.owner}',
                                  style:
                                      const TextStyle(color: kText2, fontSize: 12)),
                            ],
                          ),
                        ),
                        ElevatedButton(
                          onPressed: () => _doBuy(l.tag),
                          style: ElevatedButton.styleFrom(
                              backgroundColor: kGold,
                              foregroundColor: kBg,
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 12, vertical: 10),
                              shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12))),
                          child: Text(
                              '${fmtNum(l.price)} 💰 · Купить',
                              style: const TextStyle(
                                  fontWeight: FontWeight.w800, fontSize: 12)),
                        ),
                      ],
                    ),
                  ),
                ),
              const SizedBox(height: 10),
            ],
          );
  }

  Widget _sectionTitle(String s) => Padding(
        padding: const EdgeInsets.only(bottom: 10, left: 2),
        child: Text(s.toUpperCase(),
            style: const TextStyle(
                color: kText2, fontSize: 11, fontWeight: FontWeight.w700)),
      );

  Widget _tagChip(String tag) {
    return Container(
      width: 44,
      height: 44,
      decoration: BoxDecoration(
        color: kGold.withOpacity(0.10),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: kGold.withOpacity(0.4)),
      ),
      child: Center(
        child: Text('#${tag.replaceFirst(RegExp(r'^@+'), '')}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
                color: kGold, fontWeight: FontWeight.w800, fontSize: 13)),
      ),
    );
  }

  Widget _primaryBtn(String label, VoidCallback onTap) {
    return SizedBox(
      width: double.infinity,
      height: 44,
      child: ElevatedButton(
        onPressed: onTap,
        style: ElevatedButton.styleFrom(
            backgroundColor: kAccentBright,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12))),
        child: Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
      ),
    );
  }

  Widget _input(TextEditingController c, String hint, {bool number = false}) {
    return TextField(
      controller: c,
      keyboardType: number ? TextInputType.number : TextInputType.text,
      autocorrect: false,
      style: const TextStyle(color: kText, fontSize: 14),
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: const TextStyle(color: kText2, fontSize: 13),
        filled: true,
        fillColor: kBg,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
        border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: kBorder)),
        enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: kBorder)),
        focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: kAccentBright)),
      ),
    );
  }
}

/* ============================================================
   APP
   ============================================================ */
class SilverChatApp extends StatelessWidget {
  const SilverChatApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SilverChat',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: kBg,
        colorScheme: ColorScheme.fromSeed(
            seedColor: kAccentBright, brightness: Brightness.dark),
        useMaterial3: true,
      ),
      home: const RootScreen(),
    );
  }
}

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations(
      [DeviceOrientation.portraitUp, DeviceOrientation.portraitDown]);
  // Глобальные сетки ошибок: не даём падать процессу, показываем/log'им.
  // (Flutter в release и так не крэшит на Dart-исключениях — показывает
  //  ErrorWidget, но мы дополнительно логируем с меткой [SilverChat].)
  FlutterError.onError = (details) {
    debugPrint('[SilverChat] FlutterError: ${details.exceptionAsString()}');
    FlutterError.presentError(details);
  };
  PlatformDispatcher.instance.onError = (e, s) {
    debugPrint('[SilverChat] uncaught: $e\n$s');
    return true; // перехватываем — процесс живёт
  };
  runApp(const SilverChatApp());
}
