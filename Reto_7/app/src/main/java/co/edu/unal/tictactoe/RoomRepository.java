package co.edu.unal.tictactoe;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;

import java.util.HashMap;
import java.util.Map;

/** Operaciones de salas. Las reglas del servidor validan también cada transición. */
public final class RoomRepository {
    private final DatabaseReference mRooms;
    private final String mUid;

    public RoomRepository(DatabaseReference root, String uid) {
        mRooms = root.child("rooms");
        mUid = uid;
    }

    public Query availableRooms() {
        return mRooms.orderByChild("status").equalTo(OnlineRoom.WAITING).limitToLast(50);
    }

    public DatabaseReference room(String id) {
        return mRooms.child(id);
    }

    public String newRoomId() {
        return mRooms.push().getKey();
    }

    public Task<Void> create(String id) {
        Map<String, Object> values = new HashMap<>();
        values.put("host", mUid);
        values.put("guest", "");
        values.put("status", OnlineRoom.WAITING);
        values.put("createdAt", ServerValue.TIMESTAMP);
        return room(id).setValue(values);
    }

    public Task<Void> join(String id) {
        return transition(id, false);
    }

    public Task<Void> close(String id) {
        return transition(id, true);
    }

    private Task<Void> transition(String id, boolean closing) {
        TaskCompletionSource<Void> completion = new TaskCompletionSource<>();
        room(id).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                // Firebase puede entregar inicialmente una caché vacía. Dejamos que
                // el servidor la contraste y reintente con los datos actuales.
                if (data.getValue() == null) return Transaction.success(data);
                String host = data.child("host").getValue(String.class);
                String guest = data.child("guest").getValue(String.class);
                String status = data.child("status").getValue(String.class);
                boolean member = mUid.equals(host) || mUid.equals(guest);
                if (closing) {
                    if (!member) return Transaction.abort();
                    data.child("status").setValue(OnlineRoom.CLOSED);
                } else {
                    if (OnlineRoom.READY.equals(status) && mUid.equals(guest)) {
                        return Transaction.success(data);
                    }
                    if (!OnlineRoom.WAITING.equals(status) || !"".equals(guest)
                            || mUid.equals(host)) return Transaction.abort();
                    data.child("guest").setValue(mUid);
                    data.child("status").setValue(OnlineRoom.READY);
                }
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot data) {
                if (error != null) {
                    completion.setException(error.toException());
                } else if (!committed || data == null || !data.exists()) {
                    completion.setException(new IllegalStateException("ROOM_UNAVAILABLE"));
                } else {
                    completion.setResult(null);
                }
            }
        }, false);
        return completion.getTask();
    }

    public static OnlineRoom read(DataSnapshot snapshot) {
        String host = snapshot.child("host").getValue(String.class);
        String guest = snapshot.child("guest").getValue(String.class);
        String status = snapshot.child("status").getValue(String.class);
        if (snapshot.getKey() == null || host == null || guest == null || status == null) {
            return null;
        }
        return new OnlineRoom(snapshot.getKey(), host, guest, status);
    }
}
