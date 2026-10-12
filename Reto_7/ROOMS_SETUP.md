# Reto 7: salas

La aplicación abre `LobbyActivity`, con acceso al juego local existente. El creador
ocupa X y el invitado ocupa O. Las salas completas dejan de aparecer en la lista.
Cuando están ambos participantes se muestra el tablero en la misma sala. X comienza.
La partida se guarda en `games/{roomId}` y solo sus participantes pueden leerla.

## Activar en Firebase

Publicar el contenido de `database.rules.json` en Realtime Database → Reglas del
proyecto `triquionline-reto7`. El archivo local no publica reglas por sí mismo.
También es posible desplegarlas con Firebase CLI, tras iniciar sesión:

```powershell
firebase deploy --only database --project triquionline-reto7
```

Las reglas de esta etapa permiten:

- Consultar hasta 50 salas `waiting`, con autenticación.
- Leer una sala por su identificador con autenticación. Su contenido es únicamente
  metadata de sala (UID de participantes, estado y fecha); no contiene datos personales.
- Crear una sala vacía como anfitrión usando la fecha del servidor.
- Ocupar el único puesto libre con el UID propio; el anfitrión no puede ocupar ambos.
- Cerrar la sala siendo uno de sus participantes. No se permite cambiar las identidades,
  reabrir, borrar ni añadir campos ajenos al esquema de sala.

Las reglas de `games` permiten crear un tablero vacío solo en una sala completa.
Cada transacción debe ocupar exactamente una casilla vacía con el símbolo del
jugador que tiene el turno, incrementar el número de jugadas y comunicar el
resultado correcto. El servidor verifica victorias y empate, impide sobrescribir
el tablero y rechaza jugadas después de terminar o cerrar la sala.

La pantalla utiliza las imágenes y la detección de ganador del juego local, con
etiquetas de accesibilidad para creador e invitado. No ejecuta IA ni modifica las
estadísticas locales. Los sonidos respetan la preferencia existente y se consumen
una vez por jugada nueva; cargar o recrear la pantalla no reproduce el historial.
Para una nueva partida se cierra la sala y se crea otra, sin reiniciar la del oponente.

`RoomRepository.join()` realiza una transacción: ante dos invitados simultáneos,
solo uno puede confirmar la unión. No se muestra el éxito antes de la confirmación.

El estado y las operaciones se conservan en un ViewModel durante la rotación. El
identificador de sala se guarda por usuario en preferencias separadas de las locales.
Los observadores se retiran al detener la pantalla y se conectan al volver.
La pérdida de conexión o el paso a segundo plano no cierra automáticamente la sala.
Usar «Cerrar sala» para retirarla de la lista. La limpieza automática por abandono
y la recuperación avanzada pertenecen a la etapa posterior de robustez.

## Pruebas de reglas, sin acceso a producción

Requieren Node.js y Java (puede utilizarse el JBR de Android Studio):

```powershell
cd firebase-tests
npm ci
npm test
```

El proyecto `demo-triqui-reto7` solo existe en el emulador local. Las pruebas cubren
autenticación, consultas, datos inválidos, unión concurrente, tercer jugador,
inmutabilidad de participantes y cierre.

## Android y dispositivo

```powershell
./gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

La prueba remota de salas se omite por defecto. Tras publicar las reglas, puede
ejecutarse explícitamente en un dispositivo con Internet:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=co.edu.unal.tictactoe.RoomRepositoryTest" "-Pandroid.testInstrumentationRunnerArguments.runRoomIntegration=true"
```

Crea tres usuarios anónimos de prueba independientes de la sesión del jugador,
crea una sala, comprueba dos uniones simultáneas y cierra la sala al terminar.
El registro cerrado y los usuarios de prueba quedan en Firebase; no se modifican
otras salas ni estadísticas locales.

Comprobación manual con dos dispositivos: crear en uno, unirse desde el otro,
comprobar X/O, girar ambas pantallas y cerrar desde cualquiera. Un tercer dispositivo
no debe ver la sala completa. «Jugar contra Android» conserva el juego anterior.

## Pruebas de tablero en línea

Las pruebas del emulador también verifican turnos, casillas ocupadas, adulteración
de varias casillas, victorias X/O, empate, jugadas concurrentes y partidas terminadas.

La siguiente prueba Android es optativa y requiere publicar las reglas actualizadas:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=co.edu.unal.tictactoe.OnlineGameIntegrationTest" "-Pandroid.testInstrumentationRunnerArguments.runOnlineGameIntegration=true" "-Pandroid.testInstrumentationRunnerArguments.timeout_msec=120000"
```

Crea sesiones anónimas de prueba, completa partidas con los tres resultados posibles
y comprueba que los observadores de ambos clientes reciben el mismo tablero. También
envía una jugada desde el tablero accesible y recrea la pantalla durante una partida.
Las salas de prueba se cierran al terminar; sus registros, partidas y usuarios de
prueba permanecen en Firebase. La selección de sala del usuario se restaura.

## Archivos de la etapa de tablero

Creados:

- `app/src/main/java/co/edu/unal/tictactoe/OnlineGame.java`
- `app/src/main/java/co/edu/unal/tictactoe/OnlineGameRepository.java`
- `app/src/main/java/co/edu/unal/tictactoe/OnlineMoveSounds.java`
- `app/src/test/java/co/edu/unal/tictactoe/OnlineGameTest.java`
- `app/src/androidTest/java/co/edu/unal/tictactoe/OnlineGameIntegrationTest.java`

Modificados:

- `app/src/main/java/co/edu/unal/tictactoe/BoardView.java`
- `app/src/main/java/co/edu/unal/tictactoe/LobbyActivity.java`
- `app/src/main/java/co/edu/unal/tictactoe/LobbyViewModel.java`
- `app/src/main/res/layout/activity_lobby.xml`
- `app/src/main/res/values/strings_online.xml`
- `database.rules.json`
- `firebase-tests/rooms.test.mjs`
- `ROOMS_SETUP.md`

Verificación de esta etapa: compilación y Android Lint sin errores, 14 pruebas
unitarias, 12 pruebas de reglas en el emulador y 18 pruebas instrumentadas aprobadas
en el dispositivo físico, sin omisiones. La sincronización se probó con sesiones
Firebase independientes desde ese dispositivo; queda la comprobación manual con
dos celulares físicos. No se modificaron `MainActivity.java`, `TicTacToeGame.java`,
los diseños locales ni sus estadísticas.
