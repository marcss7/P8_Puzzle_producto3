package edu.uoc.resolvers;

/*
    Esta clase representa cada una de las puntuaciones que se obtienen en el juego.
 */
public class Puntuacion {

    private String nombre;
    private String fecha;
    private double tiempo;

    public Puntuacion() {
    }

    public Puntuacion(String nombre, String fecha, double tiempo) {
        this.nombre = nombre;
        this.fecha = fecha;
        this.tiempo = tiempo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public double getTiempo() {
        return tiempo;
    }

    public void setTiempo(double tiempo) {
        this.tiempo = tiempo;
    }
}
