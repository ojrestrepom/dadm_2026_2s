package co.edu.unal.tictactoe;

import android.os.Bundle;
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

    private TicTacToeGame mGame;

    private Button[] mBoardButtons;
    private Button mNewGameButton;

    private TextView mInfoTextView;
    private TextView mHumanWinsTextView;
    private TextView mTiesTextView;
    private TextView mComputerWinsTextView;

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

        mGame = new TicTacToeGame();

        mBoardButtons = new Button[TicTacToeGame.BOARD_SIZE];
        mBoardButtons[0] = findViewById(R.id.button0);
        mBoardButtons[1] = findViewById(R.id.button1);
        mBoardButtons[2] = findViewById(R.id.button2);
        mBoardButtons[3] = findViewById(R.id.button3);
        mBoardButtons[4] = findViewById(R.id.button4);
        mBoardButtons[5] = findViewById(R.id.button5);
        mBoardButtons[6] = findViewById(R.id.button6);
        mBoardButtons[7] = findViewById(R.id.button7);
        mBoardButtons[8] = findViewById(R.id.button8);

        mInfoTextView = findViewById(R.id.infoTextView);
        mNewGameButton = findViewById(R.id.newGameButton);
        mNewGameButton.setOnClickListener(v -> startNewGame());

        mHumanWinsTextView = findViewById(R.id.humanWinsTextView);
        mTiesTextView = findViewById(R.id.tiesTextView);
        mComputerWinsTextView = findViewById(R.id.computerWinsTextView);
        updateStats();
        startNewGame();
        setupOptionsMenu();
    }

    private void setupOptionsMenu() {
        addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.options_menu, menu);
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
                if (menuItem.getItemId() == R.id.quit) {
                    showQuitDialog();
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
                    mGame.setDifficultyLevel(levels[which]);
                    dialog.dismiss();
                    Toast.makeText(this, labels[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showQuitDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.quit)
                .setMessage(R.string.quit_question)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, (dialog, which) -> finish())
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
        mGame.clearBoard();
        mGameOver = false;

        for (int i = 0; i < mBoardButtons.length; i++) {
            final int location = i;

            mBoardButtons[i].setText("");
            mBoardButtons[i].setEnabled(true);
            mBoardButtons[i].setOnClickListener(v -> makeHumanMove(location));
        }

        if (mHumanStarts) {
            mInfoTextView.setText(R.string.first_human);
        } else {
            mInfoTextView.setText(R.string.first_computer);

            int move = mGame.getComputerMove();
            setMove(TicTacToeGame.COMPUTER_PLAYER, move);

            mInfoTextView.setText(R.string.turn_human);
        }

        mHumanStarts = !mHumanStarts;
    }

    private void makeHumanMove(int location) {
        if (mGameOver || !mBoardButtons[location].isEnabled()) return;

        setMove(TicTacToeGame.HUMAN_PLAYER, location);

        int winner = mGame.checkForWinner();

        if (winner == 0) {
            mInfoTextView.setText(R.string.turn_computer);

            int move = mGame.getComputerMove();
            setMove(TicTacToeGame.COMPUTER_PLAYER, move);

            winner = mGame.checkForWinner();
        }

        updateGameStatus(winner);
    }

    private void setMove(char player, int location) {
        mGame.setMove(player, location);

        mBoardButtons[location].setText(String.valueOf(player));
        mBoardButtons[location].setEnabled(false);

        if (player == TicTacToeGame.HUMAN_PLAYER) {
            mBoardButtons[location].setTextColor(getColor(android.R.color.holo_green_dark));
        } else {
            mBoardButtons[location].setTextColor(getColor(android.R.color.holo_red_dark));
        }
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

        updateStats();
    }
    private void updateStats() {
        mHumanWinsTextView.setText(String.valueOf(mHumanWins));
        mTiesTextView.setText(String.valueOf(mTies));
        mComputerWinsTextView.setText(String.valueOf(mComputerWins));
    }
}