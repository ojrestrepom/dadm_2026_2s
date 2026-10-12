package co.edu.unal.tictactoe;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/** Preparación de Firebase compartida por las futuras pantallas del modo en línea. */
public final class FirebaseConnection {

    private static Task<FirebaseUser> sSignInTask;

    private FirebaseConnection() {
    }

    /** Reutiliza la sesión persistida y evita inicios simultáneos desde varias pantallas. */
    public static synchronized Task<FirebaseUser> signInAnonymously() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            return Tasks.forResult(currentUser);
        }
        if (sSignInTask == null || sSignInTask.isComplete()) {
            sSignInTask = auth.signInAnonymously().onSuccessTask(result -> {
                FirebaseUser user = result.getUser();
                if (user == null) {
                    return Tasks.forException(new IllegalStateException(
                            "Firebase no devolvió una sesión de usuario."));
                }
                return Tasks.forResult(user);
            });
        }
        return sSignInTask;
    }

    /**
     * Autentica y obtiene la referencia configurada, sin leer ni escribir partidas.
     * El éxito no demuestra acceso a los datos: cada operación debe comprobar su resultado
     * y las reglas de Realtime Database deben autorizarla.
     */
    public static Task<DatabaseReference> getAuthenticatedRoot() {
        String databaseUrl = FirebaseApp.getInstance().getOptions().getDatabaseUrl();
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            return Tasks.forException(new IllegalStateException(
                    "Falta la URL de Realtime Database. Descarga google-services.json "
                            + "después de crear la base de datos y vuelve a compilar."));
        }
        return signInAnonymously().onSuccessTask(user -> Tasks.forResult(
                FirebaseDatabase.getInstance(databaseUrl).getReference()));
    }
}
