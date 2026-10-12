package co.edu.unal.tictactoe;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/** Conserva las operaciones durante la rotación sin retener una Activity. */
public class LobbyViewModel extends AndroidViewModel {
    public static final class State {
        public final List<OnlineRoom> rooms;
        public final OnlineRoom selected;
        public final String selectedId;
        public final String uid;
        public final boolean connected;
        public final boolean busy;
        public final int message;
        public final OnlineGame game;

        State(LobbyViewModel model) {
            rooms = new ArrayList<>(model.mRooms);
            selected = model.mSelected;
            selectedId = model.mSelectedId;
            uid = model.mUid;
            connected = model.mConnected;
            busy = model.mBusy || model.mAuthenticating;
            message = model.mMessage;
            game = model.mGame;
        }
    }

    private final MutableLiveData<State> mState = new MutableLiveData<>();
    private final SharedPreferences mPreferences;
    private final List<OnlineRoom> mRooms = new ArrayList<>();
    private RoomRepository mRepository;
    private DatabaseReference mRoot;
    private String mUid;
    private String mSelectedId;
    private OnlineRoom mSelected;
    private boolean mStarted;
    private boolean mConnected;
    private boolean mBusy;
    private boolean mAuthenticating;
    private int mListenerGeneration;
    private OnlineGameRepository mGameRepository;
    private ValueEventListener mGameListener;
    private OnlineGame mGame;
    private int mSoundMove = -1;
    private int mMessage = R.string.lobby_connecting;
    private Query mListQuery;
    private ValueEventListener mListListener;
    private DatabaseReference mRoomReference;
    private ValueEventListener mRoomListener;
    private DatabaseReference mConnectionReference;
    private ValueEventListener mConnectionListener;

    public LobbyViewModel(@NonNull Application application) {
        super(application);
        mPreferences = application.getSharedPreferences("online_room", 0);
        publish();
    }

    public LiveData<State> state() { return mState; }

    public void start() {
        mStarted = true;
        if (mRoot != null) attach();
        else connect();
    }

    public void stop() {
        mStarted = false;
        detach();
        mConnected = false;
        publish();
    }

    public void retry() {
        if (mBusy || mAuthenticating) return;
        mMessage = R.string.lobby_connecting;
        if (mRoot == null) connect();
        else { detach(); attach(); }
    }

    private void connect() {
        if (mAuthenticating) return;
        mAuthenticating = true;
        publish();
        FirebaseConnection.getAuthenticatedRoot().addOnCompleteListener(task -> {
            mAuthenticating = false;
            if (task.isSuccessful()) {
                mRoot = task.getResult();
                mUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                mRepository = new RoomRepository(mRoot, mUid);
                mSelectedId = mPreferences.getString(mUid, null);
                if (mStarted) attach();
            } else mMessage = R.string.lobby_auth_error;
            publish();
        });
    }

    private void attach() {
        if (!mStarted || mRoot == null || mConnectionListener != null) return;
        mConnectionReference = mRoot.child(".info/connected");
        mConnectionListener = listener(snapshot -> {
            mConnected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
            if (mConnected && mMessage == R.string.lobby_connecting) mMessage = 0;
            publish();
        });
        mConnectionReference.addValueEventListener(mConnectionListener);
        mListQuery = mRepository.availableRooms();
        mListListener = listener(snapshot -> {
            mRooms.clear();
            for (DataSnapshot child : snapshot.getChildren()) {
                OnlineRoom room = RoomRepository.read(child);
                if (room != null && room.canJoin(mUid)) mRooms.add(0, room);
            }
            publish();
        });
        mListQuery.addValueEventListener(mListListener);
        observeSelected();
    }

    private interface SnapshotCallback { void accept(DataSnapshot snapshot); }

