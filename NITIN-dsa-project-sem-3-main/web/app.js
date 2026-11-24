const state = {
  game: "tictactoe",
  board: Array(9).fill(""),
  busy: false,
  outcome: "",
  javaApiAvailable: null
};

const ticBoard = document.querySelector("#tic-board");
const connectBoard = document.querySelector("#connect-board");
const columnLabels = document.querySelector("#column-labels");
const connectBoardWrap = document.querySelector("#connect-board-wrap");
const statusLine = document.querySelector("#game-status");
const statusText = statusLine.querySelector("span:last-child");
const moveCount = document.querySelector("#move-count");
const humanCard = document.querySelector("#human-card");
const aiCard = document.querySelector("#ai-card");

document.querySelectorAll(".game-option").forEach((button) => {
  button.addEventListener("click", () => selectGame(button.dataset.game));
});
document.querySelector("#reset-button").addEventListener("click", resetGame);
ticBoard.addEventListener("click", onBoardClick);
connectBoard.addEventListener("click", onBoardClick);
columnLabels.addEventListener("click", onBoardClick);

function selectGame(game) {
  if (state.game === game) return;
  state.game = game;
  document.querySelectorAll(".game-option").forEach((button) => {
    const active = button.dataset.game === game;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  document.querySelector("#game-title").textContent =
    game === "connect4" ? "Connect Four" : "Tic-Tac-Toe";
  document.querySelector("#human-token").textContent = game === "connect4" ? "●" : "X";
  document.querySelector("#ai-token").textContent = game === "connect4" ? "●" : "O";
  document.querySelector("#human-role").textContent = game === "connect4" ? "RED · GOES FIRST" : "GOES FIRST";
  document.querySelector("#ai-role").textContent = game === "connect4" ? "ALPHA–BETA" : "MINIMAX";
  ticBoard.hidden = game !== "tictactoe";
  connectBoardWrap.hidden = game !== "connect4";
  resetGame();
}

function resetGame() {
  state.board = Array(state.game === "connect4" ? 42 : 9).fill("");
  state.busy = false;
  state.outcome = "";
  statusLine.className = "status-line";
  setStatus("Your turn. Pick a square to get started.");
  setTurn("human");
  render();
}

function onBoardClick(event) {
  const button = event.target.closest("button[data-index]");
  if (!button || state.busy || state.outcome) return;
  const index = Number(button.dataset.index);
  if (state.game === "tictactoe" && state.board[index]) return;
  if (state.game === "connect4" && !columnHasSpace(index)) return;
  makeMove(index);
}

async function makeMove(index) {
  state.busy = true;
  setStatus("The AI is thinking…");
  setTurn("computer");
  render();
  try {
    const result = await requestAIMove(index);
    state.board = result.board;
    state.outcome = result.outcome;
    showOutcome(result.outcome);
  } catch (error) {
    statusLine.className = "status-line is-error";
    setStatus(error.message);
    setTurn("human");
  } finally {
    state.busy = false;
    render();
  }
}

async function requestAIMove(index) {
  if (location.protocol === "file:" || state.javaApiAvailable === false) {
    state.javaApiAvailable = false;
    return calculateBrowserMove(state.game, state.board, index);
  }

  try {
    const response = await fetch("/api/move", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ game: state.game, board: state.board, move: index })
    });
    if ([404, 405, 501, 503].includes(response.status)) {
      state.javaApiAvailable = false;
      return calculateBrowserMove(state.game, state.board, index);
    }
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) {
      state.javaApiAvailable = false;
      return calculateBrowserMove(state.game, state.board, index);
    }
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || "The move could not be completed.");
    state.javaApiAvailable = true;
    return result;
  } catch (error) {
    if (error instanceof TypeError) {
      state.javaApiAvailable = false;
      return calculateBrowserMove(state.game, state.board, index);
    }
    throw error;
  }
}

