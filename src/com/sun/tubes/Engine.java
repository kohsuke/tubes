package com.sun.tubes;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Collection of {@link Fiber}s.
 * Owns an {@link Executor} to run them.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public class Engine<T> {
    private volatile Executor threadPool;
    public final String id;

    public Engine(String id, Executor threadPool) {
        this(id);
        this.threadPool = threadPool;
    }

    public Engine(String id) {
        this.id = id;
    }

    public void setExecutor(Executor threadPool) {
        this.threadPool = threadPool;
    }

    void addRunnable( Fiber<T> fiber) {
        if(threadPool==null) {
            synchronized(this) {
                threadPool = Executors.newFixedThreadPool(5, new Engine.DaemonThreadFactory());
            }
        }
        threadPool.execute(fiber);
    }

    /**
     * Creates a new fiber in a suspended state.
     *
     * <p>
     * To start the returned fiber, call {@link Fiber<T>#start(Tube,T,Fiber.CompletionCallback)}.
     * It will start executing the given {@link Tube<T>} with the given {@link T}.
     *
     * @return new Fiber<T>
     */
    public Fiber<T> createFiber() {
        return new Fiber<T>(this);
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            Thread daemonThread = new Thread(r, "JAXWS-Engine-"+threadNumber.getAndIncrement());
            daemonThread.setDaemon(Boolean.TRUE);
            return daemonThread;
        }
    }
}
