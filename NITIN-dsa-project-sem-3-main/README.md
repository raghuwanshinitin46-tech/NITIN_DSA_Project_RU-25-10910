# Boardroom — Java AI Strategy Games

A Java DSA project with a browser interface. Play Tic-Tac-Toe or Connect Four against an AI using Minimax and alpha-beta search. The standalone game works without Java; the Java server is optional.

## Live preview

**Local live link:** [http://localhost:8080](http://localhost:8080)

This link works on your computer while the local server is running; it is not a public GitHub Pages link. In VS Code, run the **Boardroom: Start local web server** task. Or run `Serve-Web.ps1` from this folder. Press `Ctrl+C` in the terminal to stop it.

## Run

- **Quick start:** open `web/index.html` in a modern browser. Both games run locally in the browser.
- **Java server (optional):** install JDK 17+ and run `java --add-modules jdk.httpserver GameServer.java`. Without Java, the local preview and browser game still work.

## DSA

- Tic-Tac-Toe uses exhaustive Minimax with depth-adjusted win/loss scores.
- Connect Four uses depth-limited Minimax, alpha-beta pruning, center-first move ordering, and a four-cell-window heuristic.
- Both algorithms are implemented in Java (`GameServer.java`) and in the browser (`web/app.js`) so the game remains playable without a server.

## Files

The PowerShell preview serves `web/` on port 8080. It uses the in-browser AI fallback; the Java server uses the equivalent Java algorithms when launched instead.
