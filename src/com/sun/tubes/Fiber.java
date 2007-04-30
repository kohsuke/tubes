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

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.istack.Nullable;
import com.sun.istack.NotNull;

import com.sun.tubes.helper.AbstractFilterTubeImpl;


/**
 * User-level thread&#x2E; Represents the execution of one request/response processing.
 *
 * <p>
 * JAX-WS RI is capable of running a large number of request/response concurrently by
 * using a relatively small number of threads. This is made possible by utilizing
 * a {@link Fiber} &mdash; a user-level thread that gets created for each request/response
 * processing.
 *
 * <p>
 * A fiber remembers where in the pipeline the processing is at, what needs to be
 * executed on the way out (when processing response), and other additional information
 * specific to the execution of a particular request/response.
 *
 * <h2>Suspend/Resume</h2>
 * <p>
 * Fiber can be {@link NextAction#suspend() suspended} by a {@link Tube}.
 * When a fiber is suspended, it will be kept on the side until it is
 * {@link Fiber<T>#resume(T) resumed}. This allows threads to go execute
 * other runnable fibers, allowing efficient utilization of smaller number of
 * threads.
 *
 * <h2>Context-switch Interception</h2>
 * <p>
 * {@link FiberContextSwitchInterceptor} allows {@link Tube}s
 * to perform additional processing every time a thread starts running a fiber
 * and stops running it.
 *
 * <h2>Context ClassLoader</h2>
 * <p>
 * Just like thread, a fiber has a context class loader (CCL.) A fiber's CCL
 * becomes the thread's CCL when it's executing the fiber. The original CCL
 * of the thread will be restored when the thread leaves the fiber execution.
 *
 *
 * <h2>Debugging Aid</h2>
 * <p>
 * Because {@link Fiber} doesn't keep much in the call stack, and instead use
 * {@link #conts} to store the continuation, debugging fiber related activities
 * could be harder.
 *
 * <p>
 * Setting the {@link #LOGGER} for FINE would give you basic start/stop/resume/suspend
 * level logging. Using FINER would cause more detailed logging, which includes
 * what tubes are executed in what order and how they behaved.
 *
 * <p>
 * When you debug the server side, consider setting {@link Fiber#serializeExecution}
 * to true, so that execution of fibers are serialized. Debugging a server
 * with more than one running threads is very tricky, and this switch will
 * prevent that. This can be also enabled by setting the system property on.
 * See the source code.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 * @author Pete Hendry
 */
