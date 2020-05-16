package edu.uoc.resolvers;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/*
    Esta clase representa la pantalla de juego.
 */
public class ActividadPrincipal extends AppCompatActivity implements Runnable {

    private PuzzleLayout pl;
    private int numCortes = 2;
    private int imagen;
    private long tInicio, tFin, tDelta;
    private double segTranscurridos;
    private static final int SECOND_ACTIVITY_REQUEST_CODE = 0;
    private static final int NIVELES = 5;
    HomeWatcher mHomeWatcher;
    private static final int READ_REQUEST_CODE = 42;
    public static final String Broadcast_PLAY_NEW_AUDIO = "edu.uoc.resolvers";
    private boolean isChecked = false;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private ArrayList<Integer> imagenesDisponibles = new ArrayList<>();
    private ArrayList<Integer> imagenesUsadas = new ArrayList<>();
    private Integer REQUEST_CAMERA = 1;
    File archivoFoto;
    Uri imagenUri = null;
    DatabaseReference ref;
    private Puntuacion puntuacion;
    private long maxId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.actividad_principal);

        // Vinculamos el servicio de música
        doBindService();
        Intent music = new Intent();
        music.setClass(this, ServicioMusica.class);
        startService(music);

        // Iniciamos el HomeWatcher
        mHomeWatcher = new HomeWatcher(this);
        mHomeWatcher.setOnHomePressedListener(new HomeWatcher.OnHomePressedListener() {
            @Override
            public void onHomePressed() {
                if (mServ != null) {
                    mServ.pauseMusic();
                }
            }

            @Override
            public void onHomeLongPressed() {
                if (mServ != null) {
                    mServ.pauseMusic();
                }
            }
        });
        mHomeWatcher.startWatch();

        // Solicitamos permisos para que la app pueda usar la cámara
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 2);
        }

        // Obtenemos las imágenes a usar en el juego de las almacenadas en el dispositivo del usuario
        if (checkPermissionREAD_EXTERNAL_STORAGE(this)) {
            ContentResolver cr = getApplicationContext().getContentResolver();
            String[] projection = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA};

            Cursor cursor = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                    Uri path = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                    imagenesDisponibles.add(Integer.parseInt(id));
                }
                cursor.close();
            }
            // Seleccionamos una de esas imágenes de manera aleatoria
            imagen = seleccionarImagenAleatoria(imagenesDisponibles);
        }

        pl = findViewById(R.id.tablero_juego);

        // Establecemos la imagen seleccionada como puzzle
        try {
            pl.establecerImagen(imagen, numCortes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Empezamos a contar el tiempo
        tInicio = System.currentTimeMillis();

        puntuacion = new Puntuacion();
        ref = FirebaseDatabase.getInstance().getReference().child("Records");
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists())
                    maxId = (dataSnapshot.getChildrenCount());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        // Cuando se completa el puzzle
        pl.setOnCompleteCallback(new PuzzleLayout.OnCompleteCallback() {
            @Override
            public void onComplete() {
                // Paramos el tiempo
                tFin = System.currentTimeMillis();
                tDelta = tFin - tInicio;
                segTranscurridos = tDelta / 1000.0;

                DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
                Date date = new Date();
                String strDate = dateFormat.format(date);

                // Si se produce un récord añadimos el evento al calendario
                agregarEventoCalendario(numCortes - 1, segTranscurridos);

                if (ActividadInicio.givenName != null) {
                    puntuacion.setNombre(ActividadInicio.givenName);
                } else {
                    puntuacion.setNombre("Anónimo");
                }

                puntuacion.setNivel(numCortes - 1);
                puntuacion.setTiempo(segTranscurridos);
                puntuacion.setFecha(strDate);
                ref.child(String.valueOf(maxId + 1)).setValue(puntuacion);

                // Mostramos mensaje al completar puzzle
                Toast.makeText(ActividadPrincipal.this, "¡Bravo! Tu tiempo " + String.format("%.2f", segTranscurridos).replace(".", ",") + "s", Toast.LENGTH_SHORT).show();

                // Esperamos 3 segundos para cargar el siguiente puzzle
                pl.postDelayed(ActividadPrincipal.this, 3000);
            }
        });
    }

    // Vinculamos el servicio de música
    private boolean mIsBound = false;
    private ServicioMusica mServ;
    private ServiceConnection Scon = new ServiceConnection() {

        public void onServiceConnected(ComponentName name, IBinder binder) {
            mServ = ((ServicioMusica.ServiceBinder) binder).getService();
        }

        public void onServiceDisconnected(ComponentName name) {
            mServ = null;
        }
    };

    // Vinculamos el servicio
    void doBindService() {
        bindService(new Intent(this, ServicioMusica.class), Scon, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    // Desvinculamos el servicio
    void doUnbindService() {
        if (mIsBound) {
            unbindService(Scon);
            mIsBound = false;
        }
    }

    // Este método reanuda la música
    @Override
    protected void onResume() {
        super.onResume();

        if (mServ != null) {
            mServ.resumeMusic();
        }
    }

    // Este método pone la música en pausa
    @Override
    protected void onPause() {
        super.onPause();

        // Detectamos la pausa de la pantalla
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = false;
        if (pm != null) {
            isScreenOn = pm.isScreenOn();
        }

        if (!isScreenOn) {
            if (mServ != null) {
                mServ.pauseMusic();
            }
        }
    }

    // Este método desvincula el servicio de música cuando no lo necesitamos
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Desvinculamos el servicio de música
        doUnbindService();
        Intent music = new Intent();
        music.setClass(this, ServicioMusica.class);
        stopService(music);
    }

    // Este método comprueba si la aplicación tiene permisos
    // para acceder al almacenamiento externo del dispositivo.
    public boolean checkPermissionREAD_EXTERNAL_STORAGE(final Context context) {
        int currentAPIVersion = Build.VERSION.SDK_INT;
        if (currentAPIVersion >= android.os.Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    showDialog("External storage", context, Manifest.permission.READ_EXTERNAL_STORAGE);
                } else {
                    ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                }
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    // Este método muestra un diálogo indicando
    // que se necesita dar permiso a la aplicación.
    public void showDialog(final String msg, final Context context, final String permission) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle("Permission necessary");
        alertBuilder.setMessage(msg + " permission is necessary");
        alertBuilder.setPositiveButton(android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions((Activity) context,
                                new String[]{permission},
                                MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                    }
                });
        AlertDialog alert = alertBuilder.create();
        alert.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(this, "GET_ACCOUNTS Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // Esté método selecciona aleatoriamente una imagen de entre
    // las que el usuario tiene almacenadas en el dispositivo y
    // comprueba que no se haya seleccionado previamente en esa partida.
    private int seleccionarImagenAleatoria(ArrayList<Integer> imagenes) {
        Random rand = new Random();
        int imagen = imagenes.get(rand.nextInt(imagenes.size()));

        while (imagenesUsadas.contains(imagen)) {
            imagen = imagenes.get(rand.nextInt(imagenes.size()));
        }

        imagenesUsadas.add(imagen);
        return imagen;
    }

    // Este método agrega un evento al calendario si se ha producido un récord
    // y en ese caso además envía una notificación.
    private void agregarEventoCalendario(int nivel, double tiempo) {

        if (recurperarPuntuacionesCalendario(nivel, tiempo)) {
            long fecha_record = System.currentTimeMillis();
            ContentResolver cr = getContentResolver();
            ContentValues values = new ContentValues();

            values.put(CalendarContract.Events.DTSTART, fecha_record);
            values.put(CalendarContract.Events.DTEND, fecha_record);
            values.put(CalendarContract.Events.TITLE, "TR - ¡Nuevo récord N" + nivel + "!");
            values.put(CalendarContract.Events.DESCRIPTION, String.format("%.2f", tiempo).replace(".", ","));
            values.put(CalendarContract.Events.CALENDAR_ID, 3);
            values.put(CalendarContract.Events.EVENT_TIMEZONE, "Confinado");

            // Comprobamos si tenemos permisos de acceso al calendario
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);

            // Envía notificación
            crearCanalNotificacion();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ActividadPrincipal.this, "CHANNEL_NEW_RECORD")
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentTitle("The Resolvers")
                    .setContentText("¡Enhorabuena, has batido un nuevo récord! " + String.format("%.2f", tiempo).replace(".", ",") + "s");

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ActividadPrincipal.this);

            notificationManager.notify(1, builder.build());
        }

    }

    // Este método comprueba si hay registros de la aplicación en el calendario
    private boolean recurperarPuntuacionesCalendario(int nivel, double tiempo) {
        ContentResolver contentResolver = getContentResolver();
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();

        Calendar beginTime = Calendar.getInstance();
        beginTime.set(2000, Calendar.JANUARY, 1, 0, 0);
        long startMills = beginTime.getTimeInMillis();
        long endMills = System.currentTimeMillis();

        ContentUris.appendId(builder, startMills);
        ContentUris.appendId(builder, endMills);
        String[] args = new String[]{"3"};

        Cursor eventCursor = contentResolver.query(builder.build(), new String[]{CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.DESCRIPTION},
                CalendarContract.Instances.CALENDAR_ID + " = ?", args, null);

        boolean isRecord = false;
        boolean hayRegistros = false;

        while (eventCursor.moveToNext()) {
            final String title = eventCursor.getString(0);
            final String description = eventCursor.getString(3);

            if (title.length() == 22 && title.substring(title.length() - 2, title.length() - 1).equals(Integer.toString(nivel))) {
                hayRegistros = true;
                if (tiempo < Double.parseDouble(description.replace(",", "."))) {
                    isRecord = true;
                } else {
                    isRecord = false;
                }
            }
        }

        if (hayRegistros) {
            return isRecord;
        } else {
            return !isRecord;
        }
    }

    // Este método crea el canal que permite enviar las notificaciones de los récords.
    private void crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("CHANNEL_NEW_RECORD", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Este método crea el menú selección de la barra de acción
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_principal, menu);
        return true;
    }

    // Este método dispara la acción correspondiente al elegir cada opción del menú.
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ayuda:
                // Se abre la WebView con la ayuda
                Intent ayuda = new Intent(this, ActividadAyuda.class);
                startActivity(ayuda);
                return true;
            case R.id.camara:
                // Se abre la funcionalidad de la cámara
                mServ.pauseMusic(); // Pausamos la música mientras se hace la foto
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "Foto puzzle");
                values.put(MediaStore.Images.Media.DESCRIPTION, "The Resolvers");

                Intent intentoFoto = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                // Nos aseguramos que se ha abierto la actividad de la cámara
                if (intentoFoto.resolveActivity(getPackageManager()) != null) {
                    // Creamos el archivo donde irá la imagen
                    try {
                        archivoFoto = crearArchivoImagen();
                    } catch (IOException ex) {
                        // Ha ocurrido un error al crear el archivo
                    }

                    if (archivoFoto != null) {
                        imagenUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        intentoFoto.putExtra(MediaStore.EXTRA_OUTPUT, imagenUri);
                        startActivityForResult(intentoFoto, REQUEST_CAMERA);
                    }
                }
                return true;
            case R.id.selector_musica:
                // Se abre el selector de música
                buscarPistaAudio();
                return true;
            case R.id.checkable_menu:
                isChecked = !item.isChecked();
                item.setChecked(isChecked);
                if (isChecked) {
                    AudioManager amanager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
                } else {
                    AudioManager amanager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Este método crea el archivo en el que se guarda la foto que tomamamos con la cámara
    private File crearArchivoImagen() throws IOException {
        // Creamos un nombre para el archivo
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String nombreArchivoImagen = "JPEG_" + timeStamp + "_";
        File directorio = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        // Creamos el archivo
        File imagen = File.createTempFile(nombreArchivoImagen,".jpg", directorio);

        return imagen;
    }

    // Este método permite acceder al selector de archivos para que podamos elegir un tema de música.
    public void buscarPistaAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        // Filtramos para que solo muestre los archivos que se pueden abrir.
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Añadimos el checkbox para encender y apagar la música de fondo.
        MenuItem checkable = menu.findItem(R.id.checkable_menu);
        checkable.setChecked(isChecked);
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Establecemos la nueva música de fondo
            Uri uri;

            if (data != null) {
                uri = data.getData();
                ServicioMusica.audioUri = uri;
                Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
                sendBroadcast(broadcastIntent);
            }

        } else if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK) {
            // Sustituimos la imagen del puzzle por la foto hecha con la cámara
            Bitmap foto = null;

            try {
                foto = MediaStore.Images.Media.getBitmap(getContentResolver(), imagenUri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Obtenemos el id de la foto tomada con la cámara
            imagen = obtenerIdUltimaImagen();
            imagenesUsadas.add(imagen);
            try {
                pl.establecerImagen(imagen, numCortes);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mServ.resumeMusic();
        }
    }

    // Este método permite obtener el id de la última imagen tomada con la cámara
    private int obtenerIdUltimaImagen() {
        final String[] imageColumns = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA};
        final String imageOrderBy = MediaStore.Images.Media._ID + " DESC";
        Cursor imageCursor = getApplicationContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageColumns, null, null, imageOrderBy);

        if (imageCursor.moveToFirst()) {
            int id = imageCursor.getInt(imageCursor.getColumnIndex(MediaStore.Images.Media._ID));
            imageCursor.close();
            return id;
        } else {
            return 0;
        }

    }

    @Override
    public void run() {
        numCortes++;
        imagen = seleccionarImagenAleatoria(imagenesDisponibles);

        // Si llegamos al último puzzle muestra el dialogo del fin del juego
        // Si no carga el siguiente puzzle
        if (numCortes > NIVELES + 1) {
            showDialog();
        } else {
            try {
                pl.establecerImagen(imagen, numCortes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Este método muestra el diálogo de finalización del juego.
    private void showDialog() {
        new AlertDialog.Builder(ActividadPrincipal.this)
                .setTitle(R.string.exito)
                .setMessage(R.string.reiniciar)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                numCortes = 2;
                                imagen = seleccionarImagenAleatoria(imagenesDisponibles);
                                try {
                                    pl.establecerImagen(imagen, numCortes);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                tInicio = System.currentTimeMillis();
                            }
                        }).setNegativeButton(R.string.salir,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent i = new Intent(ActividadPrincipal.this, ActividadInicio.class);
                        startActivityForResult(i, SECOND_ACTIVITY_REQUEST_CODE);
                        finish();
                    }
                }).show();
    }
}
