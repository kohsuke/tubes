package com.sun.tubes;

import java.text.SimpleDateFormat;

import javax.annotation.PreDestroy;

import com.sun.istack.NotNull;
import com.sun.tubes.helper.AbstractTubeImpl;
import com.sun.tubes.helper.AbstractFilterTubeImpl;


/**
 * Data processing element.
 *
 * <h2>What is a {@link Tube}?</h2>
 * <p>
 * {@link Tube} is a basic processing unit that represents a single link in
 * a data processing chain. Mutliple tubes are often put together in
 * a line (it needs not one dimensional &mdash; more later), and act on
 * a specific data type in a sequential fashion.
 *
 * <p>
 * {@link Tube}s run asynchronously. That is, there is no guarantee that
 * {@link #processRequest} and {@link #processResponse} run
 * in the same thread, nor is there any guarantee that this tube and next
 * tube run in the same thread. Furthermore, one thread may be used to
 * run multiple tubelines in turn (just like a real CPU runs multiple
 * threads in turn.)
 *
 *
 * <h2>Tube Lifecycle</h2>
 * A tubeline may be expensive to set up, so once it's created it should be reused.
 * A tubeline is not reentrant; one tubeline can beused to process one request/response
 * at at time. The same tubeline instance may serve multiple request/response,
 * if one comes after another and they don't overlap.
 * <p>
 * Where a need arises to process multiple requests concurrently, a tubeline
 * must be cloned through {@link TubeCloner}. Caching strategies may be used to
 * provide a set of tubeline instance that can be reused to reduce the need to
 * clone tubelines.
 * <p>
 * Before a tubeline owner dies, it may invoke {@link #preDestroy()} on the last
 * remaining tubeline.
 *
 * <h2>Tube and state</h2>
 * <p>
 * The lifecycle of tubelines is designed to allow a {@link Tube} to store various
 * state in easily accessible fashion.
 *
 * <h3>Per-packet state</h3>
 * <p>
 * We refer to the data type of the tube as the "packet" in the following description.
 * <p>
 * Any information that changes from a packet to packet should be
 * stored in the packet (this is data type specific).
 *
 * <h3>Per-thread state</h3>
 * <p>
 * Any expensive-to-create objects that are non-reentrant can be stored
 * either in instance variables of a {@link Tube}, or a static {@link ThreadLocal}.
 *
 * <p>
 * The first approach works, because {@link Tube} is
 * non reentrant. When a tube is copied, new instances should be allocated
 * so that two {@link Tube} instances don't share thread-unsafe resources.
 *
 * <p>
 * Similarly the second approach works, since {@link ThreadLocal} guarantees
 * that each thread gets its own private copy.
 *
 * <p>
 * The former is faster to access, and you need not worry about clean up.
 * On the other hand, because there can be many more concurrent requests
 * than # of threads, you may end up holding onto more resources than necessary.
 *
 * <p>
 * This includes state like canonicalizers, JAXB unmarshallers,
 * {@link SimpleDateFormat}, etc.
 *
 *
 * <h3>VM-wide state</h3>
 * <p>
 * <tt>static</tt> is always there for you to use.
 *
 *
 *
 * @see AbstractTubeImpl
 * @see AbstractFilterTubeImpl
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 * @author Pete Hendry
 */
public interface Tube<T> {

    /**
     * Acts on a request and perform some protocol specific operation.
     *
     * @param request data to be processed by the tube implementation.
     *
     * @return
     *      A {@link NextAction<T>} object that represents the next action
     *      to be taken by the JAX-WS runtime.
     */
    NextAction<T> processRequest(@NotNull T request);


    /**
     * Acts on a response and performs some protocol specific operation.
     *
     * <p>
     * Once a {@link Tube<T>#processRequest(T)} is invoked, this method
     * will be always invoked with the response, before this {@link Tube}
     * processes another request.
     *
     * @param response the data to be processed as a response.
     *
     * @return
     *      A {@link NextAction} object that represents the next action
     *      to be taken by the JAX-WS runtime.
     */
    @NotNull NextAction<T> processResponse(@NotNull T response);

    /**
     * Acts on a exception and performs some clean up operations.
     *
     * <p>
     * If a {@link Tube<T>#processRequest(T)}, {@link Tube<T>#processResponse(T)},
     * {@link #processException(Throwable)} throws an exception, this method
     * will be always invoked on all the {@link Tube}s in the remaining
     * {@link NextAction}s.
     *
     * <p>
     * @param t the error.
     *
     * @return
     *      A {@link NextAction} object that represents the next action
     *      to be taken by the JAX-WS runtime.
     */
    @NotNull NextAction<T> processException(@NotNull Throwable t);

    /**
     * Invoked before the last copy of the pipeline is about to be discarded,
     * to give {@link Tube}s a chance to clean up any resources.
     *
     * <p>
     * This can be used to invoke {@link PreDestroy} lifecycle methods
     * on user handler. The invocation of it is optional on the client side,
     * but mandatory on the server side.
     *
     * <p>
     * When multiple copies of pipelines are created, this method is called
     * only on one of them.
     */
    void preDestroy();

    /**
     * Creates an identical clone of this {@link Tube}.
     *
     * <p>
     * This method creates an identical pipeline that can be used
     * concurrently with this pipeline. When the caller of a pipeline
     * is multi-threaded and need concurrent use of the same pipeline,
     * it can do so by creating copies through this method.
     *
     * <h3>Implementation Note</h3>
     * <p>
     * It is the implementation's responsibility to call
     * {@link TubeCloner#add(Tube,Tube)} to register the copied pipe
     * with the original. This is required before you start copying
     * the other {@link Tube} references you have, or else there's a
     * risk of infinite recursion.
     * <p>
     * For most {@link Tube} implementations that delegate to another
     * {@link Tube}, this method requires that you also copy the {@link Tube}
     * that you delegate to.
     * <p>
     * For limited number of {@link Tube}s that do not maintain any
     * thread unsafe resource, it is allowed to simply return <tt>this</tt>
     * from this method (notice that even if you are stateless, if you
     * got a delegating {@link Tube} and that one isn't stateless, you
     * still have to copy yourself.)
     *
     * <p>
     * Note that this method might be invoked by one thread while another
     * thread is executing the other process method.
     *
     * @param cloner
     *      Use this object (in particular its {@link TubeCloner#copy(Tube)} method
     *      to clone other pipe references you have
     *      in your pipe. See {@link TubeCloner} for more discussion
     *      about why.
     *
     * @return
     *      always non-null {@link Tube}.
     */
    Tube<T> copy(TubeCloner cloner);
}
