package edu.uoc.resolvers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
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
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

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
    private Map<Integer, Puntuacion> puntos = new HashMap<>();

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

        // Creamos la vista de las puntuaciones
        puntuaciones = findViewById(R.id.puntuaciones);

        // Comprobamos si se han concedido los permisos de lectura y escritura en el Calendario.
        if (ContextCompat.checkSelfPermission(ActividadInicio.this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(ActividadInicio.this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            obtenerPuntuaciones(puntuaciones); //Con los permisos concedidos, se muestran las puntuaciones en el TextView.
        } else {
            //Sin la concesión del permiso, se muestra el siguiente mensaje en el TextView.
            String msjPermisos = getString(R.string.msjPermisos);
            Toast.makeText(ActividadInicio.this, msjPermisos, Toast.LENGTH_SHORT).show();
        }

        // Creamos los botones de inicio y fin de sesión
        signInButton = findViewById(R.id.sign_in_button);
        signOutButton = findViewById(R.id.sign_out_button);

        // Configuramos el inicio de sesión en Google
        mAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }
        });

        signOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mGoogleSignInClient.signOut();
                signOutButton.setVisibility(View.INVISIBLE);
                String msjCierreSesion = getString(R.string.msjCierreSesion);
                Toast.makeText(ActividadInicio.this, msjCierreSesion, Toast.LENGTH_SHORT).show();
            }
        });

    }

    // Esté método crea el intento de inicio de sesión
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

    // Este método controla el resultado del inicio de sesión e informa al usuario
    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            String authOk = getString(R.string.authOk);
            Toast.makeText(ActividadInicio.this, authOk, Toast.LENGTH_SHORT).show();
            firebaseGoogleAuth(account);
        } catch (ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            String authKo = getString(R.string.authKo);
            Toast.makeText(ActividadInicio.this, authKo, Toast.LENGTH_SHORT).show();
            firebaseGoogleAuth(null);
        }
    }

    // Este método gestiona las credenciales de acceso del usuario a Firebase
    private void firebaseGoogleAuth(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            updateUI(user);
                        } else {
                            updateUI(null);
                        }
                    }
                });
    }

    // Este método muestra un mensaje de bienvenida al usurio cuando inicia sesión
    private void updateUI(FirebaseUser user) {
        signOutButton.setVisibility(View.VISIBLE);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        String msjBienvenida = getString(R.string.msjBienvenida);
        if (account != null) {
            givenName = account.getGivenName();
            Toast.makeText(ActividadInicio.this, msjBienvenida + " " + givenName + "!", Toast.LENGTH_SHORT).show();
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
        puntos.clear();

        ref = FirebaseDatabase.getInstance().getReference();
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (int i = 1; i <= 5; i++) {
                    Query query = ref.child("Records/" + i).orderByChild("tiempo").limitToFirst(1);

                    int finalI = i;
                    query.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists()) {
                                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                    Puntuacion p = snapshot.getValue(Puntuacion.class);
                                    String myString = NumberFormat.getInstance().format(p.getTiempo());
                                    String nombre;
                                    String paddedString = StringUtils.leftPad(myString, 10, " ");
                                    if (p.getNombre().equals(" ")) {
                                        nombre = getString(R.string.anonimo);
                                    } else {
                                        nombre = p.getNombre();
                                    }
                                    puntuaciones.append(dataSnapshot.getKey() + "     " + String.format("%-11s", nombre) + paddedString + "\n");
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
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
