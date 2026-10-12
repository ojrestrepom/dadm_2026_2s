package co.edu.unal.tictactoe;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/** Usa los sonidos y la preferencia existentes sin afectar al juego local. */
final class OnlineMoveSounds {
    private SoundPool mPool;
    private int mX;
    private int mO;
    private boolean mXLoaded;
    private boolean mOLoaded;

    void start(Context context) {
        stop();
        mPool = new SoundPool.Builder().setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        mPool.setOnLoadCompleteListener((pool, id, status) -> {
            if (pool != mPool || status != 0) return;
            if (id == mX) mXLoaded = true;
            if (id == mO) mOLoaded = true;
        });
        mX = mPool.load(context, R.raw.human_move, 1);
        mO = mPool.load(context, R.raw.computer_move, 1);
    }

    void play(Context context, char player) {
        if (mPool == null || !context.getSharedPreferences("sound_preferences", 0)
                .getBoolean("sound_enabled", true)) return;
        if (player == 'X' && mXLoaded) mPool.play(mX, .7f, .7f, 1, 0, 1);
        if (player == 'O' && mOLoaded) mPool.play(mO, .7f, .7f, 1, 0, 1);
    }

    void stop() {
        if (mPool != null) { mPool.setOnLoadCompleteListener(null); mPool.release(); mPool = null; }
        mXLoaded = false;
        mOLoaded = false;
    }
}
