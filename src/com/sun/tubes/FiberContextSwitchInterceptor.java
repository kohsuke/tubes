/**
 * Copyright Notice
 *
 * Copyright (c) 2000-2004, Cape Clear Software.
 * All Rights Reserved
 *
 * This software is protected by copyright and other intellectual
 * property rights and by international treaties. Any unauthorised
 * reproduction or distribution of this software or any portion
 * thereof is strictly prohibited.
 */
package com.sun.tubes;

import java.security.AccessController;
import java.security.PrivilegedAction;


/**
 * Interception for {@link com.sun.xml.ws.api.pipe.Fiber} context switch.
 *
 * <p>
 * Even though pipeline runs asynchronously, sometimes it's desirable
 * to bind some state to the current thread running a fiber. Such state
 * may include security subject (in terms of {@link AccessController#doPrivileged}),
 * or a transaction.
 *
 * <p>
 * This mechanism makes it possible to do such things, by allowing
 * some code to be executed before and after a thread executes a fiber.
 *
 * <p>
 * The design also encapsulates the entire fiber execution in a single
 * opaque method invocation {@link FiberContextSwitchInterceptor.Work#execute}, allowing the use of
 * <tt>finally</tt> block.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public interface FiberContextSwitchInterceptor<T> {
    /**
     * Allows the interception of the fiber execution.
     *
     * <p>
     * This method needs to be implemented like this:
     *
     * <pre>
     * &lt;R,P> R execute( Fiber f, P p, Work&lt;R,P> work ) {
     *   // do some preparation work
     *   ...
     *   try {
     *     // invoke
     *     return work.execute(p);
     *   } finally {
     *     // do some clean up work
     *     ...
     *   }
     * }
     * </pre>
     *
     * <p>
     * While somewhat unintuitive,
     * this interception mechanism enables the interceptor to wrap
     * the whole fiber execution into a {@link AccessController#doPrivileged(PrivilegedAction)},
     * for example.
     *
     * @param f
     *      {@link Fiber} to be executed.
     * @param p
     *      The opaque parameter value for {@link FiberContextSwitchInterceptor.Work}. Simply pass this value to
     *      {@link FiberContextSwitchInterceptor.Work#execute(Object)}.
     * @return
     *      The opaque return value from the the {@link FiberContextSwitchInterceptor.Work}. Simply return
     *      the value from {@link FiberContextSwitchInterceptor.Work#execute(Object)}.
     */
    <R,P> R execute( Fiber<T> f, P p, FiberContextSwitchInterceptor.Work<R,P> work );

    /**
     * Abstraction of the execution that happens inside the interceptor.
     */
    interface Work<R,P> {
        /**
         * Have the current thread executes the current fiber,
         * and returns when it stops doing so.
         *
         * <p>
         * The parameter and the return value is controlled by the
         * JAX-WS runtime, and interceptors should simply treat
         * them as opaque values.
         */
        R execute(P param);
    }
}
