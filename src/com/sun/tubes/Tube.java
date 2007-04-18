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

import javax.annotation.PreDestroy;

import com.sun.istack.NotNull;


/**
 * @author Pete Hendry
 * @version $Id$
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
