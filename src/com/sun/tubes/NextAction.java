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
package com.sun.tubes;


/**
 * Indicates what shall happen after {@link Tube<T>#processRequest(T)} or
 * {@link Tube<T>#processResponse(T)} returns.
 *
 * <p>
 * To allow reuse of this object, this class is mutable.
 *
 * @author Kohsuke Kawaguchi
 * @author Pete Hendry
 */
public class NextAction<T> {

    int kind;
    Tube<T> next;
    T packet;
    /**
     * Really either {@link RuntimeException} or {@link Error}.
     */
    Throwable throwable;

    // public enum Kind { INVOKE, INVOKE_AND_FORGET, RETURN, SUSPEND }

    static final int INVOKE = 0;
    static final int INVOKE_AND_FORGET = 1;
    static final int RETURN = 2;
    static final int THROW = 3;
    static final int SUSPEND = 4;

    private void set(int k, Tube<T> v, T p, Throwable t) {
        this.kind = k;
        this.next = v;
        this.packet = p;
        this.throwable = t;
    }

    /**
     * Indicates that the next action should be to
     * invoke the next tube's {@link Tube <T>#processRequest(T)},
     * then later invoke the current tube's {@link Tube <T>#processResponse(T)}
     * with the response packet.
     */
    public void invoke( Tube<T> next, T p) {
        set(INVOKE, next, p, null);
    }

    /**
     * Indicates that the next action should be to
     * invoke the next tube's {@link Tube <T>#processRequest(T)},
     * but the current tube doesn't want to receive the response packet to
     * its {@link Tube <T>#processResponse(T)}.
     */
    public void invokeAndForget( Tube<T> next, T p) {
        set(INVOKE_AND_FORGET, next, p, null);
    }

    /**
     * Indicates that the next action is to flip the processing direction
     * and starts response processing.
     */
    public void returnWith( T response ) {
        set(RETURN, null, response, null);
    }

    /**
     * Indicates that the next action is to flip the processing direction
     * and starts exception processing.
     *
     * @param t
     *      Either {@link RuntimeException} or {@link Error}, but defined to
     *      take {@link Throwable} because {@link Tube <T>#processException(Throwable)}
     *      takes {@link Throwable}.
     */
    public void throwException(Throwable t) {
        assert t instanceof RuntimeException || t instanceof Error;
        set(THROW,null,null,t);
    }

    /**
     * Indicates that the fiber should be suspended.
     * Once {@link Fiber <T>#resume(T) resumed}, return the response processing.
     */
    public void suspend() {
        set(SUSPEND, null, null, null);
    }

    /**
     * Dumps the contents to assist debugging.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(super.toString()).append(" [");
        buf.append("kind=").append(getKindString()).append(',');
        buf.append("next=").append(next).append(',');
        buf.append("packet=").append(packet).append(',');
        buf.append("throwable=").append(throwable).append(']');
        return buf.toString();
    }

    /**
     * Returns {@link #kind} in a human readable string, to assist debugging.
     */
    public String getKindString() {
        switch(kind) {
        case INVOKE:            return "INVOKE";
        case INVOKE_AND_FORGET: return "INVOKE_AND_FORGET";
        case RETURN:            return "RETURN";
        case THROW:             return "THROW";
        case SUSPEND:           return "SUSPEND";
        default:                throw new AssertionError(kind);
        }
    }

}