function calculateBrowserMove(game, cells, move) {
  const board = [...cells];
  if (game === "tictactoe") {
    if (move < 0 || move >= 9 || board[move]) throw new Error("That square is not available.");
    board[move] = "X";
    let outcome = hasTicWinner(board, "X") ? "human" : "";
    if (!outcome && board.every(Boolean)) outcome = "draw";
    if (!outcome) {
      board[bestTicTacToeMove(board)] = "O";
      if (hasTicWinner(board, "O")) outcome = "computer";
      else if (board.every(Boolean)) outcome = "draw";
    }
    return { board, outcome };
  }

  const humanRow = dropConnectFourPiece(board, move, "R");
  if (humanRow < 0) throw new Error("That column is full. Choose another one.");
  let outcome = hasConnectFourWinner(board, humanRow, move, "R") ? "human" : "";
  if (!outcome && !hasConnectFourMove(board)) outcome = "draw";
  if (!outcome) {
    const computerColumn = bestConnectFourMove(board);
    const computerRow = dropConnectFourPiece(board, computerColumn, "Y");
    if (hasConnectFourWinner(board, computerRow, computerColumn, "Y")) outcome = "computer";
    else if (!hasConnectFourMove(board)) outcome = "draw";
  }
  return { board, outcome };
}

const TIC_TAC_TOE_LINES = [
  [0, 1, 2], [3, 4, 5], [6, 7, 8],
  [0, 3, 6], [1, 4, 7], [2, 5, 8],
  [0, 4, 8], [2, 4, 6]
];

function hasTicWinner(board, mark) {
  return TIC_TAC_TOE_LINES.some((line) => line.every((index) => board[index] === mark));
}

function bestTicTacToeMove(board) {
  let bestScore = Number.NEGATIVE_INFINITY;
  let bestMove = -1;
  for (let index = 0; index < board.length; index++) {
    if (board[index]) continue;
    board[index] = "O";
    const score = minimaxTicTacToe(board, false, 0);
    board[index] = "";
    if (score > bestScore) {
      bestScore = score;
      bestMove = index;
    }
  }
  return bestMove;
}

function minimaxTicTacToe(board, maximizing, depth) {
  if (hasTicWinner(board, "O")) return 10 - depth;
  if (hasTicWinner(board, "X")) return depth - 10;
  if (board.every(Boolean)) return 0;
  let best = maximizing ? Number.NEGATIVE_INFINITY : Number.POSITIVE_INFINITY;
  const mark = maximizing ? "O" : "X";
  for (let index = 0; index < board.length; index++) {
    if (board[index]) continue;
    board[index] = mark;
    const score = minimaxTicTacToe(board, !maximizing, depth + 1);
    board[index] = "";
    best = maximizing ? Math.max(best, score) : Math.min(best, score);
  }
  return best;
}

const CONNECT_FOUR_COLUMNS = [3, 2, 4, 1, 5, 0, 6];
const CONNECT_FOUR_ROWS = 6;
const CONNECT_FOUR_WIDTH = 7;
const CONNECT_FOUR_DEPTH = 5;

function bestConnectFourMove(board) {
  let bestScore = Number.NEGATIVE_INFINITY;
  let bestColumn = -1;
  let alpha = Number.NEGATIVE_INFINITY;
  const beta = Number.POSITIVE_INFINITY;
  for (const column of CONNECT_FOUR_COLUMNS) {
    const row = dropConnectFourPiece(board, column, "Y");
    if (row < 0) continue;
    const score = hasConnectFourWinner(board, row, column, "Y")
      ? 100000
      : searchConnectFour(board, CONNECT_FOUR_DEPTH - 1, alpha, beta, false);
    board[row * CONNECT_FOUR_WIDTH + column] = "";
    if (score > bestScore) {
      bestScore = score;
      bestColumn = column;
    }
    alpha = Math.max(alpha, bestScore);
  }
  return bestColumn;
}

