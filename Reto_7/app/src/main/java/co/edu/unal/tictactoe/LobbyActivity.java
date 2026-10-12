package co.edu.unal.tictactoe;

import android.content.Intent;
import android.os.Bundle;
import android.media.AudioManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LobbyActivity extends AppCompatActivity {
    private LobbyViewModel mModel;
    private LobbyViewModel.State mState;
    private TextView mStatus;
    private TextView mRoomTitle;
    private TextView mRoomStatus;
    private TextView mEmpty;
    private LinearLayout mRoomList;
    private View mSelectedPanel;
    private View mAvailablePanel;
    private Button mCreate;
    private Button mLocal;
    private Button mClose;
    private Button mRetry;
    private BoardView mBoard;
    private final TicTacToeGame mBoardGame = new TicTacToeGame();
    private final OnlineMoveSounds mSounds = new OnlineMoveSounds();
    private boolean mResumed;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);
        MaterialToolbar toolbar = findViewById(R.id.lobbyToolbar);
        setSupportActionBar(toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lobbyLayout),
                (view, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(findViewById(R.id.lobbyLayout));
        mStatus = findViewById(R.id.lobbyStatus);
        mRoomTitle = findViewById(R.id.roomTitle);
        mRoomStatus = findViewById(R.id.roomStatus);
        mEmpty = findViewById(R.id.emptyRooms);
        mRoomList = findViewById(R.id.roomList);
        mSelectedPanel = findViewById(R.id.selectedRoomPanel);
        mAvailablePanel = findViewById(R.id.availableRoomsPanel);
        mCreate = findViewById(R.id.createRoom);
        mLocal = findViewById(R.id.localGame);
        mClose = findViewById(R.id.closeRoom);
        mRetry = findViewById(R.id.retryLobby);
        mBoard = findViewById(R.id.onlineBoard);
        mBoard.setGame(mBoardGame);
        mBoard.setPlayerLabels(R.string.online_x_label, R.string.online_o_label);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        CheckBox sound = findViewById(R.id.onlineSound);
        sound.setChecked(getSharedPreferences("sound_preferences", 0).getBoolean("sound_enabled", true));
        sound.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("sound_preferences", 0)
                .edit().putBoolean("sound_enabled", checked).apply());
        mModel = new ViewModelProvider(this).get(LobbyViewModel.class);
        mBoard.setOnCellSelectedListener(cell -> { if (mResumed) mModel.move(cell); });
        mCreate.setOnClickListener(view -> mModel.create());
        mClose.setOnClickListener(view -> confirmClose());
        mRetry.setOnClickListener(view -> mModel.retry());
        mLocal.setOnClickListener(view -> startActivity(new Intent(this, MainActivity.class)));
        mModel.state().observe(this, this::render);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (mState != null && mState.selectedId != null && mState.connected && !mState.busy) confirmClose();
                else finish();
            }
        });
    }

    @Override protected void onStart() { super.onStart(); mModel.start(); }
    @Override protected void onStop() { mModel.stop(); super.onStop(); }

    @Override protected void onResume() {
        super.onResume();
        mSounds.start(this);
        mResumed = true;
        ((CheckBox) findViewById(R.id.onlineSound)).setChecked(
                getSharedPreferences("sound_preferences", 0).getBoolean("sound_enabled", true));
        if (mState != null) render(mState);
    }

    @Override protected void onPause() {
        mResumed = false;
        mBoard.setEnabled(false);
        mSounds.stop();
        super.onPause();
    }

    private void confirmClose() {
        if (mState == null || mState.busy || !mState.connected) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lobby_close_room)
                .setMessage(R.string.lobby_close_question)
                .setPositiveButton(R.string.yes, (dialog, which) -> mModel.close())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void render(LobbyViewModel.State state) {
        mState = state;
        boolean selected = state.selectedId != null;
        boolean enabled = state.connected && !state.busy;
        mStatus.setText(state.busy ? R.string.lobby_working : state.message != 0
                ? state.message : state.connected ? R.string.lobby_connected : R.string.lobby_offline);
        mCreate.setEnabled(enabled && !selected);
        mClose.setEnabled(enabled);
        mLocal.setEnabled(!selected);
        // El modo local sigue disponible incluso si la autenticación tarda o falla.
        mRetry.setEnabled(!state.busy);
        mSelectedPanel.setVisibility(selected ? View.VISIBLE : View.GONE);
        mAvailablePanel.setVisibility(selected ? View.GONE : View.VISIBLE);
        boolean playing = selected && state.selected != null
                && OnlineRoom.READY.equals(state.selected.status);
        mBoard.setVisibility(playing ? View.VISIBLE : View.GONE);
        mBoard.setEnabled(false);
        if (selected) {
            OnlineRoom room = state.selected;
            mRoomTitle.setText(getString(R.string.lobby_room_name, room == null
                    ? state.selectedId.substring(Math.max(0, state.selectedId.length() - 6))
                    : room.displayCode()));
            mRoomStatus.setText(room == null ? getString(R.string.lobby_working)
                    : getString(OnlineRoom.WAITING.equals(room.status)
                    ? R.string.lobby_waiting : R.string.lobby_ready,
                    state.uid.equals(room.host) ? "X" : "O"));
            if (playing && state.game != null) {
                char player = state.uid.equals(room.host) ? 'X' : 'O';
                mBoardGame.setBoardState(state.game.board());
                mBoard.refreshBoard();
                mBoard.setEnabled(mResumed && enabled && state.game.result == 0
                        && state.game.turn() == player);
                int status = state.game.result == 1 ? R.string.result_tie
                        : state.game.result != 0
                        ? ((state.game.result == 2 ? 'X' : 'O') == player
                        ? R.string.result_human_wins : R.string.online_lost)
                        : state.game.turn() == player ? R.string.turn_human : R.string.online_opponent_turn;
                mRoomStatus.setText(getString(R.string.online_status, String.valueOf(player), getString(status)));
                char sound = mModel.consumeMoveSound();
                if (mResumed) mSounds.play(this, sound);
            } else {
                mBoardGame.clearBoard();
                mBoard.refreshBoard();
            }
        }
        mRoomList.removeAllViews();
        mEmpty.setVisibility(state.rooms.isEmpty() ? View.VISIBLE : View.GONE);
        for (OnlineRoom room : state.rooms) {
            MaterialButton button = new MaterialButton(this);
            button.setText(getString(R.string.lobby_join_room, room.displayCode()));
            button.setAllCaps(false);
            button.setEnabled(enabled && !selected);
            button.setOnClickListener(view -> mModel.join(room.id));
            mRoomList.addView(button, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }
}
