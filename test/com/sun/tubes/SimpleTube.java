/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems Inc. All Rights Reserved
 */
package com.sun.tubes;

import com.sun.tubes.helper.AbstractTubeImpl;


/**
 * @author Pete Hendry
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
