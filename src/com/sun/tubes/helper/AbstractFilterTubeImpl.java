package com.sun.tubes.helper;

import com.sun.istack.NotNull;

import com.sun.tubes.Tube;
import com.sun.tubes.TubeCloner;
import com.sun.tubes.NextAction;


/**
 * Convenient default implementation for filtering {@link Tube}.
 *
 * <p>
 * In this prototype, this is not that convenient, but in the real production
 * code where we have {@code preDestroy()} and {@code clone()}, this
 * is fairly handy.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractFilterTubeImpl<T>
    extends AbstractTubeImpl<T> {
    protected final Tube<T> next;

    protected AbstractFilterTubeImpl(Tube<T> next) {
        this.next = next;
    }

    protected AbstractFilterTubeImpl( AbstractFilterTubeImpl<T> that, TubeCloner cloner) {
        super(that, cloner);
        this.next = cloner.copy(that.next);
    }

    /**
     * Default no-op implementation.
     */
    public @NotNull
    NextAction<T> processRequest( T request) {
        return invokeAction(next,request);
    }

    /**
     * Default no-op implementation.
     */
    public @NotNull NextAction<T> processResponse(T response) {
        return returnWithAction(response);
    }

    /**
     * Default no-op implementation.
     */
    public @NotNull NextAction<T> processException(Throwable t) {
        return throwAction(t);
    }

    public void preDestroy() {
        next.preDestroy();
    }
}