function searchConnectFour(board, depth, alpha, beta, maximizing) {
  if (depth === 0 || !hasConnectFourMove(board)) return evaluateConnectFour(board);
  let best = maximizing ? Number.NEGATIVE_INFINITY : Number.POSITIVE_INFINITY;
  const mark = maximizing ? "Y" : "R";
  for (const column of CONNECT_FOUR_COLUMNS) {
    const row = dropConnectFourPiece(board, column, mark);
    if (row < 0) continue;
    const score = hasConnectFourWinner(board, row, column, mark)
      ? (maximizing ? 100000 + depth : -100000 - depth)
      : searchConnectFour(board, depth - 1, alpha, beta, !maximizing);
    board[row * CONNECT_FOUR_WIDTH + column] = "";
    if (maximizing) {
      best = Math.max(best, score);
      alpha = Math.max(alpha, best);
    } else {
      best = Math.min(best, score);
      beta = Math.min(beta, best);
    }
    if (beta <= alpha) break;
  }
  return best;
}

function evaluateConnectFour(board) {
  let score = 0;
  for (let row = 0; row < CONNECT_FOUR_ROWS; row++) {
    const center = board[row * CONNECT_FOUR_WIDTH + 3];
    if (center === "Y") score += 4;
    else if (center === "R") score -= 4;
  }
  for (let row = 0; row < CONNECT_FOUR_ROWS; row++) {
    for (let column = 0; column < CONNECT_FOUR_WIDTH; column++) {
      if (column + 3 < CONNECT_FOUR_WIDTH) score += scoreConnectFourWindow(board, row, column, 0, 1);
      if (row + 3 < CONNECT_FOUR_ROWS) score += scoreConnectFourWindow(board, row, column, 1, 0);
      if (row + 3 < CONNECT_FOUR_ROWS && column + 3 < CONNECT_FOUR_WIDTH) {
        score += scoreConnectFourWindow(board, row, column, 1, 1);
      }
      if (row + 3 < CONNECT_FOUR_ROWS && column - 3 >= 0) {
        score += scoreConnectFourWindow(board, row, column, 1, -1);
      }
    }
  }
  return score;
}

function scoreConnectFourWindow(board, row, column, rowStep, columnStep) {
  let computer = 0;
  let human = 0;
  let empty = 0;
  for (let offset = 0; offset < 4; offset++) {
    const index = (row + offset * rowStep) * CONNECT_FOUR_WIDTH + column + offset * columnStep;
    if (board[index] === "Y") computer++;
    else if (board[index] === "R") human++;
    else empty++;
  }
  if (computer > 0 && human > 0) return 0;
  if (computer === 4) return 100000;
  if (human === 4) return -100000;
  if (computer === 3 && empty === 1) return 90;
  if (computer === 2 && empty === 2) return 12;
  if (human === 3 && empty === 1) return -110;
  if (human === 2 && empty === 2) return -14;
  return 0;
}

function dropConnectFourPiece(board, column, mark) {
  if (column < 0 || column >= CONNECT_FOUR_WIDTH) return -1;
  for (let row = CONNECT_FOUR_ROWS - 1; row >= 0; row--) {
    const index = row * CONNECT_FOUR_WIDTH + column;
    if (!board[index]) {
      board[index] = mark;
      return row;
    }
  }
  return -1;
}

function hasConnectFourWinner(board, row, column, mark) {
  const directions = [[0, 1], [1, 0], [1, 1], [1, -1]];
  return directions.some(([rowStep, columnStep]) =>
    1
      + countConnectFourDirection(board, row, column, mark, rowStep, columnStep)
      + countConnectFourDirection(board, row, column, mark, -rowStep, -columnStep) >= 4
  );
}

function countConnectFourDirection(board, row, column, mark, rowStep, columnStep) {
  let count = 0;
  let nextRow = row + rowStep;
  let nextColumn = column + columnStep;
  while (nextRow >= 0 && nextRow < CONNECT_FOUR_ROWS
    && nextColumn >= 0 && nextColumn < CONNECT_FOUR_WIDTH
    && board[nextRow * CONNECT_FOUR_WIDTH + nextColumn] === mark) {
    count++;
    nextRow += rowStep;
    nextColumn += columnStep;
  }
  return count;
}

function hasConnectFourMove(board) {
  return CONNECT_FOUR_COLUMNS.some((column) => !board[column]);
}

