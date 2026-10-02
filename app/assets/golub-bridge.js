// Мост между игрой и Android-обёрткой. Подключается в <head> до скриптов игры (см. build.sh).
// Подменяет браузерные API, которых нет в WebView: Notification, service worker с periodicSync
// и CacheStorage для меты напоминаний, а также красит системные панели под фон игры.
(function () {
  var A = window.GolubAndroid;
  if (!A) return;

  // --- Уведомления ---
  var permWaiters = [];
  window.__golubPerm = function (state) {
    var w = permWaiters;
    permWaiters = [];
    w.forEach(function (f) { f(state); });
  };
  function Notif(title, opts) { A.notify(String(title || ''), String((opts && opts.body) || '')); }
  Object.defineProperty(Notif, 'permission', { get: function () { return A.notifPermission(); } });
  Notif.requestPermission = function (cb) {
    return new Promise(function (res) {
      permWaiters.push(function (s) { if (cb) cb(s); res(s); });
      A.requestNotifPermission();
    });
  };
  window.Notification = Notif;

  // Напоминания в фоне делает AlarmManager, игре достаточно знать, что periodicSync «есть».
  var reg = {
    periodicSync: {
      register: function () { return Promise.resolve(); },
      unregister: function () { return Promise.resolve(); }
    },
    showNotification: function (title, opts) { Notif(title, opts); return Promise.resolve(); }
  };
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      getRegistration: function () { return Promise.resolve(reg); },
      register: function () { return Promise.resolve(reg); },
      ready: Promise.resolve(reg)
    }
  });

  var perms = navigator.permissions;
  var origQuery = perms && perms.query && perms.query.bind(perms);
  var fakePerms = {
    query: function (d) {
      if (d && d.name === 'periodic-background-sync') return Promise.resolve({ state: 'granted' });
      if (d && d.name === 'notifications') return Promise.resolve({ state: Notif.permission === 'default' ? 'prompt' : Notif.permission });
      return origQuery ? origQuery(d) : Promise.reject(new Error('unsupported'));
    }
  };
  Object.defineProperty(navigator, 'permissions', { configurable: true, value: fakePerms });

  // Игра пишет мету (вкл/выкл, время последнего захода, имя голубя) в кэш golub-meta,
  // откуда её читал service worker. Здесь она уходит в приложение для расписания напоминаний.
  var cache = {
    put: function (key, resp) {
      return resp.text().then(function (t) { A.setMeta(t); });
    },
    match: function () { return Promise.resolve(undefined); }
  };
  Object.defineProperty(window, 'caches', {
    configurable: true,
    value: {
      open: function () { return Promise.resolve(cache); },
      has: function () { return Promise.resolve(true); },
      keys: function () { return Promise.resolve(['golub-meta']); },
      delete: function () { return Promise.resolve(true); }
    }
  });

  // --- Цвет системных панелей под текущую тему игры ---
  var last = '';
  function syncBars() {
    if (!document.body) return;
    var c = getComputedStyle(document.body).backgroundColor;
    var m = c.match(/\d+(\.\d+)?/g);
    if (!m || m.length < 3) return;
    var r = +m[0], g = +m[1], b = +m[2];
    var key = r + ',' + g + ',' + b;
    if (key === last) return;
    last = key;
    A.setBars(r, g, b);
  }
  document.addEventListener('DOMContentLoaded', function () {
    syncBars();
    new MutationObserver(function () { requestAnimationFrame(syncBars); })
      .observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class', 'style'] });
    var mq = matchMedia('(prefers-color-scheme: dark)');
    (mq.addEventListener ? mq.addEventListener.bind(mq, 'change') : mq.addListener.bind(mq))(syncBars);
  });
  window.addEventListener('load', syncBars);
  window.__golubSyncBars = syncBars;
})();
