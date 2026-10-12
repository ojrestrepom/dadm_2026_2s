package co.edu.unal.tictactoe;

import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {

    private static final String GAME_STATE = "game_state";
    private static final String GAME_PREFERENCES = "game_preferences";
    private static final String HUMAN_WINS = "human_wins";
    private static final String COMPUTER_WINS = "computer_wins";
    private static final String TIES = "ties";
    private static final String DIFFICULTY = "difficulty";
    private SharedPreferences mGamePreferences;

    private static final String SOUND_PREFERENCES = "sound_preferences";
    private static final String SOUND_ENABLED = "sound_enabled";

    private SharedPreferences mSoundPreferences;
    private boolean mSoundEnabled;
    private SoundPool mSoundPool;
    private int mHumanSound;
    private int mComputerSound;
    private boolean mHumanSoundLoaded;
    private boolean mComputerSoundLoaded;

    private TicTacToeGame mGame;

    private BoardView mBoardView;
    private Button mNewGameButton;

    private TextView mInfoTextView;
    private TextView mHumanWinsTextView;
    private TextView mTiesTextView;
    private TextView mComputerWinsTextView;

    private static final long COMPUTER_MOVE_DELAY_MS = 1000L;
    private final Handler mComputerHandler = new Handler(Looper.getMainLooper());
    private final Runnable mComputerMove = this::makeComputerMove;
    private boolean mComputerTurn;
    private boolean mResumed;

    private boolean mGameOver;
    private int mHumanWins = 0;
    private int mComputerWins = 0;
    private int mTies = 0;
    private boolean mHumanStarts = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout),
                (view, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(findViewById(R.id.mainLayout));

        mSoundPreferences = getSharedPreferences(SOUND_PREFERENCES, MODE_PRIVATE);
        mSoundEnabled = mSoundPreferences.getBoolean(SOUND_ENABLED, true);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mGame = new TicTacToeGame();
        mGamePreferences = getSharedPreferences(GAME_PREFERENCES, MODE_PRIVATE);
        mHumanWins = mGamePreferences.getInt(HUMAN_WINS, 0);
        mComputerWins = mGamePreferences.getInt(COMPUTER_WINS, 0);
        mTies = mGamePreferences.getInt(TIES, 0);
        int difficulty = mGamePreferences.getInt(DIFFICULTY, 2);
        mGame.setDifficultyLevel(difficulty == 0 ? TicTacToeGame.DifficultyLevel.Easy
                : difficulty == 1 ? TicTacToeGame.DifficultyLevel.Harder
                : TicTacToeGame.DifficultyLevel.Expert);

        mBoardView = findViewById(R.id.board);
        mBoardView.setGame(mGame);
        mBoardView.setOnCellSelectedListener(this::makeHumanMove);

        mInfoTextView = findViewById(R.id.infoTextView);
        mNewGameButton = findViewById(R.id.newGameButton);
        mNewGameButton.setOnClickListener(v -> startNewGame());

        mHumanWinsTextView = findViewById(R.id.humanWinsTextView);
        mTiesTextView = findViewById(R.id.tiesTextView);
        mComputerWinsTextView = findViewById(R.id.computerWinsTextView);
        Bundle gameState = savedInstanceState == null ? null
                : savedInstanceState.getBundle(GAME_STATE);
        if (gameState == null) {
            startNewGame();
        } else {
            restoreGameState(gameState);
        }
        updateStats();
        setupOptionsMenu();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle state = new Bundle();
        state.putCharArray("board", mGame.getBoardState());
        state.putString("difficulty", mGame.getDifficultyLevel().name());
        state.putBoolean("game_over", mGameOver);
        state.putBoolean("computer_turn", mComputerTurn);
        state.putBoolean("human_starts", mHumanStarts);
        state.putCharSequence("info", mInfoTextView.getText());
        outState.putBundle(GAME_STATE, state);
    }

    private void restoreGameState(Bundle state) {
        mGame.setBoardState(state.getCharArray("board"));
        String difficulty = state.getString("difficulty", TicTacToeGame.DifficultyLevel.Expert.name());
        try {
            mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.valueOf(difficulty));
        } catch (IllegalArgumentException exception) {
            mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Expert);
        }
        mGameOver = state.getBoolean("game_over");
        mComputerTurn = state.getBoolean("computer_turn");
        mHumanStarts = state.getBoolean("human_starts");
        mInfoTextView.setText(state.getCharSequence("info"));
        // Restaurar no es jugar: no reproducir sonidos ni volver a sumar el resultado.
        // onResume programa una sola respuesta si el turno restaurado es de Android.
        updateBoardInput();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHumanSoundLoaded = false;
        mComputerSoundLoaded = false;
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        mSoundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            // Ignorar callbacks de una instancia liberada al pausar la actividad.
            if (soundPool != mSoundPool || status != 0) return;
            if (sampleId == mHumanSound) mHumanSoundLoaded = true;
            if (sampleId == mComputerSound) mComputerSoundLoaded = true;
        });
        mHumanSound = mSoundPool.load(this, R.raw.human_move, 1);
        mComputerSound = mSoundPool.load(this, R.raw.computer_move, 1);
        mResumed = true;
        scheduleComputerMove();
        updateBoardInput();
    }

    @Override
    protected void onPause() {
        mResumed = false;
        mComputerHandler.removeCallbacks(mComputerMove);
        updateBoardInput();
        if (mSoundPool != null) {
            mSoundPool.setOnLoadCompleteListener(null);
            mSoundPool.release();
            mSoundPool = null;
        }
        mHumanSoundLoaded = false;
        mComputerSoundLoaded = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mComputerHandler.removeCallbacks(mComputerMove);
        super.onDestroy();
    }

    private void updateBoardInput() {
        mBoardView.setEnabled(mResumed && !mGameOver && !mComputerTurn && !isFinishing());
        mBoardView.refreshBoard();
    }

    private void scheduleComputerMove() {
        mComputerHandler.removeCallbacks(mComputerMove);
        if (mResumed && mComputerTurn && !mGameOver && !isFinishing()) {
            mComputerHandler.postDelayed(mComputerMove, COMPUTER_MOVE_DELAY_MS);
        }
    }

    private void makeComputerMove() {
        if (!mResumed || !mComputerTurn || mGameOver || isFinishing()) return;

        int winner = mGame.checkForWinner();
        if (winner == 0) {
            // Consultar la dificultad vigente al ejecutar la jugada.
            setMove(TicTacToeGame.COMPUTER_PLAYER, mGame.getComputerMove());
            winner = mGame.checkForWinner();
        }
        mComputerTurn = false;
        updateGameStatus(winner);
        updateBoardInput();
    }

    private void playMoveSound(char player) {
        if (!mSoundEnabled || mSoundPool == null) return;
        if (player == TicTacToeGame.HUMAN_PLAYER && mHumanSoundLoaded) {
            mSoundPool.play(mHumanSound, 0.7f, 0.7f, 1, 0, 1f);
        } else if (player == TicTacToeGame.COMPUTER_PLAYER && mComputerSoundLoaded) {
            mSoundPool.play(mComputerSound, 0.7f, 0.7f, 1, 0, 1f);
        }
    }

    private void setupOptionsMenu() {
        addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.options_menu, menu);
                menu.findItem(R.id.sound).setChecked(mSoundEnabled);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.new_game) {
                    startNewGame();
                    return true;
                }
                if (menuItem.getItemId() == R.id.ai_difficulty) {
                    showDifficultyDialog();
                    return true;
                }
                if (menuItem.getItemId() == R.id.reset_scores) {
                    resetScores();
                    return true;
                }
                if (menuItem.getItemId() == R.id.quit) {
                    showQuitDialog();
                    return true;
                }
                if (menuItem.getItemId() == R.id.sound) {
                    mSoundEnabled = !mSoundEnabled;
                    menuItem.setChecked(mSoundEnabled);
                    mSoundPreferences.edit().putBoolean(SOUND_ENABLED, mSoundEnabled).apply();
                    if (!mSoundEnabled && mSoundPool != null) {
                        mSoundPool.autoPause();
                    }
                    return true;
                }
                if (menuItem.getItemId() == R.id.about) {
                    showAboutDialog();
                    return true;
                }
                return false;
            }
        }, this);
    }


    private void showDifficultyDialog() {
        final TicTacToeGame.DifficultyLevel[] levels = {
                TicTacToeGame.DifficultyLevel.Easy,
                TicTacToeGame.DifficultyLevel.Harder,
                TicTacToeGame.DifficultyLevel.Expert
        };
        final CharSequence[] labels = {
                getString(R.string.difficulty_easy),
                getString(R.string.difficulty_harder),
                getString(R.string.difficulty_expert)
        };

        int selected = 0;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == mGame.getDifficultyLevel()) {
                selected = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.difficulty_choose)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    changeDifficulty(levels[which]);
                    dialog.dismiss();
                    Toast.makeText(this, labels[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void changeDifficulty(TicTacToeGame.DifficultyLevel level) {
        mGame.setDifficultyLevel(level);
        // Codigos estables: no dependen del orden de las constantes del enum.
        int value = level == TicTacToeGame.DifficultyLevel.Easy ? 0
                : level == TicTacToeGame.DifficultyLevel.Harder ? 1 : 2;
        mGamePreferences.edit().putInt(DIFFICULTY, value).apply();
    }

    private void saveScores() {
        mGamePreferences.edit()
                .putInt(HUMAN_WINS, mHumanWins)
                .putInt(COMPUTER_WINS, mComputerWins)
                .putInt(TIES, mTies)
                .apply();
    }

    private void resetScores() {
        mHumanWins = 0;
        mComputerWins = 0;
        mTies = 0;
        saveScores();
        updateStats();
    }

    private void showQuitDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.quit)
                .setMessage(R.string.quit_question)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    mComputerHandler.removeCallbacks(mComputerMove);
                    finish();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about)
                .setView(R.layout.about_dialog)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void startNewGame() {
        mComputerHandler.removeCallbacks(mComputerMove);
        mGame.clearBoard();
        mGameOver = false;
        mComputerTurn = !mHumanStarts;

        if (mHumanStarts) {
            mInfoTextView.setText(R.string.first_human);
        } else {
            mInfoTextView.setText(R.string.first_computer);
        }

        mHumanStarts = !mHumanStarts;
        updateBoardInput();
        scheduleComputerMove();
    }

    private void makeHumanMove(int location) {
        if (!mResumed || mComputerTurn || mGameOver || isFinishing()
                || !setMove(TicTacToeGame.HUMAN_PLAYER, location)) return;

        int winner = mGame.checkForWinner();
        if (winner == 0) {
            mComputerTurn = true;
            mInfoTextView.setText(R.string.turn_computer);
            updateBoardInput();
            scheduleComputerMove();
        } else {
            updateGameStatus(winner);
            updateBoardInput();
        }
    }

    private boolean setMove(char player, int location) {
        if (!mGame.setMove(player, location)) {
            return false;
        }
        mBoardView.refreshBoard();
        playMoveSound(player);
        return true;
    }

    private void updateGameStatus(int winner) {
        if (winner == 0) {
            mInfoTextView.setText(R.string.turn_human);
            return;
        }

        mGameOver = true;

        if (winner == 1) {
            mTies++;
            mInfoTextView.setText(R.string.result_tie);
        } else if (winner == 2) {
            mHumanWins++;
            mInfoTextView.setText(R.string.result_human_wins);
        } else if (winner == 3) {
            mComputerWins++;
            mInfoTextView.setText(R.string.result_computer_wins);
        }

        saveScores();
        updateStats();
    }
    private void updateStats() {
        mHumanWinsTextView.setText(String.valueOf(mHumanWins));
        mTiesTextView.setText(String.valueOf(mTies));
        mComputerWinsTextView.setText(String.valueOf(mComputerWins));
    }
}