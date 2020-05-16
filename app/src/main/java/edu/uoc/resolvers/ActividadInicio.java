package edu.uoc.resolvers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

/*
    Esta clase representa la pantalla de bienvenida.
 */
public class ActividadInicio extends AppCompatActivity {

    HomeWatcher mHomeWatcher;
    private int CALENDAR_PERMISSION_CODE = 1;
    private SignInButton signInButton;
    private GoogleSignInClient mGoogleSignInClient;
    private String TAG = "Actividad inicio";
    private FirebaseAuth mAuth;
    private Button signOutButton;
    private int RC_SIGN_IN = 2;
    public static String givenName;
    private DatabaseReference ref;
    private TextView puntuaciones;
    private ArrayList<Puntuacion> puntos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.actividad_inicio);
        getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.colorPrimary));

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

        // Solicitamos los permisos de escritura y lectura en el Calendario.
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION_CODE);

        // Creamos el botón de inicio
        Button botonInicio = findViewById(R.id.botonInicio);

            botonInicio.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        abrirActividadPrincipal();
                    } catch (Exception e) {
                        Log.e("Inicio", e.getMessage());
                    }

                }
            });


        // Creamos las puntuaciones
        puntuaciones = findViewById(R.id.puntuaciones);

        // Comprobamos si se han concedido los permisos de lectura y escritura en el Calendario.
        if (ContextCompat.checkSelfPermission(ActividadInicio.this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(ActividadInicio.this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            obtenerPuntuaciones(puntuaciones); //Con los permisos concedidos, se muestran las puntuaciones en el TextView.
        } else {
            //Sin la concesión del permiso, se muestra el siguiente mensaje en el TextView.
            puntuaciones.append("Debe dar permisos de lectura y escritura al Calendario para poder visualizar y registrar nuevas puntuaciones.");
        }

        signInButton = findViewById(R.id.sign_in_button);
        mAuth = FirebaseAuth.getInstance();
        signOutButton = findViewById(R.id.sign_out_button);

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        signInButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                signIn();
            }
        });

        signOutButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                mGoogleSignInClient.signOut();
                signOutButton.setVisibility(View.INVISIBLE);
                Toast.makeText(ActividadInicio.this, "Se ha cerrado sesión", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Toast.makeText(ActividadInicio.this, "Autenticacion correcta", Toast.LENGTH_SHORT).show();
            firebaseGoogleAuth(account);
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            Toast.makeText(ActividadInicio.this, "Autenticacion fallida", Toast.LENGTH_SHORT).show();
            firebaseGoogleAuth(null);
        }
    }

    private void firebaseGoogleAuth (GoogleSignInAccount account) {

        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(ActividadInicio.this, "Éxito", Toast.LENGTH_SHORT).show();
                            FirebaseUser user = mAuth.getCurrentUser();
                            updateUI(user);
                        } else {
                            Toast.makeText(ActividadInicio.this, "Error", Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }
                    }
                });
    }

    private void updateUI(FirebaseUser user) {
        signOutButton.setVisibility(View.VISIBLE);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        if (account != null) {
            givenName = account.getGivenName();
            Toast.makeText(ActividadInicio.this, givenName, Toast.LENGTH_SHORT).show();
        }
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

    // Vincular servicio
    void doBindService() {
        bindService(new Intent(this, ServicioMusica.class), Scon, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    // Desvincular servicio
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

    // Este método permite obtener la máxima puntuaciones
    // para cada nivel de las persistidas en la base de datos de Firebase.
    private void obtenerPuntuaciones(final TextView puntuaciones) {
        puntuaciones.append("");

        ref = FirebaseDatabase.getInstance().getReference();
        ref.child("Records").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                puntos.clear();
                int i;
                double minTime = Double.MAX_VALUE;
                for (i = 0; i < 5; i++) {
                    Iterator<DataSnapshot> items = dataSnapshot.getChildren().iterator();
                    String minName = null;
                    String minDate = null;
                    minTime = Double.MAX_VALUE;
                    while (items.hasNext()) {
                        DataSnapshot item = items.next();
                        String nombre = item.child("nombre").getValue().toString();
                        String fecha = item.child("fecha").getValue().toString();
                        double tiempo = Double.parseDouble(item.child("tiempo").getValue().toString());
                        int nivel = Integer.parseInt(item.child("nivel").getValue().toString());
                        if (nivel == i + 1) {
                            if (tiempo < minTime) {
                                minTime = tiempo;
                                minName = nombre;
                                minDate = fecha;
                            }
                        }
                    }
                    Puntuacion p = new Puntuacion(minName, i + 1, minDate, minTime);
                    if (minName != null) {
                        puntos.add(p);
                    }
                }
                for (Puntuacion p : puntos) {
                    puntuaciones.append(p.getNivel() + "     " + String.format("%-11s", p.getNombre()) + String.format("%10.2f", p.getTiempo()).replace(".", ",") + "\n");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    // Este método hace que al hacer clic en el botón de inicio
    // se nos abra la pantalla principal del juego.
    private void abrirActividadPrincipal() {
        Intent intent = new Intent(this, ActividadPrincipal.class);
        startActivity(intent);
    }

}
