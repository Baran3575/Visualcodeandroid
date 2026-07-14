/* Injected alongside mobile.css (run via evaluateJavascript, CSP-proof).
   Keeps touch-action sane and a few behavioural tweaks. */
(function () {
  try {
    var s = document.createElement("style");
    s.textContent =
      "*{touch-action: manipulation !important; -webkit-tap-highlight-color: transparent !important;} " +
      "::selection{background:#094771 !important;}";
    document.head.appendChild(s);
  } catch (e) {}
})();