public class Fiber<T>
    implements Runnable {

    /**
     * {@link Tube}s whose {@link Tube<T>#processResponse(T)} method needs
     * to be invoked on the way back.
     */
    private List<Tube<T>> conts = new ArrayList<Tube<T>>(16);

    /**
     * If this field is non-null, the next instruction to execute is
     * to call its {@link Tube<T>#processRequest(T)}. Otherwise
     * the instruction is to call {@link #conts}.
     */
    private Tube<T> next;

    private T packet;

    private Throwable/*but really it's either RuntimeException or Error*/ throwable;

    public final Engine<T> owner;

    /**
     * Is this thread suspended? 0=not suspended, 1=suspended.
     *
     * <p>
     * Logically this is just a boolean, but we need to prepare for the case
     * where the thread is {@link Fiber<T>#resume(T) resumed} before we get to the {@link #suspend()}.
     * This happens when things happen in the following order:
     *
     * <ol>
     *  <li>Tube decides that the fiber needs to be suspended to wait for the external event.
     *  <li>Tube hooks up fiber with some external mechanism (like NIO channel selector)
     *  <li>Tube returns with {@link NextAction#suspend()}.
     *  <li>"External mechanism" becomes signal state and invokes {@link Fiber<T>#resume(T)}
     *      to wake up fiber
     *  <li>{@link Fiber#doRun} invokes {@link Fiber#suspend()}.
     * </ol>
     *
     * <p>
     * Using int, this will work OK because {@link #suspendedCount} becomes -1 when
     * {@link Fiber<T>#resume(T)} occurs before {@link #suspend()}.
     *
     * <p>
     * Increment and decrement is guarded by 'this' object.
     */
    private volatile int suspendedCount = 0;

    /**
     * Is this fiber completed?
     */
    private volatile boolean completed;

    /**
     * Is this {@link Fiber} currently running in the synchronous mode?
     */
    private boolean synchronous;

    private boolean interrupted;

    private final int id;

    /**
     * Active {@link FiberContextSwitchInterceptor}s for this fiber.
     */
    private List<FiberContextSwitchInterceptor<T>> interceptors;

    /**
     * Not null when {@link #interceptors} is not null.
     */
    private InterceptorHandler interceptorHandler;

    /**
     * This flag is set to true when a new interceptor is added.
     *
     * When that happens, we need to first exit the current interceptors
     * and then reenter them, so that the newly added interceptors start
     * taking effect. This flag is used to control that flow.
     */
    private boolean needsToReenter;

    /**
     * Fiber's context {@link ClassLoader}.
     */
    private @Nullable ClassLoader contextClassLoader;

    private @Nullable CompletionCallback<T> completionCallback;

    /**
     * Set to true if this fiber is started asynchronously, to avoid
     * doubly-invoking completion code.
     */
    private boolean started;

    /**
     * Callback to be invoked when a {@link Fiber} finishs execution.
     */
    public interface CompletionCallback<T> {
        /**
         * Indicates that the fiber has finished its execution.
         *
         * <p>
         * Since the JAX-WS RI runs asynchronously,
         * this method maybe invoked by a different thread
         * than any of the threads that started it or run a part of tubeline.
         *
         * @param response the response data
         */
        void onCompletion(@NotNull T response);

        /**
         * Indicates that the fiber has finished abnormally, by throwing a given {@link Throwable}.
         *
         * @param error exception for the error
         */
        void onCompletion(@NotNull Throwable error);
    }

    protected Fiber(Engine<T> engine) {
        this.owner = engine;
        if(isTraceEnabled()) {
            id = iotaGen.incrementAndGet();
            LOGGER.fine(getName()+" created");
        } else {
            id = -1;
        }

        // if this is run from another fiber, then we naturally inherit its context classloader,
        // so this code works for fiber->fiber inheritance just fine.
        contextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    /**
     * Starts the execution of this fiber asynchronously.
     *
     * <p>
     * This method works like {@link Thread#start()}.
     *
     * @param tubeline
     *      The first tube of the tubeline that will act on the packet.
     * @param request
     *      The request packet to be passed to <tt>startPoint.processRequest()</tt>.
     * @param completionCallback
     *      The callback to be invoked when the processing is finished and the
     *      final response packet is available.
     *
     * @see Fiber<T>#runSync(Tube,T)
     */
    public void start(@NotNull Tube<T> tubeline, @NotNull T request, @Nullable CompletionCallback<T> completionCallback) {
        next = tubeline;
        this.packet = request;
        this.completionCallback = completionCallback;
        this.started = true;
        owner.addRunnable(this);
    }

    /**
     * Wakes up a suspended fiber.
     *
     * <p>
     * If a fiber was suspended from the {@link Tube<T>#processRequest(T)} method,
     * then the execution will be resumed from the corresponding
     * {@link Tube<T>#processResponse(T)} method with the specified response packet
     * as the parameter.
     *
     * <p>
     * If a fiber was suspended from the {@link Tube<T>#processResponse(T)} method,
     * then the execution will be resumed from the next tube's
     * {@link Tube<T>#processResponse(T)} method with the specified response packet
     * as the parameter.
     *
     * <p>
     * This method is implemented in a race-free way. Another thread can invoke
     * this method even before this fiber goes into the suspension mode. So the caller
     * need not worry about synchronizing {@link NextAction#suspend()} and this method.
     *
     * @param response the response data
     */
    public synchronized void resume(@NotNull T response) {
        if(isTraceEnabled())
            LOGGER.fine(getName()+" resumed");
        packet = response;
        if( --suspendedCount == 0 ) {
            if(synchronous) {
                notifyAll();
            } else {
                owner.addRunnable(this);
            }
        }
    }


    /**
     * Suspends this fiber's execution until the resume method is invoked.
     *
     * The call returns immediately, and when the fiber is resumed
     * the execution picks up from the last scheduled continuation.
     */
    private synchronized void suspend() {
        if(isTraceEnabled())
            LOGGER.fine(getName()+" suspended");
        suspendedCount++;
    }

    /**
     * Adds a new {@link FiberContextSwitchInterceptor} to this fiber.
     *
     * <p>
     * The newly installed fiber will take effect immediately after the current
     * tube returns from its {@link Tube<T>#processRequest(T)} or
     * {@link Tube<T>#processResponse(T)}, before the next tube begins processing.
     *
     * <p>
     * So when the tubeline consists of X and Y, and when X installs an interceptor,
     * the order of execution will be as follows:
     *
     * <ol>
     *  <li>X.processRequest()
     *  <li>interceptor gets installed
     *  <li>interceptor.execute() is invoked
     *  <li>Y.processRequest()
     * </ol>
     *
     * @param interceptor the interceptor to be added
     */
    public void addInterceptor(@NotNull FiberContextSwitchInterceptor<T> interceptor) {
        if(interceptors ==null) {
            interceptors = new ArrayList<FiberContextSwitchInterceptor<T>>();
            interceptorHandler = new InterceptorHandler();
        }
        interceptors.add(interceptor);
        needsToReenter = true;
    }

    /**
     * Removes a {@link FiberContextSwitchInterceptor} from this fiber.
     *
     * <p>
     * The removal of the interceptor takes effect immediately after the current
     * tube returns from its {@link Tube<T>#processRequest(T)} or
     * {@link Tube<T>#processResponse(T)}, before the next tube begins processing.
     *
     *
     * <p>
     * So when the tubeline consists of X and Y, and when Y uninstalls an interceptor
     * on the way out, then the order of execution will be as follows:
     *
     * <ol>
     *  <li>Y.processResponse() (notice that this happens with interceptor.execute() in the callstack)
     *  <li>interceptor gets uninstalled
     *  <li>interceptor.execute() returns
     *  <li>X.processResponse()
     * </ol>
     *
     * @param interceptor the interceptor to be removed
     * @return true if the specified interceptor was removed. False if
     *      the specified interceptor was not registered with this fiber to begin with.
     */
    public boolean removeInterceptor(@NotNull FiberContextSwitchInterceptor<T> interceptor) {
        if(interceptors !=null && interceptors.remove(interceptor)) {
            needsToReenter = true;
            return true;
        }
        return false;
    }

    /**
     * Gets the context {@link ClassLoader} of this fiber.
     *
     * @return the context classloader of this Fiber
     */
    public @Nullable ClassLoader getContextClassLoader() {
        return contextClassLoader;
    }

    /**
     * Sets the context {@link ClassLoader} of this fiber.
     *
     * @param contextClassLoader the classloader to be used by this instance
     *
     * @return the previously set classloader
     */
    public ClassLoader setContextClassLoader(@Nullable ClassLoader contextClassLoader) {
        ClassLoader r = this.contextClassLoader;
        this.contextClassLoader = contextClassLoader;
        return r;
    }

    /**
     * DO NOT CALL THIS METHOD. This is an implementation detail
     * of {@link Fiber}.
     */
    @Deprecated
    public void run() {
        assert !synchronous;
        next = doRun(next);
        completionCheck();
    }

    /**
     * Runs a given {@link Tube} (and everything thereafter) synchronously.
     *
     * <p>
     * This method blocks and returns only when all the successive {@link Tube}s
     * complete their request/response processing. This method can be used
     * if a {@link Tube} needs to fallback to synchronous processing.
     *
     * <h3>Example:</h3>
     * <pre>
     * class FooTube extends {@link AbstractFilterTubeImpl} {
     *   NextAction processRequest(T request) {
     *     // run everything synchronously and return with the response packet
     *     return doReturnWith(Fiber.current().runSync(next,request));
     *   }
     *   NextAction processResponse(T response) {
     *     // never be invoked
     *   }
     * }
     * </pre>
     *
     * @param tubeline
     *      The first tube of the tubeline that will act on the packet.
     * @param request
     *      The request packet to be passed to <tt>startPoint.processRequest()</tt>.
     * @return
     *      The response packet to the <tt>request</tt>.
     *
     * @see Fiber<T>#start(Tube, T, CompletionCallback)
     */
    public synchronized @NotNull T runSync(@NotNull Tube<T> tubeline, @NotNull T request) {
        // save the current continuation, so that we return runSync() without executing them.
        final List<Tube<T>> oldCont = conts;
        final boolean oldSynchronous = synchronous;

        if(conts.size()>0) {
            conts = new ArrayList<Tube<T>>(16);
        }

        try {
            synchronous = true;
            this.packet = request;
            doRun(tubeline);
            if(throwable!=null) {
                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                }
                if (throwable instanceof Error) {
                    throw (Error) throwable;
                }
                // our system is supposed to only accept Error or RuntimeException
                throw new AssertionError(throwable);
            }
            return this.packet;
        } finally {
            conts = oldCont;
            synchronous = oldSynchronous;
            if(interrupted) {
                Thread.currentThread().interrupt();
                interrupted = false;
            }
            if(!started)
                completionCheck();
        }
    }

    private synchronized void completionCheck() {
        if(conts.size()==0) {
            if(isTraceEnabled())
                LOGGER.fine(getName()+" completed");
            completed = true;
            notifyAll();
            if(completionCallback!=null) {
                if(throwable!=null)
                    completionCallback.onCompletion(throwable);
                else
                    completionCallback.onCompletion(packet);
            }
        }
    }

    ///**
    // * Blocks until the fiber completes.
    // */
    //public synchronized void join() throws InterruptedException {
    //    while(!completed)
    //        wait();
    //}

    /**
     * Invokes all registered {@link InterceptorHandler}s and then call into
     * {@link Fiber#__doRun(Tube)}.
     */
    private class InterceptorHandler implements FiberContextSwitchInterceptor.Work<Tube<T>,Tube<T>> {
        /**
         * Index in {@link Fiber#interceptors} to invoke next.
         */
        private int idx;

        /**
         * Initiate the interception, and eventually invokes {@link Fiber#__doRun(Tube)}.
         *
         * @param next the tube to be invoked
         *
         * @return the next tube to be invoked
         */
        Tube<T> invoke(Tube<T> next) {
            idx=0;
            return execute(next);
        }

        public Tube<T> execute(Tube<T> next) {
            if(idx==interceptors.size()) {
                return __doRun(next);
            } else {
                FiberContextSwitchInterceptor<T> interceptor = interceptors.get(idx++);
                return interceptor.execute(Fiber.this,next,this);
            }
        }
    }

    /**
     * Executes the fiber as much as possible.
     *
     * @param next
     *      The next tube whose {@link Tube<T>#processRequest(T)} is to be invoked. If null,
     *      that means we'll just call {@link Tube<T>#processResponse(T)} on the continuation.
     *
     * @return
     *      If non-null, the next time execution resumes, it should resume from calling
     *      the {@link Tube<T>#processRequest(T)}. Otherwise it means just finishing up
     *      the continuation.
     */
    @SuppressWarnings({"LoopStatementThatDoesntLoop"}) // IntelliJ reports this bogus error
    private Tube<T> doRun(Tube<T> next) {
        Thread currentThread = Thread.currentThread();

        if(isTraceEnabled())
            LOGGER.fine(getName()+" running by "+currentThread.getName());

        if(serializeExecution) {
            serializedExecutionLock.lock();
            try {
                return _doRun(next);
            } finally {
                serializedExecutionLock.unlock();
            }
        } else {
            return _doRun(next);
        }
    }

    private Tube<T> _doRun(Tube<T> next) {
        Thread currentThread = Thread.currentThread();

        ClassLoader old = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(contextClassLoader);
        try {
            do {
                needsToReenter = false;

                // if interceptors are set, go through the interceptors.
                if(interceptorHandler ==null)
                    next = __doRun(next);
                else
                    next = interceptorHandler.invoke(next);
            } while(needsToReenter);

            return next;
        } finally {
            currentThread.setContextClassLoader(old);
        }
    }

    /**
     * To be invoked from {@link #doRun(Tube)}.
     *
     * @param next the tube to be run
     *
     * @return the next tube to be run if blocked or needing to reenter, otherwise null if run
     *         to completion.
     *
     * @see #doRun(Tube<T>)
     */
    private Tube<T> __doRun(Tube<T> next) {
        final Fiber old = CURRENT_FIBER.get();
        CURRENT_FIBER.set(this);

        // if true, lots of debug messages to show what's being executed
        final boolean traceEnabled = LOGGER.isLoggable(Level.FINER);

        try {
            while(!isBlocking() && !needsToReenter) {
                try {
                    NextAction<T> na;
                    Tube<T> last;
                    if(throwable!=null) {
                        if(conts.size()==0) {
                            // nothing else to execute. we are done.
                            return null;
                        }
                        last = popCont();
                        if(traceEnabled)
                            LOGGER.finer(getName()+' '+last+".processException("+throwable+')');
                        na = last.processException(throwable);
                    } else {
                        if(next!=null) {
                            if(traceEnabled)
                                LOGGER.finer(getName()+' '+next+".processRequest("+packet+')');
                            na = next.processRequest(packet);
                            last = next;
                        } else {
                            if(conts.size()==0) {
                                // nothing else to execute. we are done.
                                return null;
                            }
                            last = popCont();
                            if(traceEnabled)
                                LOGGER.finer(getName()+' '+last+".processResponse("+packet+')');
                            na = last.processResponse(packet);
                        }
                    }

                    if(traceEnabled)
                        LOGGER.finer(getName()+' '+last+" returned with "+na);

                    // If resume is called before suspend, then make sure
					// resume(T) is not lost
                    if (na.kind != NextAction.SUSPEND) {
                        packet = na.packet;
                        throwable = na.throwable;
                    }

                    switch(na.kind) {
                    case NextAction.INVOKE:
                        pushCont(last);
                        // fall through next
                    case NextAction.INVOKE_AND_FORGET:
                        next = na.next;
                        break;
                    case NextAction.RETURN:
                    case NextAction.THROW:
                        next = null;
                        break;
                    case NextAction.SUSPEND:
                        pushCont(last);
                        next = null;
                        suspend();
                        break;
                    default:
                        throw new AssertionError();
                    }
                } catch (RuntimeException t) {
                    if(traceEnabled)
                        LOGGER.log(Level.FINER,getName()+" Caught "+t+". Start stack unwinding",t);
                    throwable = t;
                } catch (Error t) {
                    if(traceEnabled)
                        LOGGER.log(Level.FINER,getName()+" Caught "+t+". Start stack unwinding",t);
                    throwable = t;
                }
            }
            // there's nothing we can execute right away.
            // we'll be back when this fiber is resumed.
            return next;
        } finally {
            CURRENT_FIBER.set(old);
        }
    }

    private void pushCont(Tube<T> tube) {
        conts.add(tube);
    }

    private Tube<T> popCont() {
        return conts.remove(conts.size() - 1);
    }

    /**
     * @return true if the fiber needs to block its execution.
     */
    // TODO: synchronization on synchronous case is wrong.
    private boolean isBlocking() {
        if(synchronous) {
            while(suspendedCount==1)
                try {
                    if (isTraceEnabled()) {
                        LOGGER.fine(getName()+" is blocking thread "+Thread.currentThread().getName());
                    }
                    wait(); // the synchronized block is the whole runSync method.
                } catch (InterruptedException e) {
                    // remember that we are interrupted, but don't respond to it
                    // right away. This behavior is in line with what happens
                    // when you are actually running the whole thing synchronously.
                    interrupted = true;
                }
            return false;
        }
        else
            return suspendedCount==1;
    }

    private String getName() {
        return "engine-"+owner.id+"fiber-"+id;
    }

    public String toString() {
        return getName();
    }

    /**
     * Gets the current {@link T} associated with this fiber.
     *
     * <p>
     * @return null if no packet has been associated with the fiber yet.
     */
    public @Nullable T getPacket() {
        return packet;
    }

    /**
     * @return true if this fiber is still running or suspended.
     */
    public boolean isAlive() {
        return !completed;
    }

    /**
     * (ADVANCED)
     *
     * <p>
     * Fiber may run synchronously for various reasons. Perhaps this is
     * on client side and application has invoked a synchronous method call.
     * Perhaps this is on server side and we have deployed on a synchronous
     * transport (like servlet.)
     *
     * <p>
     * When a fiber is run synchronously (IOW by {@link Fiber<T>#runSync(Tube, T)}),
     * further invocations to {@link Fiber<T>#runSync(Tube, T)} can be done
     * without degrading the performance.
     *
     * <p>
     * So this value can be used as a further optimization hint for
     * advanced {@link Tube}s to choose the best strategy to invoke
     * the next {@link Tube}. For example, a tube may want to install
     * a {@link FiberContextSwitchInterceptor} if running async, yet
     * it might find it faster to do {@link Fiber<T>#runSync(Tube, T)}
     * if it's already running synchronously.
     *
     * @return true if the current fiber is being executed synchronously.
     */
    public static boolean isSynchronous() {
        return current().synchronous;
    }

    /**
     * Gets the current fiber that's running.
     *
     * <p>
     * This works like {@link Thread#currentThread()}.
     * This method only works when invoked from {@link Tube}.
     */
    public static @NotNull Fiber current() {
        Fiber fiber = CURRENT_FIBER.get();
        if(fiber==null)
            throw new IllegalStateException("Can be only used from fibers");
        return fiber;
    }

    private static final ThreadLocal<Fiber> CURRENT_FIBER = new ThreadLocal<Fiber>();

    /**
     * Used to allocate unique number for each fiber.
     */
    private static final AtomicInteger iotaGen = new AtomicInteger();

    private static boolean isTraceEnabled() {
        return LOGGER.isLoggable(Level.FINE);
    }

    private static final Logger LOGGER = Logger.getLogger(Fiber.class.getName());


    private static final ReentrantLock serializedExecutionLock = new ReentrantLock();

    /**
     * Set this boolean to true to execute fibers sequentially one by one.
     * See class javadoc.
     */
    public static volatile boolean serializeExecution = Boolean.getBoolean(Fiber.class.getName()+".serialize");

}
