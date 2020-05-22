package edu.uoc.resolvers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.customview.widget.ViewDragHelper;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;

/*
    Esta clase nos permite especificar como se posicionan unas piezas
    con respecto a otras y en relación al elemento padre que las contiene.
 */
public class PuzzleLayout extends RelativeLayout {

    private ViewDragHelper vdh; // Este objeto va a manejar los movimientos de las piezas
    private PuzzleHelper ph;
    private int idImagen;
    private int numCortes;
    private int alturaImagen;
    private int anchuraImagen;
    private int anchuraPieza;
    private int alturaPieza;
    private OnCompleteCallback occ;
    StorageReference storageReference;
    StorageReference imageRef;
    Bitmap my_image;

    public PuzzleLayout(Context context) {
        super(context);
        init();
    }

    public PuzzleLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PuzzleLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {

        // Declaramos las constantes que van a almacenar los efectos sonoros.
        final SoundPool soundPool;
        final int deslizar;
        final int exito;

        // Usamos SoundPool para reproducir los efectos de sonoros.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(audioAttributes)
                    .build();
        } else {
            soundPool = new SoundPool(6, AudioManager.STREAM_MUSIC, 0);
        }

        deslizar = soundPool.load(this.getContext(), R.raw.deslizar, 1);
        exito = soundPool.load(this.getContext(), R.raw.exito, 1);

        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                alturaImagen = getHeight();
                anchuraImagen = getWidth();
                getViewTreeObserver().removeOnPreDrawListener(this);
                if (idImagen != 0 && numCortes != 0) {
                    try {
                        crearPiezas();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }
        });

        ph = new PuzzleHelper();

        vdh = ViewDragHelper.create(this, 1.0f, new ViewDragHelper.Callback() {

            // Este método permite capturar la pieza que queremos mover
            @Override
            public boolean tryCaptureView(View child, int pointerId) {
                int indice = indexOfChild(child);
                return ph.obtenerPosicionDesplazamiento(indice) != PuzzleHelper.POSICION_ACTUAL;
            }

            // Este método controla los movimientos horizontales de las piezas
            @Override
            public int clampViewPositionHorizontal(View child, int izquierda, int dx) {

                int indice = indexOfChild(child);
                int posicion = ph.obtenerPieza(indice).posicion;
                int izquierdaPieza = (posicion % numCortes) * anchuraPieza;
                int bordeIzquierdo = izquierdaPieza - anchuraPieza;
                int bordeDerecho = izquierdaPieza + anchuraPieza;
                int direction = ph.obtenerPosicionDesplazamiento(indice);

                switch (direction) {
                    case PuzzleHelper.IZQUIERDA:
                        if (izquierda <= bordeIzquierdo)
                            return bordeIzquierdo;
                        else if (izquierda >= izquierdaPieza)
                            return izquierdaPieza;
                        else
                            return izquierda;

                    case PuzzleHelper.DERECHA:
                        if (izquierda >= bordeDerecho)
                            return bordeDerecho;
                        else if (izquierda <= izquierdaPieza)
                            return izquierdaPieza;
                        else
                            return izquierda;
                    default:
                        return izquierdaPieza;
                }
            }

            // Este método controla los movimientos verticales de las piezas
            @Override
            public int clampViewPositionVertical(View child, int arriba, int dy) {
                int indice = indexOfChild(child);
                Pieza pieza = ph.obtenerPieza(indice);
                int posicion = pieza.posicion;

                int arribaPieza = (posicion / numCortes) * alturaPieza;
                int bordeSuperior = arribaPieza - alturaPieza;
                int bordeInferior = arribaPieza + alturaPieza;
                int direccion = ph.obtenerPosicionDesplazamiento(indice);

                switch (direccion) {
                    case PuzzleHelper.ARRIBA:
                        if (arriba <= bordeSuperior)
                            return bordeSuperior;
                        else if (arriba >= arribaPieza)
                            return arribaPieza;
                        else
                            return arriba;
                    case PuzzleHelper.ABAJO:
                        if (arriba >= bordeInferior)
                            return bordeInferior;
                        else if (arriba <= arribaPieza)
                            return arribaPieza;
                        else
                            return arriba;
                    default:
                        return arribaPieza;
                }
            }

            // Este método controla lo que ocurre cuando soltamos la pieza que queremos mover
            @Override
            public void onViewReleased(View releasedChild, float xvel, float yvel) {
                int indice = indexOfChild(releasedChild);
                boolean estaCompleto = ph.intercambiarPosicionConPiezaVacia(indice);
                Pieza pieza = ph.obtenerPieza(indice);
                vdh.settleCapturedViewAt(pieza.phorizontal * anchuraPieza, pieza.pvertical * alturaPieza);
                View piezaVacia = getChildAt(0);
                ViewGroup.LayoutParams lp = piezaVacia.getLayoutParams();

                piezaVacia.setLayoutParams(releasedChild.getLayoutParams());
                releasedChild.setLayoutParams(lp);
                invalidate();

                // Se reproduce el sonido de deslizamiento de la pieza
                soundPool.play(deslizar, 1, 1, 1, 0, 1);

                if (estaCompleto) {
                    piezaVacia.setVisibility(VISIBLE);
                    // Se reproduce el sonido de finalización del puzzle
                    soundPool.play(exito, 1, 1, 1, 0, 1);
                    occ.onComplete();
                }
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return vdh.shouldInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        vdh.processTouchEvent(event);
        return true;
    }

    @Override
    public void computeScroll() {
        if (vdh.continueSettling(true)) {
            invalidate();
        }
    }

    // Esté método trocea la imagen que toca en función
    // del número de cortes que se le pasan por parámetro.
    public void establecerImagen(int idImagen, int numCortes) throws IOException {
        this.numCortes = numCortes;
        this.idImagen = idImagen;

        if (anchuraImagen != 0 && alturaImagen != 0) {
            crearPiezas();
        }
    }

    // Este método crea las piezas del puzzle y las desordena.
    private void crearPiezas() throws IOException {
        removeAllViews();
        ph.establecerNumeroCortes(numCortes);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inDensity = dm.densityDpi;

        storageReference = FirebaseStorage.getInstance().getReference();
        imageRef = storageReference.child("img_0" + idImagen + ".jpg");

        final File localFile = File.createTempFile("Images", "jpg");
        FileDownloadTask task = imageRef.getFile(localFile);
        task.addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                my_image = BitmapFactory.decodeFile(localFile.getAbsolutePath());

                Bitmap bitmap = escalarImagen(my_image, anchuraImagen, alturaImagen);
                my_image.recycle();

                anchuraPieza = anchuraImagen / numCortes;
                alturaPieza = alturaImagen / numCortes;

                for (int i = 0; i < numCortes; i++) {
                    for (int j = 0; j < numCortes; j++) {
                        ImageView iv = new ImageView(getContext());
                        iv.setScaleType(ImageView.ScaleType.FIT_XY);
                        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.leftMargin = j * anchuraPieza;
                        lp.topMargin = i * alturaPieza;
                        iv.setLayoutParams(lp);
                        Bitmap b = Bitmap.createBitmap(bitmap, lp.leftMargin, lp.topMargin, anchuraPieza, alturaPieza);
                        iv.setImageBitmap(b);
                        addView(iv);
                    }
                }
                desordenarPiezas();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(getContext(), "Descarga fallida", Toast.LENGTH_LONG).show();
            }
        });
    }

    // Este método escala la imagen para adaptarla a la pantalla.
    private Bitmap escalarImagen(Bitmap bm, int nuevaAnchura, int nuevaAltura) {
        int ancho = bm.getWidth();
        int alto = bm.getHeight();
        float escaladoAncho = ((float) nuevaAnchura) / ancho;
        float escaladoAlto = ((float) nuevaAltura) / alto;
        Matrix matriz = new Matrix();
        matriz.postScale(escaladoAncho, escaladoAlto);
        return Bitmap.createBitmap(bm, 0, 0, ancho, alto, matriz, true);
    }

    // Este método desordena las piezas.
    private void desordenarPiezas() {
        int num = numCortes * numCortes * 8;
        View piezaVacia = getChildAt(0);
        View vistaVecina;

        for (int i = 0; i < num; i++) {
            int posicionVecina = ph.encontrarIndiceVecinoPiezaVacia();
            ViewGroup.LayoutParams lpPiezaVacia = piezaVacia.getLayoutParams();
            vistaVecina = getChildAt(posicionVecina);
            piezaVacia.setLayoutParams(vistaVecina.getLayoutParams());
            vistaVecina.setLayoutParams(lpPiezaVacia);
            ph.intercambiarPosicionConPiezaVacia(posicionVecina);
        }

        piezaVacia.setVisibility(INVISIBLE);
    }

    // Este método dispara los acontecimientos que ocurren cuando se completa el puzzle.
    public void setOnCompleteCallback(OnCompleteCallback onCompleteCallback) {
        occ = onCompleteCallback;
    }

    public interface OnCompleteCallback {
        void onComplete();
    }

}
