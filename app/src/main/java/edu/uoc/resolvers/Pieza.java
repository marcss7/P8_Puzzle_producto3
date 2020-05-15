package edu.uoc.resolvers;

/*
   Esta clase representa la posición de cada una de las piezas en las
   que se divide el puzzle y que es necesario recolocar para resolverlo.
 */

class Pieza {

    int posicion;
    int pvertical;
    int phorizontal;

    Pieza(int posicion, int pvertical, int phorizontal){
        this.posicion = posicion;
        this.pvertical = pvertical;
        this.phorizontal = phorizontal;
    }

}
