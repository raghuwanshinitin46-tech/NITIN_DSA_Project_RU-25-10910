import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class GameServer {
    private static final int PORT = 8080;
    private static final int ROWS = 6;
    private static final int COLS = 7;
    private static final int SEARCH_DEPTH = 5;
    private static final char EMPTY = '.';
    private static final char HUMAN = 'X';
    private static final char COMPUTER = 'O';
    private static final char RED = 'R';
    private static final char YELLOW = 'Y';
    private static final int[] COLUMN_ORDER = {3, 2, 4, 1, 5, 0, 6};
    private static final Path WEB_ROOT = Path.of("web").toAbsolutePath().normalize();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/", GameServer::handleRequest);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Boardroom is ready at http://localhost:" + PORT);
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        try {
            String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
            if ("GET".equals(exchange.getRequestMethod()) && "/api/health".equals(path)) {
                sendJson(exchange, 200, "{\"status\":\"ok\"}");
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && "/api/move".equals(path)) {
                handleMove(exchange);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                servePage(exchange, path);
                return;
            }
            sendJson(exchange, 405, errorJson("Method not allowed."));
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400, errorJson(ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            sendJson(exchange, 500, errorJson("The game server encountered an unexpected error."));
        } finally {
            exchange.close();
        }
    }

    private static void handleMove(HttpExchange exchange) throws IOException {
        byte[] requestBytes = exchange.getRequestBody().readNBytes(8193);
        if (requestBytes.length > 8192) {
            throw new IllegalArgumentException("Request is too large.");
        }
        Object parsed = Json.parse(new String(requestBytes, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> request)) {
            throw new IllegalArgumentException("Expected a JSON object.");
        }

        String game = requiredString(request, "game");
        List<?> cells = requiredList(request, "board");
        int move = requiredInteger(request, "move");
        Map<String, Object> response;
        if ("tictactoe".equals(game)) {
            response = playTicTacToe(cells, move);
        } else if ("connect4".equals(game)) {
            response = playConnectFour(cells, move);
        } else {
            throw new IllegalArgumentException("Choose Tic-Tac-Toe or Connect Four.");
        }
        sendJson(exchange, 200, Json.stringify(response));
    }

    private static Map<String, Object> playTicTacToe(List<?> cells, int move) {
        if (cells.size() != 9 || move < 0 || move >= 9) {
            throw new IllegalArgumentException("That Tic-Tac-Toe move is out of range.");
        }
        char[] board = readBoard(cells, EMPTY, HUMAN, COMPUTER);
        if (count(board, HUMAN) != count(board, COMPUTER)
                || hasWinner(board, HUMAN) || hasWinner(board, COMPUTER)) {
            throw new IllegalArgumentException("This Tic-Tac-Toe board is no longer active.");
        }
        if (board[move] != EMPTY) {
            throw new IllegalArgumentException("That square is already occupied.");
        }

        board[move] = HUMAN;
        String outcome = "";
        if (hasWinner(board, HUMAN)) {
            outcome = "human";
        } else if (count(board, EMPTY) == 0) {
            outcome = "draw";
        } else {
            board[bestTicTacToeMove(board)] = COMPUTER;
            if (hasWinner(board, COMPUTER)) {
                outcome = "computer";
            } else if (count(board, EMPTY) == 0) {
                outcome = "draw";
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("board", boardStrings(board));
        response.put("outcome", outcome);
        return response;
    }

    private static int bestTicTacToeMove(char[] board) {
        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;
        for (int i = 0; i < board.length; i++) {
            if (board[i] != EMPTY) continue;
            board[i] = COMPUTER;
            int score = minimax(board, false, 0);
            board[i] = EMPTY;
            if (score > bestScore) {
                bestScore = score;
                bestMove = i;
            }
        }
        return bestMove;
    }

    private static int minimax(char[] board, boolean maximizing, int depth) {
        if (hasWinner(board, COMPUTER)) return 10 - depth;
        if (hasWinner(board, HUMAN)) return depth - 10;
        if (count(board, EMPTY) == 0) return 0;

        int best = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        char mark = maximizing ? COMPUTER : HUMAN;
        for (int i = 0; i < board.length; i++) {
            if (board[i] != EMPTY) continue;
            board[i] = mark;
            int score = minimax(board, !maximizing, depth + 1);
            board[i] = EMPTY;
            best = maximizing ? Math.max(best, score) : Math.min(best, score);
        }
        return best;
    }

    private static boolean hasWinner(char[] board, char mark) {
        int[][] lines = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
                {0, 4, 8}, {2, 4, 6}
        };
        for (int[] line : lines) {
            if (board[line[0]] == mark && board[line[1]] == mark && board[line[2]] == mark) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> playConnectFour(List<?> cells, int column) {
        if (cells.size() != ROWS * COLS || column < 0 || column >= COLS) {
            throw new IllegalArgumentException("That Connect Four column is out of range.");
        }
        char[][] board = new char[ROWS][COLS];
        int redCount = 0;
        int yellowCount = 0;
        for (int index = 0; index < cells.size(); index++) {
            Object value = cells.get(index);
            if (!(value instanceof String cell)) {
                throw new IllegalArgumentException("The board contains an invalid cell.");
            }
            char token = cell.isEmpty() ? EMPTY : cell.length() == 1 ? cell.charAt(0) : '?';
            if (token != EMPTY && token != RED && token != YELLOW) {
                throw new IllegalArgumentException("The board contains an invalid game piece.");
            }
            int row = index / COLS;
            int col = index % COLS;
            board[row][col] = token;
            if (token == RED) redCount++;
            if (token == YELLOW) yellowCount++;
        }
        if (redCount != yellowCount || hasWinner(board, RED) || hasWinner(board, YELLOW)) {
            throw new IllegalArgumentException("This Connect Four board is no longer active.");
        }
        for (int col = 0; col < COLS; col++) {
            boolean seenPiece = false;
            for (int row = ROWS - 1; row >= 0; row--) {
                if (board[row][col] != EMPTY) {
                    seenPiece = true;
                } else if (seenPiece) {
                    throw new IllegalArgumentException("The board contains a piece floating above an empty cell.");
                }
            }
        }

        int humanRow = drop(board, column, RED);
        if (humanRow < 0) throw new IllegalArgumentException("That column is full. Choose another one.");
        String outcome = "";
        if (isWinningMove(board, humanRow, column, RED)) {
            outcome = "human";
        } else if (!hasLegalMove(board)) {
            outcome = "draw";
        } else {
            int computerColumn = bestConnectFourMove(board);
            int computerRow = drop(board, computerColumn, YELLOW);
            if (isWinningMove(board, computerRow, computerColumn, YELLOW)) {
                outcome = "computer";
            } else if (!hasLegalMove(board)) {
                outcome = "draw";
            }
        }

        List<String> resultBoard = new ArrayList<>(ROWS * COLS);
        for (char[] row : board) resultBoard.addAll(boardStrings(row));
        Map<String, Object> response = new HashMap<>();
        response.put("board", resultBoard);
        response.put("outcome", outcome);
        return response;
    }

    private static int bestConnectFourMove(char[][] board) {
        int bestScore = Integer.MIN_VALUE;
        int bestColumn = -1;
        int alpha = Integer.MIN_VALUE;
        for (int column : COLUMN_ORDER) {
            int row = drop(board, column, YELLOW);
            if (row < 0) continue;
            int score = isWinningMove(board, row, column, YELLOW)
                    ? 100_000
                    : searchConnectFour(board, SEARCH_DEPTH - 1, alpha, Integer.MAX_VALUE, false);
            board[row][column] = EMPTY;
            if (score > bestScore) {
                bestScore = score;
                bestColumn = column;
            }
            alpha = Math.max(alpha, bestScore);
        }
        return bestColumn;
    }

    private static int searchConnectFour(char[][] board, int depth, int alpha, int beta, boolean maximizing) {
        if (depth == 0 || !hasLegalMove(board)) return evaluateConnectFour(board);
        int best = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        char mark = maximizing ? YELLOW : RED;
        for (int column : COLUMN_ORDER) {
            int row = drop(board, column, mark);
            if (row < 0) continue;
            int score = isWinningMove(board, row, column, mark)
                    ? (maximizing ? 100_000 + depth : -100_000 - depth)
                    : searchConnectFour(board, depth - 1, alpha, beta, !maximizing);
            board[row][column] = EMPTY;
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

    private static int evaluateConnectFour(char[][] board) {
        int score = 0;
        for (int row = 0; row < ROWS; row++) {
            if (board[row][COLS / 2] == YELLOW) score += 4;
            else if (board[row][COLS / 2] == RED) score -= 4;
        }
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLS; column++) {
                if (column + 3 < COLS) {
                    score += scoreWindow(board[row][column], board[row][column + 1],
                            board[row][column + 2], board[row][column + 3]);
                }
                if (row + 3 < ROWS) {
                    score += scoreWindow(board[row][column], board[row + 1][column],
                            board[row + 2][column], board[row + 3][column]);
                }
                if (row + 3 < ROWS && column + 3 < COLS) {
                    score += scoreWindow(board[row][column], board[row + 1][column + 1],
                            board[row + 2][column + 2], board[row + 3][column + 3]);
                }
                if (row + 3 < ROWS && column - 3 >= 0) {
                    score += scoreWindow(board[row][column], board[row + 1][column - 1],
                            board[row + 2][column - 2], board[row + 3][column - 3]);
                }
            }
        }
        return score;
    }

    private static int scoreWindow(char first, char second, char third, char fourth) {
        int computer = 0;
        int human = 0;
        int empty = 0;
        char[] cells = {first, second, third, fourth};
        for (char cell : cells) {
            if (cell == YELLOW) computer++;
            else if (cell == RED) human++;
            else empty++;
        }
        if (computer > 0 && human > 0) return 0;
        if (computer == 4) return 100_000;
        if (human == 4) return -100_000;
        if (computer == 3 && empty == 1) return 90;
        if (computer == 2 && empty == 2) return 12;
        if (human == 3 && empty == 1) return -110;
        if (human == 2 && empty == 2) return -14;
        return 0;
    }

    private static int drop(char[][] board, int column, char mark) {
        for (int row = ROWS - 1; row >= 0; row--) {
            if (board[row][column] == EMPTY) {
                board[row][column] = mark;
                return row;
            }
        }
        return -1;
    }

    private static boolean isWinningMove(char[][] board, int row, int column, char mark) {
        return 1 + countDirection(board, row, column, mark, 0, 1)
                        + countDirection(board, row, column, mark, 0, -1) >= 4
                || 1 + countDirection(board, row, column, mark, 1, 0)
                        + countDirection(board, row, column, mark, -1, 0) >= 4
                || 1 + countDirection(board, row, column, mark, 1, 1)
                        + countDirection(board, row, column, mark, -1, -1) >= 4
                || 1 + countDirection(board, row, column, mark, 1, -1)
                        + countDirection(board, row, column, mark, -1, 1) >= 4;
    }

    private static int countDirection(char[][] board, int row, int column, char mark, int rowStep, int colStep) {
        int count = 0;
        int nextRow = row + rowStep;
        int nextColumn = column + colStep;
        while (nextRow >= 0 && nextRow < ROWS && nextColumn >= 0 && nextColumn < COLS
                && board[nextRow][nextColumn] == mark) {
            count++;
            nextRow += rowStep;
            nextColumn += colStep;
        }
        return count;
    }

    private static boolean hasWinner(char[][] board, char mark) {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLS; column++) {
                if (board[row][column] == mark && isWinningMove(board, row, column, mark)) return true;
            }
        }
        return false;
    }

    private static boolean hasLegalMove(char[][] board) {
        for (int column = 0; column < COLS; column++) {
            if (board[0][column] == EMPTY) return true;
        }
        return false;
    }

    private static char[] readBoard(List<?> cells, char empty, char first, char second) {
        char[] board = new char[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            Object value = cells.get(i);
            if (!(value instanceof String cell)) {
                throw new IllegalArgumentException("The board contains an invalid cell.");
            }
            char token = cell.isEmpty() ? empty : cell.length() == 1 ? cell.charAt(0) : '?';
            if (token != empty && token != first && token != second) {
                throw new IllegalArgumentException("The board contains an invalid game piece.");
            }
            board[i] = token;
        }
        return board;
    }

    private static int count(char[] board, char target) {
        int total = 0;
        for (char cell : board) if (cell == target) total++;
        return total;
    }

    private static List<String> boardStrings(char[] board) {
        List<String> cells = new ArrayList<>(board.length);
        for (char cell : board) cells.add(cell == EMPTY ? "" : Character.toString(cell));
        return cells;
    }

    private static String requiredString(Map<?, ?> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof String string)) throw new IllegalArgumentException("Missing or invalid " + key + ".");
        return string;
    }

    private static List<?> requiredList(Map<?, ?> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Missing or invalid " + key + ".");
        return list;
    }

    private static int requiredInteger(Map<?, ?> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Missing or invalid " + key + ".");
        return number.intValue();
    }

    private static String errorJson(String message) {
        return Json.stringify(Map.of("error", message == null ? "Invalid request." : message));
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void servePage(HttpExchange exchange, String requestPath) throws IOException {
        String relative = "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
        if (!relative.equals("index.html") && !relative.equals("styles.css") && !relative.equals("app.js")) {
            sendJson(exchange, 404, errorJson("Page not found."));
            return;
        }
        Path page = WEB_ROOT.resolve(relative).normalize();
        if (!page.startsWith(WEB_ROOT) || !Files.isRegularFile(page)) {
            sendJson(exchange, 404, errorJson("Page not found."));
            return;
        }
        String type = relative.endsWith(".css") ? "text/css; charset=utf-8"
                : relative.endsWith(".js") ? "text/javascript; charset=utf-8"
                : "text/html; charset=utf-8";
        byte[] bytes = Files.readAllBytes(page);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final class Json {
        private final String source;
        private int index;

        private Json(String source) {
            this.source = source;
        }

        private static Object parse(String source) {
            Json parser = new Json(source);
            Object value = parser.readValue();
            parser.skipWhitespace();
            if (parser.index != source.length()) throw new IllegalArgumentException("Invalid JSON request.");
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (index >= source.length()) throw new IllegalArgumentException("Empty JSON request.");
            return switch (source.charAt(index)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            index++;
            Map<String, Object> values = new HashMap<>();
            skipWhitespace();
            if (consume('}')) return values;
            while (true) {
                skipWhitespace();
                if (index >= source.length() || source.charAt(index) != '"') {
                    throw new IllegalArgumentException("Invalid JSON object.");
                }
                String key = readString();
                skipWhitespace();
                require(':');
                if (values.put(key, readValue()) != null) {
                    throw new IllegalArgumentException("Duplicate JSON field.");
                }
                skipWhitespace();
                if (consume('}')) return values;
                require(',');
            }
        }

        private List<Object> readArray() {
            index++;
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return values;
            while (true) {
                values.add(readValue());
                skipWhitespace();
                if (consume(']')) return values;
                require(',');
            }
        }

        private String readString() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (index < source.length()) {
                char character = source.charAt(index++);
                if (character == '"') return result.toString();
                if (character == '\\') {
                    if (index >= source.length()) break;
                    char escaped = source.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(readUnicode());
                        default -> throw new IllegalArgumentException("Invalid JSON string.");
                    }
                } else {
                    if (character < 0x20) throw new IllegalArgumentException("Invalid JSON string.");
                    result.append(character);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string.");
        }

        private char readUnicode() {
            if (index + 4 > source.length()) throw new IllegalArgumentException("Invalid JSON escape.");
            try {
                char value = (char) Integer.parseInt(source.substring(index, index + 4), 16);
                index += 4;
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid JSON escape.");
            }
        }

        private Number readNumber() {
            int start = index;
            if (index < source.length() && source.charAt(index) == '-') index++;
            int digits = index;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            if (digits == index) throw new IllegalArgumentException("Invalid JSON value.");
            try {
                return Integer.parseInt(source.substring(start, index));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("JSON number is out of range.");
            }
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private boolean consume(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!consume(expected)) throw new IllegalArgumentException("Invalid JSON request.");
        }

        private static String stringify(Object value) {
            if (value == null) return "null";
            if (value instanceof String string) {
                StringBuilder result = new StringBuilder("\"");
                for (int i = 0; i < string.length(); i++) {
                    char character = string.charAt(i);
                    switch (character) {
                        case '"' -> result.append("\\\"");
                        case '\\' -> result.append("\\\\");
                        case '\b' -> result.append("\\b");
                        case '\f' -> result.append("\\f");
                        case '\n' -> result.append("\\n");
                        case '\r' -> result.append("\\r");
                        case '\t' -> result.append("\\t");
                        default -> {
                            if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
                            else result.append(character);
                        }
                    }
                }
                return result.append('"').toString();
            }
            if (value instanceof Number || value instanceof Boolean) return value.toString();
            if (value instanceof List<?> list) {
                List<String> items = new ArrayList<>();
                for (Object item : list) items.add(stringify(item));
                return "[" + String.join(",", items) + "]";
            }
            if (value instanceof Map<?, ?> map) {
                List<String> entries = new ArrayList<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new IllegalArgumentException("JSON object keys must be strings.");
                    }
                    entries.add(stringify(key) + ":" + stringify(entry.getValue()));
                }
                return "{" + String.join(",", entries) + "}";
            }
            throw new IllegalArgumentException("Unsupported JSON response value.");
        }
    }
}
