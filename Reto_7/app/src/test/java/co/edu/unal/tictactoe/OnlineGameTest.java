package co.edu.unal.tictactoe;

import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineGameTest {
    @Test public void winsStopMovesAndSnapshotsAreIndependent() {
        OnlineGame start = OnlineGame.empty();
        OnlineGame game = start.move('X', 0).move('O', 3).move('X', 1).move('O', 4).move('X', 2);
        assertEquals(2, game.result);
        assertEquals(5, game.moves);
        assertEquals(' ', start.board()[0]);
        char[] copy = game.board();
        copy[0] = 'O';
        assertEquals('X', game.board()[0]);
        assertThrows(IllegalStateException.class, () -> game.move('O', 8));
    }

    @Test public void rejectsWrongTurnAndOccupiedCell() {
        assertThrows(IllegalStateException.class, () -> OnlineGame.empty().move('O', 0));
        OnlineGame game = OnlineGame.empty().move('X', 0);
        assertThrows(IllegalStateException.class, () -> game.move('O', 0));
        assertThrows(IllegalStateException.class, () -> game.move('X', 1));
        assertThrows(IllegalStateException.class, () -> game.move('O', 9));
    }

    @Test public void detectsDrawAndOWin() {
        int[] draw = {0, 1, 2, 4, 3, 5, 7, 6, 8};
        OnlineGame game = OnlineGame.empty();
        for (int cell : draw) game = game.move(game.turn(), cell);
        assertEquals(1, game.result);
        assertEquals(9, game.moves);
        OnlineGame oWins = OnlineGame.empty().move('X', 0).move('O', 3)
                .move('X', 1).move('O', 4).move('X', 8).move('O', 5);
        assertEquals(3, oWins.result);
    }
}
