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
 * Copyright 2007 Sun Microsystems Inc. All Rights Reserved
 */
package com.sun.tubes.helper;

import com.sun.tubes.Tube;
import com.sun.tubes.TubeCloner;
import com.sun.tubes.NextAction;
import com.sun.tubes.Fiber;


/**
 * Base class for {@link Tube <T>} implementation.
 *
 * @author Kohsuke Kawaguchi
 * @author Pete Hendry
 */
public abstract class AbstractTubeImpl<T>
    implements Tube<T> {

    /**
     * Default constructor.
     */
    protected AbstractTubeImpl() {
    }

    /**
     * Copy constructor.
     *
     * @param that tube being copied
     * @param cloner cloner to use to prevent looping in graph
     */
    protected AbstractTubeImpl( AbstractTubeImpl<T> that, TubeCloner cloner) {
        cloner.add(that,this);
    }

    protected NextAction<T> invokeAction(Tube<T> next, T packet) {
        NextAction<T> na = createNextAction();
        na.invoke(next,packet);
        return na;
    }


    protected NextAction<T> invokeAndForgetAction(Tube<T> next, T packet) {
        NextAction<T> na = createNextAction();
        na.invokeAndForget(next,packet);
        return na;
    }

    protected NextAction<T> returnWithAction(T response) {
        NextAction<T> na = createNextAction();
        na.returnWith(response);
        return na;
    }

    protected NextAction<T> suspendAction() {
        NextAction<T> na = createNextAction();
        na.suspend();
        return na;
    }

    protected NextAction<T> throwAction(Throwable t) {
        NextAction<T> na = createNextAction();
        na.throwException(t);
        return na;
    }

    protected NextAction<T> createNextAction() {
        return new NextAction<T>();
    }

    /**
     * "Dual stack" compatibility mechanism.
     *
     * @param p  the data object to be processed by the tube
     *
     * @return resulting data object after processing
     */
    public T process(T p) {
        return ((Fiber<T>)Fiber.current()).runSync(this,p);
    }

    public abstract AbstractTubeImpl<T> copy(TubeCloner cloner);
}
