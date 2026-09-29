package co.edu.unal.tictactoe;

import android.os.SystemClock;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ComputerTurnTest {

    private static TicTacToeGame game(MainActivity activity) {
        try {
            Field field = MainActivity.class.getDeclaredField("mGame");
            field.setAccessible(true);
            return (TicTacToeGame) field.get(activity);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void move(MainActivity activity, int cell) {
        try {
            Method method = MainActivity.class.getDeclaredMethod("makeHumanMove", int.class);
            method.setAccessible(true);
            method.invoke(activity, cell);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int count(MainActivity activity, char player) {
        int count = 0;
        for (int i = 0; i < TicTacToeGame.BOARD_SIZE; i++) {
            if (game(activity).getBoardOccupant(i) == player) count++;
        }
        return count;
    }

    @Test
    public void computerWaitsAndHumanCannotPlayTwice() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                move(activity, 0);
                move(activity, 1);
                assertEquals(1, count(activity, 'X'));
                assertEquals(0, count(activity, 'O'));
                assertFalse(activity.findViewById(R.id.board).isEnabled());
                assertEquals(activity.getString(R.string.turn_computer),
                        ((TextView) activity.findViewById(R.id.infoTextView)).getText().toString());
            });
            // Sleep only on the instrumentation thread, never on the UI thread.
            SystemClock.sleep(1400);
            scenario.onActivity(activity -> {
                assertEquals(1, count(activity, 'O'));
                assertTrue(activity.findViewById(R.id.board).isEnabled());
            });
            SystemClock.sleep(1200);
            scenario.onActivity(activity -> assertEquals(1, count(activity, 'O')));
        }
    }

    @Test
    public void newGameDiscardsThePreviousPendingMove() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                move(activity, 0);
                activity.findViewById(R.id.newGameButton).performClick();
                assertFalse(activity.findViewById(R.id.board).isEnabled());
                assertEquals(0, count(activity, 'O'));
                activity.findViewById(R.id.newGameButton).performClick();
                assertTrue(activity.findViewById(R.id.board).isEnabled());
            });
            SystemClock.sleep(1400);
            scenario.onActivity(activity -> {
                assertEquals(0, count(activity, 'X'));
                assertEquals(0, count(activity, 'O'));
            });
        }
    }

    @Test
    public void pausedTurnResumesOnlyOnce() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> move(activity, 0));
            scenario.moveToState(Lifecycle.State.CREATED);
            SystemClock.sleep(1400);
            scenario.onActivity(activity -> assertEquals(0, count(activity, 'O')));
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> assertFalse(activity.findViewById(R.id.board).isEnabled()));
            SystemClock.sleep(1400);
            scenario.onActivity(activity -> {
                assertEquals(1, count(activity, 'O'));
                assertTrue(activity.findViewById(R.id.board).isEnabled());
            });
        }
    }

    @Test
    public void delayedMoveUsesTheCurrentDifficulty() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                game(activity).setDifficultyLevel(TicTacToeGame.DifficultyLevel.Easy);
                game(activity).setMove('X', 0);
                game(activity).setMove('X', 1);
                game(activity).setMove('O', 4);
                move(activity, 6);
                game(activity).setDifficultyLevel(TicTacToeGame.DifficultyLevel.Expert);
            });
            SystemClock.sleep(1400);
            scenario.onActivity(activity -> assertEquals('O', game(activity).getBoardOccupant(2)));
        }
    }
}