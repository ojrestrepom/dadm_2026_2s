package co.edu.unal.tictactoe;

import java.util.HashMap;
import java.util.Map;

/** Instantánea inmutable de una partida confirmada por Firebase. */
public final class OnlineGame {
    private final char[] mBoard;
    public final int moves;
    public final int result;
    public final String lastCell;

    public OnlineGame(char[] board, int moves, int result, String lastCell) {
        mBoard = board.clone();
        this.moves = moves;
        this.result = result;
        this.lastCell = lastCell;
    }

    public static OnlineGame empty() {
        return new OnlineGame(new TicTacToeGame().getBoardState(), 0, 0, "-1");
    }

    public char[] board() { return mBoard.clone(); }
    public char turn() { return moves % 2 == 0 ? 'X' : 'O'; }

    public OnlineGame move(char player, int cell) {
        if (result != 0 || player != turn() || cell < 0 || cell >= 9
                || mBoard[cell] != TicTacToeGame.OPEN_SPOT) {
            throw new IllegalStateException("INVALID_MOVE");
        }
        TicTacToeGame game = new TicTacToeGame();
        game.setBoardState(mBoard);
        game.setMove(player, cell);
        return new OnlineGame(game.getBoardState(), moves + 1,
                game.checkForWinner(), Integer.toString(cell));
    }

    public Map<String, Object> values() {
        Map<String, Object> board = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            board.put(Integer.toString(i), mBoard[i] == ' ' ? "-" : String.valueOf(mBoard[i]));
        }
        Map<String, Object> values = new HashMap<>();
        values.put("board", board);
        values.put("moves", moves);
        values.put("result", result);
        values.put("lastCell", lastCell);
        return values;
    }
}
