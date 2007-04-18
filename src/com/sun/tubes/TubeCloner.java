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

import java.util.Map;
import java.util.HashMap;


/**
 * Clones the whole pipeline.
 *
 * <p>
 * Since {@link com.sun.xml.ws.api.pipe.Tube}s may form an arbitrary directed graph, someone needs
 * to keep track of isomorphism for a clone to happen correctly. This class
 * serves that role.
 *
 * @author Kohsuke Kawaguchi
 */
public class TubeCloner {
    // Pipe to pipe, or tube to tube
    protected final Map<Object,Object> master2copy = new HashMap<Object,Object>();

    protected TubeCloner() {
    }

    /**
     * Invoked by a client of a tube to clone the whole pipeline.
     *
     * <p>
     * {@link Tube}s implementing the {@link Tube#copy(TubeCloner)} method
     * shall use {@link #copy(Tube)} method.
     *
     * @param p
     *      The entry point of a pipeline to be copied. must not be null.
     * @return
     *      The cloned pipeline. Always non-null.
     */
    public static Tube clone(Tube p) {
        final TubeCloner cloner = new TubeCloner();

        return cloner.copy(p);
    }

    /**
     * Invoked by a {@link Tube#copy(TubeCloner)} implementation
     * to copy a reference to another pipe.
     *
     * <p>
     * This method is for {@link Tube} implementations, not for users.
     *
     * <p>
     * If the given tube is already copied for this cloning episode,
     * this method simply returns that reference. Otherwise it copies
     * a tube, make a note, and returns a copied tube. This additional
     * step ensures that a graph is cloned isomorphically correctly.
     *
     * <p>
     * (Think about what happens when a graph is A->B, A->C, B->D, and C->D
     * if you don't have this step.)
     *
     * @param t
     *      The tube to be copied.
     * @return
     *      The cloned tube. Always non-null.
     */
    public <P extends Tube> P copy(P t) {
        Tube r = (Tube)master2copy.get(t);
        if(r==null) {
            r = t.copy(this);
            // the pipe must puts its copy to the map by itself
            assert master2copy.get(t)==r : "the tube must call the add(...) method to register itself before start copying other pipes, but "+t +" hasn't done so";
        }
        return (P)r;
    }

    /**
     * This method must be called from within the copy constructor
     * to notify that the copy was created.
     *
     * <p>
     * When your pipe has references to other pipes,
     * it's particularly important to call this method
     * before you start copying the pipes you refer to,
     * or else there's a chance of inifinite loop.
     */
    public void add(Tube original, Tube copy) {
        assert !master2copy.containsKey(original);
        assert original!=null && copy!=null;
        master2copy.put(original,copy);
    }
}
