package edu.uoc.resolvers;

import java.util.Date;

public class Puntuacion {

    private String nombre;
    private int nivel;
    private String fecha;
    private double tiempo;

    public Puntuacion() {
    }

    public Puntuacion(String nombre, int nivel, String fecha, double tiempo) {
        this.nombre = nombre;
        this.nivel = nivel;
        this.fecha = fecha;
        this.tiempo = tiempo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getNivel() {
        return nivel;
    }

    public void setNivel(int nivel) {
        this.nivel = nivel;
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
