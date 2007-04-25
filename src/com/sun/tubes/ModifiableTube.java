package com.sun.tubes;


/**
 * Tubes that implement this interface can have their next element inspected and changed.
 * 
 * @author Pete Hendry
 * @version $Id$
 */
public interface ModifiableTube<T>
    extends Tube<T>{

    void setNext(Tube<T> next);


    Tube<T> getNext();
}