    private ValueEventListener listener(SnapshotCallback callback) {
        int generation = mListenerGeneration;
        return new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mStarted && generation == mListenerGeneration) callback.accept(snapshot);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                if (!mStarted || generation != mListenerGeneration) return;
                mMessage = error.getCode() == DatabaseError.PERMISSION_DENIED
                        ? R.string.lobby_permission_error : R.string.lobby_network_error;
                publish();
            }
        };
    }

    public void create() {
        if (!canOperate() || mSelectedId != null) return;
        String id = mRepository.newRoomId();
        if (id == null) return;
        select(id); // Guardar antes de enviar: permite recuperar una operación tras recreación.
        execute(mRepository.create(id), true);
    }

    public void join(String id) {
        if (!canOperate() || mSelectedId != null) return;
        select(id);
        execute(mRepository.join(id), true);
    }

    public void close() {
        if (!canOperate() || mSelectedId == null) return;
        execute(mRepository.close(mSelectedId), false);
    }

    public void move(int cell) {
        if (!canOperate() || mGame == null || mGameRepository == null || mSelected == null
                || !OnlineRoom.READY.equals(mSelected.status) || mGame.result != 0) return;
        char player = mUid.equals(mSelected.host) ? 'X' : 'O';
        if (mGame.turn() != player) return;
        String roomId = mSelectedId;
        mBusy = true;
        mMessage = 0;
        publish();
        mGameRepository.move(player, cell, mGame.moves).addOnCompleteListener(task -> {
            mBusy = false;
            if (roomId.equals(mSelectedId) && !task.isSuccessful()) mMessage = R.string.online_move_error;
            publish();
        });
    }

    /** Consumir una sola vez; no reproducir snapshots iniciales ni recuperación de varias jugadas. */
    public char consumeMoveSound() {
        if (mGame == null) return ' ';
        int previous = mSoundMove;
        mSoundMove = mGame.moves;
        return previous >= 0 && mGame.moves == previous + 1
                ? (mGame.moves % 2 == 1 ? 'X' : 'O') : ' ';
    }

    private boolean canOperate() {
        return mRepository != null && mConnected && !mBusy;
    }

    private void execute(Task<Void> task, boolean entering) {
        mBusy = true;
        mMessage = 0;
        publish();
        task.addOnCompleteListener(result -> {
            mBusy = false;
            if (result.isSuccessful()) {
                if (!entering) clearSelection();
                else if (mStarted) observeSelected();
            } else {
                if (entering) clearSelection();
                mMessage = R.string.lobby_operation_error;
            }
            publish();
        });
    }

    private void select(String id) {
        mSelectedId = id;
        mPreferences.edit().putString(mUid, id).apply();
    }

    private void observeSelected() {
        detachRoom();
        if (mSelectedId == null || mBusy || !mStarted) return;
        String selectedId = mSelectedId;
        mRoomReference = mRepository.room(mSelectedId);
        mRoomListener = listener(snapshot -> {
            if (!selectedId.equals(mSelectedId)) return;
            OnlineRoom room = RoomRepository.read(snapshot);
            if (room == null || !room.contains(mUid) || OnlineRoom.CLOSED.equals(room.status)) {
                clearSelection();
                mMessage = R.string.lobby_room_closed;
            } else {
                mSelected = room;
                if (OnlineRoom.READY.equals(room.status)) observeGame(selectedId);
            }
            publish();
        });
        mRoomReference.addValueEventListener(mRoomListener);
    }

    private void clearSelection() {
        detachRoom();
        detachGame();
        mGame = null;
        mSelectedId = null;
        mSelected = null;
        mPreferences.edit().remove(mUid).apply();
    }

    private void detachRoom() {
        if (mRoomReference != null && mRoomListener != null) {
            mRoomReference.removeEventListener(mRoomListener);
        }
        mRoomReference = null;
        mRoomListener = null;
    }

    private void detach() {
        mListenerGeneration++;
        detachRoom();
        detachGame();
        if (mListQuery != null && mListListener != null) mListQuery.removeEventListener(mListListener);
        if (mConnectionReference != null && mConnectionListener != null) {
            mConnectionReference.removeEventListener(mConnectionListener);
        }
        mListListener = null;
        mConnectionListener = null;
    }

    private void publish() { mState.setValue(new State(this)); }

    private void observeGame(String roomId) {
        if (mGameRepository != null || !mStarted) return;
        mGameRepository = new OnlineGameRepository(mRoot, roomId);
        mGameListener = listener(snapshot -> {
            if (!roomId.equals(mSelectedId)) return;
            mGame = OnlineGameRepository.read(snapshot);
            publish();
        });
        mGameRepository.reference().addValueEventListener(mGameListener);
        mGameRepository.initialize().addOnFailureListener(error -> {
            if (roomId.equals(mSelectedId)) {
                mMessage = R.string.online_load_error;
                publish();
            }
        });
    }

    private void detachGame() {
        if (mGameRepository != null && mGameListener != null) {
            mGameRepository.reference().removeEventListener(mGameListener);
        }
        mGameRepository = null;
        mGameListener = null;
        mGame = null;
        mSoundMove = -1;
    }

    @Override protected void onCleared() { stop(); }
}