function showOutcome(outcome) {
  statusLine.className = "status-line";
  if (outcome === "human") {
    statusLine.classList.add("is-win");
    setStatus("You win. Nicely played.");
    setTurn("");
  } else if (outcome === "computer") {
    statusLine.classList.add("is-computer-win");
    setStatus("AI wins this one. Rematch?");
    setTurn("");
  } else if (outcome === "draw") {
    setStatus("It's a draw. Good game.");
    setTurn("");
  } else {
    setStatus("Your turn. Pick your next move.");
    setTurn("human");
  }
}

function setStatus(message) {
  statusText.textContent = message;
}

function setTurn(player) {
  humanCard.classList.toggle("is-turn", player === "human");
  aiCard.classList.toggle("is-turn", player === "computer");
}

function columnHasSpace(column) {
  return state.board[column] === "";
}

function render() {
  const moves = state.board.filter(Boolean).length;
  moveCount.textContent = `${moves} ${moves === 1 ? "MOVE" : "MOVES"}`;
  if (state.game === "tictactoe") renderTicTacToe();
  else renderConnectFour();
}

function renderTicTacToe() {
  const winners = winningCells(state.board, 3, 3, "X", "O");
  ticBoard.replaceChildren(...state.board.map((mark, index) => {
    const cell = document.createElement("button");
    cell.type = "button";
    cell.className = "tic-cell";
    cell.dataset.index = String(index);
    cell.setAttribute("role", "gridcell");
    cell.setAttribute("aria-label", `Row ${Math.floor(index / 3) + 1}, column ${index % 3 + 1}${mark ? `, ${mark}` : ", empty"}`);
    cell.textContent = mark;
    cell.disabled = state.busy || Boolean(state.outcome) || Boolean(mark);
    if (mark === "X") cell.classList.add("is-human");
    if (mark === "O") cell.classList.add("is-computer");
    if (winners.has(index)) cell.classList.add("is-winning");
    return cell;
  }));
}

function renderConnectFour() {
  const winners = winningCells(state.board, 6, 7, "R", "Y");
  columnLabels.replaceChildren(...Array.from({ length: 7 }, (_, column) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "column-button";
    button.dataset.index = String(column);
    button.textContent = String(column + 1);
    button.setAttribute("aria-label", `Drop a red piece in column ${column + 1}`);
    button.disabled = state.busy || Boolean(state.outcome) || !columnHasSpace(column);
    return button;
  }));
  connectBoard.replaceChildren(...state.board.map((mark, index) => {
    const cell = document.createElement("button");
    const row = Math.floor(index / 7);
    const column = index % 7;
    cell.type = "button";
    cell.className = "connect-cell";
    cell.dataset.index = String(column);
    cell.setAttribute("role", "gridcell");
    cell.setAttribute("aria-label", `Row ${row + 1}, column ${column + 1}${mark ? `, ${mark === "R" ? "red" : "yellow"}` : ", empty"}`);
    cell.disabled = state.busy || Boolean(state.outcome) || !columnHasSpace(column);
    if (mark === "R") cell.classList.add("is-human");
    if (mark === "Y") cell.classList.add("is-computer");
    if (winners.has(index)) cell.classList.add("is-winning");
    return cell;
  }));
}

function winningCells(board, rows, columns, first, second) {
  const winners = new Set();
  const directions = [[0, 1], [1, 0], [1, 1], [1, -1]];
  const targetLength = rows === 3 ? 3 : 4;
  for (let row = 0; row < rows; row++) {
    for (let column = 0; column < columns; column++) {
      const start = row * columns + column;
      const mark = board[start];
      if (mark !== first && mark !== second) continue;
      for (const [rowStep, columnStep] of directions) {
        const endRow = row + (targetLength - 1) * rowStep;
        const endColumn = column + (targetLength - 1) * columnStep;
        if (endRow < 0 || endRow >= rows || endColumn < 0 || endColumn >= columns) continue;
        const line = Array.from({ length: targetLength }, (_, offset) =>
          (row + offset * rowStep) * columns + column + offset * columnStep);
        if (line.every((index) => board[index] === mark)) line.forEach((index) => winners.add(index));
      }
    }
  }
  return winners;
}

render();
