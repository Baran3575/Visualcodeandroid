/* Injected alongside mobile.css. Small behaviour tweaks for touch. */
(function () {
  // Disable double-tap zoom everywhere.
  var s = document.createElement("style");
  s.textContent = "*{touch-action: manipulation !important;}";
  document.head.appendChild(s);

  // Make hover-only context menus reachable on long-press / tap.
  // VS Code already binds contextmenu; ensure default behaviour is not blocked.
  document.addEventListener(
    "contextmenu",
    function (e) {
      // no-op: let VS Code's own handler run on touch long-press
    },
    true
  );

  // Auto-dismiss the "Get Started" walkthrough once, for a clean first launch.
  function dismissGetStarted() {
    var btn = document.querySelector(
      ".editor-group-container .welcomePage .button, .monaco-button"
    );
    // intentionally passive; user can close manually
  }
  window.setTimeout(dismissGetStarted, 1500);
})();
