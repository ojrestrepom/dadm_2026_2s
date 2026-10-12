package co.edu.unal.tictactoe;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class OnlineGameIntegrationTest {
    private final List<FirebaseApp> mApps = new ArrayList<>();
    private final List<String> mRooms = new ArrayList<>();
    private RoomRepository mHost;

    @Before public void optIn() {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runOnlineGameIntegration")));
    }

    private DatabaseReference client() throws Exception {
        FirebaseApp app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                FirebaseApp.getInstance().getOptions(), "game-test-" + UUID.randomUUID());
        mApps.add(app);
        Tasks.await(FirebaseAuth.getInstance(app).signInAnonymously(), 30, TimeUnit.SECONDS);
        return FirebaseDatabase.getInstance(app).getReference();
    }

    private RoomRepository rooms(DatabaseReference root) {
        String uid = FirebaseAuth.getInstance(root.getDatabase().getApp()).getCurrentUser().getUid();
        return new RoomRepository(root, uid);
    }

    private String create(DatabaseReference hostRoot, DatabaseReference guestRoot) throws Exception {
        mHost = rooms(hostRoot);
        String id = mHost.newRoomId();
        Tasks.await(mHost.create(id), 30, TimeUnit.SECONDS);
        mRooms.add(id);
        Tasks.await(rooms(guestRoot).join(id), 30, TimeUnit.SECONDS);
        Tasks.await(Tasks.whenAll(new OnlineGameRepository(hostRoot, id).initialize(),
                new OnlineGameRepository(guestRoot, id).initialize()), 30, TimeUnit.SECONDS);
        return id;
    }

    @Test public void completeGamesReachBothPlayersAndCannotRestart() throws Exception {
        DatabaseReference hostRoot = client();
        DatabaseReference guestRoot = client();
        int[][] sequences = {{0,3,1,4,2}, {0,3,1,4,8,5}, {0,1,2,4,3,5,7,6,8}};
        int[] results = {2,3,1};
        for (int round = 0; round < sequences.length; round++) {
            String id = create(hostRoot, guestRoot);
            OnlineGameRepository host = new OnlineGameRepository(hostRoot, id);
            OnlineGameRepository guest = new OnlineGameRepository(guestRoot, id);
            for (int index = 0; index < sequences[round].length; index++) {
                OnlineGameRepository actor = index % 2 == 0 ? host : guest;
                Tasks.await(actor.move(index % 2 == 0 ? 'X' : 'O', sequences[round][index], index),
                        30, TimeUnit.SECONDS);
                OnlineGame x = awaitGame(host, index + 1);
                OnlineGame o = awaitGame(guest, index + 1);
                assertArrayEquals(x.board(), o.board());
                assertEquals(x.result, o.result);
            }
            OnlineGame finished = awaitGame(host, sequences[round].length);
            assertEquals(results[round], finished.result);
            Tasks.await(guest.initialize(), 30, TimeUnit.SECONDS);
            assertEquals(finished.moves, awaitGame(guest, finished.moves).moves);
            assertThrows(Exception.class, () -> Tasks.await(host.move(finished.turn(), 8, finished.moves),
                    30, TimeUnit.SECONDS));
            Tasks.await(mHost.close(id), 30, TimeUnit.SECONDS);
        }
    }

    @Test public void boardSendsAccessibleMoveAndSurvivesRecreation() throws Exception {
        DatabaseReference hostRoot = Tasks.await(FirebaseConnection.getAuthenticatedRoot(),30,TimeUnit.SECONDS);
        DatabaseReference guestRoot = client();
        String id = create(hostRoot, guestRoot);
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences("online_room", 0);
        String previous = preferences.getString(uid, null);
        preferences.edit().putString(uid, id).commit();
        try (ActivityScenario<LobbyActivity> scenario = ActivityScenario.launch(LobbyActivity.class)) {
            awaitState(scenario, 0);
            scenario.onActivity(activity -> {
                BoardView board = activity.findViewById(R.id.onlineBoard);
                assertTrue(board.isEnabled());
                assertTrue(board.getAccessibilityNodeProvider().performAction(0,
                        AccessibilityNodeInfo.ACTION_CLICK, null));
            });
            OnlineGameRepository guest = new OnlineGameRepository(guestRoot, id);
            awaitGame(guest, 1);
            awaitState(scenario, 1);
            scenario.onActivity(activity -> assertFalse(activity.findViewById(R.id.onlineBoard).isEnabled()));
            Tasks.await(guest.move('O', 3, 1), 30, TimeUnit.SECONDS);
            awaitState(scenario, 2);
            scenario.recreate();
            awaitState(scenario, 2);
            scenario.onActivity(activity -> {
                LobbyViewModel model = new ViewModelProvider(activity).get(LobbyViewModel.class);
                OnlineGame game = model.state().getValue().game;
                assertEquals('X', game.board()[0]);
                assertEquals('O', game.board()[3]);
                assertTrue(activity.findViewById(R.id.onlineBoard).isEnabled());
                assertEquals(' ', model.consumeMoveSound());
            });
        } finally {
            if (previous == null) preferences.edit().remove(uid).commit();
            else preferences.edit().putString(uid, previous).commit();
        }
    }

    private OnlineGame awaitGame(OnlineGameRepository repository, int moves) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<OnlineGame> result = new AtomicReference<>();
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot data) {
                OnlineGame game = OnlineGameRepository.read(data);
                if (game != null && game.moves == moves) { result.set(game); latch.countDown(); }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { latch.countDown(); }
        };
        repository.reference().addValueEventListener(listener);
        try {
            assertTrue("No llegó la jugada al observador", latch.await(30, TimeUnit.SECONDS));
            assertNotNull(result.get());
            return result.get();
        } finally { repository.reference().removeEventListener(listener); }
    }

    private void awaitState(ActivityScenario<LobbyActivity> scenario, int moves) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Observer<LobbyViewModel.State> observer = state -> {
            if (state.game != null && state.game.moves == moves && state.connected && !state.busy) latch.countDown();
        };
        scenario.onActivity(activity -> new ViewModelProvider(activity).get(LobbyViewModel.class)
                .state().observeForever(observer));
        try { assertTrue("La pantalla no recibió el tablero", latch.await(30, TimeUnit.SECONDS)); }
        finally { scenario.onActivity(activity -> new ViewModelProvider(activity).get(LobbyViewModel.class)
                .state().removeObserver(observer)); }
    }

    @After public void closeTestRooms() throws Exception {
        try {
            if (mHost != null) for (String id : mRooms) Tasks.await(mHost.close(id),30,TimeUnit.SECONDS);
        } finally {
            for (FirebaseApp app : mApps) {
                FirebaseDatabase.getInstance(app).goOffline();
                FirebaseAuth.getInstance(app).signOut();
                app.delete();
            }
        }
    }
}
