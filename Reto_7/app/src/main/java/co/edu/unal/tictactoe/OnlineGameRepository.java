package co.edu.unal.tictactoe;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

public final class OnlineGameRepository {
    private final DatabaseReference mGame;

    public OnlineGameRepository(DatabaseReference root, String roomId) {
        mGame = root.child("games").child(roomId);
    }

    public DatabaseReference reference() { return mGame; }

    public Task<Void> initialize() { return transact(true, ' ', -1, -1); }

    public Task<Void> move(char player, int cell, int expectedMoves) {
        return transact(false, player, cell, expectedMoves);
    }

    private Task<Void> transact(boolean initialize, char player, int cell, int expectedMoves) {
        TaskCompletionSource<Void> completion = new TaskCompletionSource<>();
        mGame.runTransaction(new Transaction.Handler() {
            @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData data) {
                if (initialize) {
                    if (data.getValue() != null) return Transaction.abort();
                    data.setValue(OnlineGame.empty().values());
                } else {
                    if (data.getValue() == null) return Transaction.success(data);
                    Long moves = data.child("moves").getValue(Long.class);
                    Long result = data.child("result").getValue(Long.class);
                    if (moves == null || result == null || moves != expectedMoves) return Transaction.abort();
                    char[] board = new char[9];
                    for (int i = 0; i < 9; i++) {
                        String value = data.child("board").child(Integer.toString(i)).getValue(String.class);
                        if (value == null || !value.matches("[-XO]")) return Transaction.abort();
                        board[i] = "-".equals(value) ? ' ' : value.charAt(0);
                    }
                    try {
                        OnlineGame game = new OnlineGame(board, moves.intValue(), result.intValue(), "-1");
                        data.setValue(game.move(player, cell).values());
                    } catch (IllegalStateException exception) {
                        return Transaction.abort();
                    }
                }
                return Transaction.success(data);
            }

            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot data) {
                if (error != null) completion.setException(error.toException());
                else if (data != null && data.exists() && (initialize || committed)) completion.setResult(null);
                else completion.setException(new IllegalStateException("MOVE_NOT_COMMITTED"));
            }
        }, false);
        return completion.getTask();
    }

    public static OnlineGame read(DataSnapshot data) {
        if (!data.exists()) return null;
        Long moves = data.child("moves").getValue(Long.class);
        Long result = data.child("result").getValue(Long.class);
        String lastCell = data.child("lastCell").getValue(String.class);
        if (moves == null || moves < 0 || moves > 9 || result == null || result < 0
                || result > 3 || lastCell == null) return null;
        char[] board = new char[9];
        for (int i = 0; i < 9; i++) {
            String value = data.child("board").child(Integer.toString(i)).getValue(String.class);
            if (value == null || !value.matches("[-XO]")) return null;
            board[i] = "-".equals(value) ? ' ' : value.charAt(0);
        }
        return new OnlineGame(board, moves.intValue(), result.intValue(), lastCell);
    }
}
