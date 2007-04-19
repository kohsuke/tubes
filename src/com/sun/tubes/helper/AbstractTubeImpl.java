package com.sun.tubes.helper;

import com.sun.tubes.Tube;
import com.sun.tubes.TubeCloner;
import com.sun.tubes.NextAction;
import com.sun.tubes.Fiber;


/**
 * Base class for {@link Tube <T>} implementation.
 *
 * @author Kohsuke Kawaguchi
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
