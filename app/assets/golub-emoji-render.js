// Заменяет системные эмодзи на встроенные картинки (golub-emoji.js), чтобы на любом
// телефоне иконки выглядели одинаково. Следит за DOM и обрабатывает новый текст.
(function () {
  var MAP = window.GOLUB_EMOJI;
  if (!MAP) return;
  var URL = {};
  Object.keys(MAP).forEach(function (k) {
    URL[k] = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(MAP[k]);
  });
  var keys = Object.keys(MAP).sort(function (a, b) { return b.length - a.length; });
  var esc = function (s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); };
  var RE = new RegExp('(?:' + keys.map(esc).join('|') + ')️?', 'gu');
  var TEST = new RegExp(RE.source, 'u');
  var SKIP = { SCRIPT: 1, STYLE: 1, TEXTAREA: 1, INPUT: 1, OPTION: 1, SELECT: 1, TITLE: 1, NOSCRIPT: 1 };
  var SVGNS = 'http://www.w3.org/2000/svg';

  var css = document.createElement('style');
  css.textContent = 'img.emj{display:inline-block;width:1.15em;height:1.15em;margin:0 .04em;vertical-align:-.2em;' +
    'pointer-events:none;-webkit-user-drag:none;user-select:none}';
  document.head.appendChild(css);

  function img(e) {
    var k = e.replace(/️/g, '');
    var i = document.createElement('img');
    i.className = 'emj';
    i.alt = e;
    i.draggable = false;
    i.src = URL[k];
    return i;
  }

  // Эмодзи внутри SVG <text> (колесо удачи) меняем на <image> того же размера
  function svgText(t) {
    var e = t.textContent.trim().match(RE);
    if (!e || t.textContent.trim() !== e[0]) return;
    var fs = parseFloat(t.getAttribute('font-size')) || 16, s = fs * 1.15;
    var x = parseFloat(t.getAttribute('x')) || 0, y = parseFloat(t.getAttribute('y')) || 0;
    var im = document.createElementNS(SVGNS, 'image');
    im.setAttribute('href', URL[e[0].replace(/️/g, '')]);
    im.setAttribute('width', s);
    im.setAttribute('height', s);
    im.setAttribute('x', (x - s / 2).toFixed(1));
    im.setAttribute('y', (y - s / 2).toFixed(1));
    if (t.getAttribute('transform')) im.setAttribute('transform', t.getAttribute('transform'));
    t.parentNode.replaceChild(im, t);
  }

  function textNode(n) {
    var p = n.parentNode;
    if (!p || SKIP[p.nodeName] || !TEST.test(n.data)) return;
    if (p.namespaceURI === SVGNS) {
      if (p.nodeName === 'text' || p.nodeName === 'tspan') svgText(p.nodeName === 'tspan' ? p.parentNode : p);
      return;
    }
    if (p.isContentEditable) return;
    var s = n.data, frag = document.createDocumentFragment(), last = 0, m;
    RE.lastIndex = 0;
    while ((m = RE.exec(s))) {
      if (m.index > last) frag.appendChild(document.createTextNode(s.slice(last, m.index)));
      frag.appendChild(img(m[0]));
      last = m.index + m[0].length;
    }
    if (last < s.length) frag.appendChild(document.createTextNode(s.slice(last)));
    p.replaceChild(frag, n);
  }

  function walk(root) {
    if (root.nodeType === 3) return textNode(root);
    if (root.nodeType !== 1 || SKIP[root.nodeName]) return;
    var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT), list = [], n;
    while ((n = w.nextNode())) if (TEST.test(n.data)) list.push(n);
    list.forEach(textNode);
  }

  function start() {
    walk(document.body);
    new MutationObserver(function (muts) {
      for (var i = 0; i < muts.length; i++) {
        var m = muts[i];
        if (m.type === 'characterData') textNode(m.target);
        else for (var j = 0; j < m.addedNodes.length; j++) walk(m.addedNodes[j]);
      }
    }).observe(document.body, { childList: true, subtree: true, characterData: true });
  }
  if (document.body) start();
  else document.addEventListener('DOMContentLoaded', start);
})();
