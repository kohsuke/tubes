package com.sun.tubes;

import com.sun.tubes.helper.AbstractTubeImpl;


/**
 * @author Pete Hendry
 * @version $Id$
 */
public class SimpleTube<T>
    extends AbstractTubeImpl<T>
    implements ModifiableTube<T> {

    int preDestroyCount;
    int requestCount;
    int responseCount;
    int exceptionCount;
    int copyCount;

    Tube<T> next;


    public SimpleTube( Tube<T> next ) {
        this.next = next;
    }


    private SimpleTube( SimpleTube<T> that, TubeCloner cloner ) {
        super(that, cloner);
        if ( that.next != null ) {
            this.next = cloner.copy(that.next);
        }
    }


    public SimpleTube<T> copy( TubeCloner cloner ) {
        ++copyCount;
        return new SimpleTube<T>(this, cloner);
    }


    public NextAction<T> processRequest( T request ) {
        ++requestCount;
        if ( next == null ) {
            return returnWithAction(request);
        }
        return invokeAction(next, request);
    }


    public NextAction<T> processResponse( T response ) {
        ++responseCount;
        return returnWithAction(response);
    }


    public NextAction<T> processException( Throwable t ) {
        ++exceptionCount;
        return throwAction(t);
    }


    public void preDestroy() {
        ++preDestroyCount;
    }


    public void setNext( Tube<T> next ) {
        this.next = next;
    }


    public Tube<T> getNext() {
        return next;
    }
}
