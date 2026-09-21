package co.edu.unal.tictactoe;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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