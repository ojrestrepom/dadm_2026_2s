package co.edu.unal.tictactoe;

import org.junit.Test;
import static org.junit.Assert.*;

public class TicTacToeGameTest {
    @Test
    public void acceptedMovesAreReadableAndCannotBeOverwritten() {
        TicTacToeGame game = new TicTacToeGame();
        for (int i = 0; i < TicTacToeGame.BOARD_SIZE; i++) {
            assertEquals(TicTacToeGame.OPEN_SPOT, game.getBoardOccupant(i));
            char player = i % 2 == 0 ? TicTacToeGame.HUMAN_PLAYER : TicTacToeGame.COMPUTER_PLAYER;
            assertTrue(game.setMove(player, i));
            assertEquals(player, game.getBoardOccupant(i));
            assertFalse(game.setMove(player == 'X' ? 'O' : 'X', i));
            assertEquals(player, game.getBoardOccupant(i));
        }
    }

    @Test
    public void invalidMovesLeaveBoardUntouched() {
        TicTacToeGame game = new TicTacToeGame();
        assertFalse(game.setMove('X', -1));
        assertFalse(game.setMove('O', 9));
        assertFalse(game.setMove(' ', 0));
        assertFalse(game.setMove('Z', 0));
        for (int i = 0; i < 9; i++) assertEquals(' ', game.getBoardOccupant(i));
    }

    @Test
    public void invalidBoardQueriesFailClearly() {
        TicTacToeGame game = new TicTacToeGame();
        assertThrows(IllegalArgumentException.class, () -> game.getBoardOccupant(-1));
        assertThrows(IllegalArgumentException.class, () -> game.getBoardOccupant(9));
    }

    @Test
    public void restartPreservesEachDifficultyAndClearsAllCells() {
        TicTacToeGame game = new TicTacToeGame();
        assertEquals(TicTacToeGame.DifficultyLevel.Expert, game.getDifficultyLevel());
        for (TicTacToeGame.DifficultyLevel level : TicTacToeGame.DifficultyLevel.values()) {
            game.setDifficultyLevel(level);
            game.setMove('X', 4);
            game.clearBoard();
            assertEquals(level, game.getDifficultyLevel());
            for (int i = 0; i < 9; i++) assertEquals(' ', game.getBoardOccupant(i));
        }
    }

    @Test
    public void harderAndExpertWinBeforeBlockingWithoutChangingBoard() {
        for (TicTacToeGame.DifficultyLevel level : new TicTacToeGame.DifficultyLevel[] {
                TicTacToeGame.DifficultyLevel.Harder, TicTacToeGame.DifficultyLevel.Expert}) {
            TicTacToeGame game = new TicTacToeGame();
            game.setDifficultyLevel(level);
            game.setMove('O', 0);
            game.setMove('O', 1);
            game.setMove('X', 3);
            game.setMove('X', 4);
            assertEquals(2, game.getComputerMove());
            assertEquals(' ', game.getBoardOccupant(2));
            assertEquals(' ', game.getBoardOccupant(5));
            assertEquals(0, game.checkForWinner());
            assertTrue(game.setMove('O', 2));
            assertEquals(3, game.checkForWinner());
        }
    }

    @Test
    public void expertBlocksAndAllLevelsHandleLastCellAndFullBoard() {
        TicTacToeGame game = new TicTacToeGame();
        game.setMove('X', 0);
        game.setMove('X', 1);
        assertEquals(2, game.getComputerMove());
        assertEquals(' ', game.getBoardOccupant(2));
        for (TicTacToeGame.DifficultyLevel level : TicTacToeGame.DifficultyLevel.values()) {
            game.clearBoard();
            game.setDifficultyLevel(level);
            char[] cells = {'X', 'O', 'X', 'X', 'O', 'O', 'O', 'X'};
            for (int i = 0; i < cells.length; i++) game.setMove(cells[i], i);
            assertEquals(8, game.getComputerMove());
            assertEquals(' ', game.getBoardOccupant(8));
            assertTrue(game.setMove('X', 8));
            assertEquals(1, game.checkForWinner());
            assertEquals(-1, game.getComputerMove());
        }
    }

    @Test
    public void boardStateUsesDefensiveCopies() {
        TicTacToeGame game = new TicTacToeGame();
        char[] state = {'X', 'O', ' ', ' ', 'X', ' ', ' ', ' ', 'O'};
        game.setBoardState(state);
        state[0] = ' ';
        assertEquals('X', game.getBoardOccupant(0));
        char[] exported = game.getBoardState();
        exported[1] = 'X';
        assertEquals('O', game.getBoardOccupant(1));
        TicTacToeGame restored = new TicTacToeGame();
        restored.setBoardState(game.getBoardState());
        assertArrayEquals(game.getBoardState(), restored.getBoardState());
    }

    @Test
    public void invalidStateIsRejectedWithoutPartialChanges() {
        TicTacToeGame game = new TicTacToeGame();
        game.setMove('X', 4);
        char[] original = game.getBoardState();
        assertThrows(IllegalArgumentException.class, () -> game.setBoardState(null));
        assertThrows(IllegalArgumentException.class, () -> game.setBoardState(new char[8]));
        assertThrows(IllegalArgumentException.class, () -> game.setBoardState(
                new char[] {'O', ' ', ' ', ' ', ' ', ' ', ' ', ' ', '?'}));
        assertArrayEquals(original, game.getBoardState());
    }
}