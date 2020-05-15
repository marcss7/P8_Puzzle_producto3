package edu.uoc.resolvers;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;

/*
    Esta clase representa el servicio que se va a encargar de
    gestionar la reproducción de la música de fondo de la aplicación.
 */
public class ServicioMusica extends Service implements MediaPlayer.OnErrorListener {

    private final IBinder mBinder = new ServiceBinder();
    MediaPlayer mPlayer;
    private int length = 0;
    public static Uri audioUri;

    public ServicioMusica() {
    }

    public class ServiceBinder extends Binder {
        ServicioMusica getService() {
            return ServicioMusica.this;
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (audioUri != null) {
            mPlayer = MediaPlayer.create(this, audioUri);
        } else {
            mPlayer = MediaPlayer.create(this, R.raw.solve_the_puzzle);
        }

        mPlayer.setOnErrorListener(this);

        if (mPlayer != null) {
            mPlayer.setLooping(true);
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }

        mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

            public boolean onError(MediaPlayer mp, int what, int
                    extra) {

                onError(mPlayer, what, extra);
                return true;
            }
        });
        register_playNewAudio();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mPlayer != null) {
            mPlayer.start();
        }
        return START_NOT_STICKY;
    }

    // Este método pausa la música
    public void pauseMusic() {
        if (mPlayer != null) {
            if (mPlayer.isPlaying()) {
                mPlayer.pause();
                length = mPlayer.getCurrentPosition();
            }
        }
    }

    // Este método reinicia la reproducción de la música
    public void resumeMusic() {
        if (mPlayer != null) {
            if (!mPlayer.isPlaying()) {
                mPlayer.seekTo(length);
                mPlayer.start();
            }
        }
    }

    // Este método inicia la reproducción de la música
    public void startMusic() {
        if (audioUri != null) {
            mPlayer = MediaPlayer.create(this, audioUri);
        } else {
            mPlayer = MediaPlayer.create(this, R.raw.solve_the_puzzle);
        }

        mPlayer.setOnErrorListener(this);

        if (mPlayer != null) {
            mPlayer.setLooping(true);
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.start();
        }
    }

    // Este método detiene la música
    public void stopMusic() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            try {
                mPlayer.stop();
                mPlayer.release();
            } finally {
                mPlayer = null;
            }
        }
        unregisterReceiver(playNewAudio);
    }

    // Esté metodo detecta si se ha producido un error al reproducir la música de fondo.
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(this, "Error en reproductor", Toast.LENGTH_SHORT).show();
        if (mPlayer != null) {
            try {
                mPlayer.stop();
                mPlayer.release();
            } finally {
                mPlayer = null;
            }
        }
        return false;
    }

    // Este método se encarga de gestiionar la reproducción
    // del nuevo audio seleccionado por el usuario.
    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Resetea el mediaplayer para reproducir el nuevo audio
            stopMusic();
            startMusic();
        }
    };

    private void register_playNewAudio() {
        IntentFilter filter = new IntentFilter(ActividadPrincipal.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, filter);
    }
}