package co.edu.unal.tictactoe;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Prueba remota optativa: crea tres sesiones de prueba y una sala que cierra al terminar. */
@RunWith(AndroidJUnit4.class)
public class RoomRepositoryTest {
    @Test public void realClientsCreateJoinConcurrentlyAndClose() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments()
                .getString("runRoomIntegration")));
        List<FirebaseApp> apps = new ArrayList<>();
        RoomRepository host = null;
        String id = null;
        try {
            host = client(apps);
            RoomRepository first = client(apps);
            RoomRepository second = client(apps);
            id = host.newRoomId();
            Tasks.await(host.create(id), 30, TimeUnit.SECONDS);
            DataSnapshot available = Tasks.await(first.availableRooms().get(), 30, TimeUnit.SECONDS);
            assertTrue(available.hasChild(id));

            Task<Void> joinFirst = first.join(id);
            Task<Void> joinSecond = second.join(id);
            Tasks.await(Tasks.whenAllComplete(joinFirst, joinSecond), 30, TimeUnit.SECONDS);
            assertEquals(1, (joinFirst.isSuccessful() ? 1 : 0) + (joinSecond.isSuccessful() ? 1 : 0));
            OnlineRoom full = RoomRepository.read(Tasks.await(host.room(id).get(), 30, TimeUnit.SECONDS));
            assertNotNull(full);
            assertEquals(OnlineRoom.READY, full.status);
            assertFalse(full.guest.isEmpty());
            assertFalse(Tasks.await(first.availableRooms().get(), 30, TimeUnit.SECONDS).hasChild(id));

            RoomRepository guest = joinFirst.isSuccessful() ? first : second;
            Tasks.await(guest.close(id), 30, TimeUnit.SECONDS);
            OnlineRoom closed = RoomRepository.read(Tasks.await(host.room(id).get(), 30, TimeUnit.SECONDS));
            assertNotNull(closed);
            assertEquals(OnlineRoom.CLOSED, closed.status);
        } finally {
            try {
                if (host != null && id != null) Tasks.await(host.close(id), 30, TimeUnit.SECONDS);
            } finally {
                for (FirebaseApp app : apps) {
                    FirebaseDatabase.getInstance(app).goOffline();
                    FirebaseAuth.getInstance(app).signOut();
                    app.delete();
                }
            }
        }
    }

    private RoomRepository client(List<FirebaseApp> apps) throws Exception {
        FirebaseApp app = FirebaseApp.initializeApp(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                FirebaseApp.getInstance().getOptions(), "room-test-" + UUID.randomUUID());
        apps.add(app);
        String uid = Tasks.await(FirebaseAuth.getInstance(app).signInAnonymously(),
                30, TimeUnit.SECONDS).getUser().getUid();
        return new RoomRepository(FirebaseDatabase.getInstance(app).getReference(), uid);
    }
}
