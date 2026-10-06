package co.edu.unal.tictactoe;

import android.os.SystemClock;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ComputerTurnTest {

    private SharedPreferences preferences;
    private Map<String, ?> previousPreferences;

    @Before
    public void isolateGamePreferences() {
        Context context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences("game_preferences", Context.MODE_PRIVATE);
        previousPreferences = preferences.getAll();
        preferences.edit().clear().commit();
    }

    @After
    public void restoreGamePreferences() {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : previousPreferences.entrySet()) {
            editor.putInt(entry.getKey(), (Integer) entry.getValue());
        }
        editor.commit();
    }

    private static void changeDifficulty(MainActivity activity, TicTacToeGame.DifficultyLevel level) {
        try {
            Method method = MainActivity.class.getDeclaredMethod("changeDifficulty",
                    TicTacToeGame.DifficultyLevel.class);
            method.setAccessible(true);
            method.invoke(activity, level);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }


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

    @Test
    public void recreationPreservesBoardDifficultyAndNextStarter() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            char[] board = {'X', 'O', ' ', ' ', ' ', ' ', ' ', ' ', ' '};
            scenario.onActivity(activity -> {
                game(activity).setBoardState(board);
                game(activity).setDifficultyLevel(TicTacToeGame.DifficultyLevel.Harder);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertArrayEquals(board, game(activity).getBoardState());
                assertEquals(TicTacToeGame.DifficultyLevel.Harder, game(activity).getDifficultyLevel());
                assertTrue(activity.findViewById(R.id.board).isEnabled());
                activity.findViewById(R.id.newGameButton).performClick();
                assertFalse(activity.findViewById(R.id.board).isEnabled());
                assertEquals(activity.getString(R.string.first_computer),
                        ((TextView) activity.findViewById(R.id.infoTextView)).getText().toString());
            });
        }
    }

    @Test
    public void recreationRestartsPendingComputerTurnExactlyOnce() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> move(activity, 0));
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(1, count(activity, 'X'));
                assertEquals(0, count(activity, 'O'));
                assertFalse(activity.findViewById(R.id.board).isEnabled());
            });
            scenario.moveToState(Lifecycle.State.RESUMED);
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
    public void recreationDoesNotCountFinishedGameAgain() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            char[] board = {'X', 'X', ' ', 'O', 'O', ' ', ' ', ' ', ' '};
            scenario.onActivity(activity -> {
                game(activity).setBoardState(board);
                move(activity, 2);
                assertEquals("1", ((TextView) activity.findViewById(R.id.humanWinsTextView)).getText().toString());
            });
            scenario.recreate();
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(2, game(activity).checkForWinner());
                assertEquals("1", ((TextView) activity.findViewById(R.id.humanWinsTextView)).getText().toString());
                assertEquals(activity.getString(R.string.result_human_wins),
                        ((TextView) activity.findViewById(R.id.infoTextView)).getText().toString());
                assertFalse(activity.findViewById(R.id.board).isEnabled());
            });
        }
    }

    @Test
    public void scoresSurviveClosingAndLaunchingAgain() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                game(activity).setBoardState(new char[] {'X','X',' ','O','O',' ',' ',' ',' '});
                move(activity, 2);
            });
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("1", ((TextView) activity.findViewById(R.id.humanWinsTextView)).getText().toString());
                assertEquals(0, count(activity, 'X'));
                assertEquals(0, count(activity, 'O'));
            });
        }
    }

    @Test
    public void allDifficultyLevelsSurviveFreshLaunches() {
        for (TicTacToeGame.DifficultyLevel level : TicTacToeGame.DifficultyLevel.values()) {
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> changeDifficulty(activity, level));
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> assertEquals(level, game(activity).getDifficultyLevel()));
            }
        }
        preferences.edit().putInt("difficulty", 99).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertEquals(TicTacToeGame.DifficultyLevel.Expert,
                    game(activity).getDifficultyLevel()));
        }
    }

    @Test
    public void resettingScoresKeepsPendingTurnAndDifficulty() {
        preferences.edit().putInt("human_wins", 4).putInt("computer_wins", 3).putInt("ties", 2).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                changeDifficulty(activity, TicTacToeGame.DifficultyLevel.Harder);
                move(activity, 0);
                com.google.android.material.appbar.MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
                assertTrue(toolbar.getMenu().performIdentifierAction(R.id.reset_scores, 0));
                assertEquals("0", ((TextView) activity.findViewById(R.id.humanWinsTextView)).getText().toString());
                assertEquals("0", ((TextView) activity.findViewById(R.id.computerWinsTextView)).getText().toString());
                assertEquals("0", ((TextView) activity.findViewById(R.id.tiesTextView)).getText().toString());
                assertEquals(1, count(activity, 'X'));
                assertFalse(activity.findViewById(R.id.board).isEnabled());
                assertEquals(TicTacToeGame.DifficultyLevel.Harder, game(activity).getDifficultyLevel());
            });
            SystemClock.sleep(1400);
            scenario.onActivity(activity -> assertEquals(1, count(activity, 'O')));
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("0", ((TextView) activity.findViewById(R.id.humanWinsTextView)).getText().toString());
                assertEquals("0", ((TextView) activity.findViewById(R.id.computerWinsTextView)).getText().toString());
                assertEquals("0", ((TextView) activity.findViewById(R.id.tiesTextView)).getText().toString());
                assertEquals(TicTacToeGame.DifficultyLevel.Harder, game(activity).getDifficultyLevel());
            });
        }
    }
}