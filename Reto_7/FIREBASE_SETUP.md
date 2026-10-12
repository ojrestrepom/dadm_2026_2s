# Firebase: preparación del Reto 7

La configuración Android utiliza Google Services y Firebase BoM, con los SDK de
Authentication y Realtime Database. Firebase se inicializa automáticamente mediante
su proveedor Android. No se necesita una clase Application personalizada.

El juego local no solicita autenticación ni depende de una conexión a Internet.
La pantalla de salas usa `FirebaseConnection.getAuthenticatedRoot()`:
devuelve una tarea asíncrona, reutiliza la sesión existente y propaga los errores.
Obtener una referencia no confirma conectividad ni permisos de lectura/escritura.

## Configuración de Firebase Console

1. En el proyecto `triquionline-reto7`, habilitar Authentication → Anónimo.
2. Conservar reglas restrictivas en Realtime Database. No es necesario abrir
   lecturas o escrituras públicas para verificar la conexión.
3. El archivo `app/google-services.json` actualizado por el propietario ya incluye
   `project_info.firebase_url` para la aplicación `co.edu.unal.tictactoe`:
   `https://triquionline-reto7-default-rtdb.firebaseio.com`.
   El texto `:null` mostrado en la consola no forma parte de esta URL.
4. Sincronizar y recompilar. La configuración se incorpora al APK durante la compilación.

## Verificaciones

Con Java de Android Studio disponible para Gradle:

```powershell
./gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

Con un emulador o dispositivo conectado, la siguiente prueba verifica que Firebase
se inicializa con el proyecto correcto. No crea usuarios ni escribe datos:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=co.edu.unal.tictactoe.FirebaseConnectionTest"
```

Para verificar además la autenticación real y la conexión a Realtime Database,
habilitar explícitamente la prueba de integración (requiere Internet y puede crear
un usuario anónimo en el proyecto configurado):

```powershell
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=co.edu.unal.tictactoe.FirebaseConnectionTest" "-Pandroid.testInstrumentationRunnerArguments.runFirebaseIntegration=true"
```

Esta prueba observa `.info/connected`, retira su observador al terminar y no escribe
datos de juego. No valida reglas de salas; sus pruebas se explican en `ROOMS_SETUP.md`.
Las pruebas ordinarias omiten esta comprobación remota salvo que se pase el argumento.

Referencias: https://firebase.google.com/docs/android/setup y
https://firebase.google.com/docs/auth/android/anonymous-auth
