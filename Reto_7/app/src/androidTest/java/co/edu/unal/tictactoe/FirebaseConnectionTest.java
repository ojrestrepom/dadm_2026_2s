package co.edu.unal.tictactoe;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class FirebaseConnectionTest {

    @Test
    public void firebaseInitializesFromAndroidConfiguration() {
        assertEquals("triquionline-reto7",
                FirebaseApp.getInstance().getOptions().getProjectId());
    }

    @Test
    public void authenticateAndConnectWhenExplicitlyRequested() throws Exception {
        // La prueba real es optativa: puede crear un usuario anónimo en Firebase.
        assumeTrue("true".equals(InstrumentationRegistry.getArguments()
                .getString("runFirebaseIntegration")));

        DatabaseReference root = Tasks.await(
                FirebaseConnection.getAuthenticatedRoot(), 30, TimeUnit.SECONDS);
        assertNotNull(Tasks.await(FirebaseConnection.signInAnonymously(),
                30, TimeUnit.SECONDS).getUid());

        DatabaseReference connected = root.child(".info/connected");
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<DatabaseError> error = new AtomicReference<>();
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    finished.countDown();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                error.set(databaseError);
                finished.countDown();
            }
        };
        connected.addValueEventListener(listener);
        try {
            assertTrue("No se confirmó la conexión con Realtime Database.",
                    finished.await(30, TimeUnit.SECONDS));
            if (error.get() != null) {
                fail(error.get().getMessage());
            }
        } finally {
            connected.removeEventListener(listener);
        }
    }
}
