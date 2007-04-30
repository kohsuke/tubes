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
 * Copyright 2006 Sun Microsystems Inc. All Rights Reserved
 */
package com.sun.tubes;

import java.io.IOException;

import junit.framework.TestCase;
import junit.textui.TestRunner;


/**
 * @author Pete Hendry
 */
public class TubesUnitTest
    extends TestCase {

    public static void main( final String[] args ) {
        TestRunner.main(new String[]{
            "-noloading", TubesUnitTest.class.getName()
        });
    }


    public TubesUnitTest( final String name ) {
        super(name);
    }


    public void setUp()
        throws Exception {

    }


    public void tearDown() {
    }


    public void testSimpleSingleTube()
        throws Exception {

        final SimpleTube<String> tube = createSimpleTubeline(1);

        final Engine<String> engine = new Engine<String>("singleTubeCompletes");

        // test that request method is called only (tube calling returnWith does not have
        // its response or exception methods called.
        String result = runTubelineSync(engine, tube, "Howdy");
        assertEquals("Howdy", result);
        assertEquals(0, tube.copyCount);
        assertEquals(1, tube.requestCount);
        assertEquals(0, tube.responseCount);
        assertEquals(0, tube.exceptionCount);

        // run on copy of tubeline and ensure doesn't affect original
        final SimpleTube<String> copy = TubeCloner.clone(tube);
        result = runTubelineSync(engine, copy, "Howdy");
        assertEquals("Howdy", result);
        assertEquals(1, tube.copyCount);
        assertEquals(1, tube.requestCount);
        assertEquals(0, tube.responseCount);
        assertEquals(0, tube.exceptionCount);
        assertEquals(0, copy.copyCount);
        assertEquals(1, copy.requestCount);
        assertEquals(0, copy.responseCount);
        assertEquals(0, copy.exceptionCount);
    }


    public void testSimpleMultiTube()
        throws Exception {

        final SimpleTube<String> one = createSimpleTubeline(3);
        assertNotNull(one);
        final SimpleTube<String> two = (SimpleTube<String>)one.getNext();
        assertNotNull(two);
        final SimpleTube<String> three = (SimpleTube<String>)two.getNext();
        assertNotNull(three);
        assertNull(three.getNext());

        final Engine<String> engine = new Engine<String>("multiTubeCompletes");

        final String result = runTubelineSync(engine, one, "Howdy");
        assertEquals("Howdy", result);

        // each tube should have had its request and response methods called once except the
        // final tube (three) which won't have its response method called.
        assertEquals(0, one.copyCount);
        assertEquals(1, one.requestCount);
        assertEquals(1, one.responseCount);
        assertEquals(0, one.exceptionCount);
        assertEquals(0, two.copyCount);
        assertEquals(1, two.requestCount);
        assertEquals(1, two.responseCount);
        assertEquals(0, two.exceptionCount);
        assertEquals(0, three.copyCount);
        assertEquals(1, three.requestCount);
        assertEquals(0, three.responseCount);
        assertEquals(0, three.exceptionCount);

        final SimpleTube<String> oneCopy = TubeCloner.clone(one);
        assertNotNull(oneCopy);
        assertNotSame(one, oneCopy);

        final SimpleTube<String> twoCopy = (SimpleTube<String>)oneCopy.getNext();
        assertNotNull(twoCopy);
        assertNotSame(two, twoCopy);

        final SimpleTube<String> threeCopy = (SimpleTube<String>)twoCopy.getNext();
        assertNotNull(threeCopy);
        assertNotSame(three, threeCopy);

        assertNull(threeCopy.getNext());
    }


    public void testProcessException()
        throws Exception {

        // create a Tubeline that throws an exception in the 2nd element and so does not reach the
        // 3rd. The exception is then converted back to a String by the first Tube.

        final SimpleTube<String> three = new SimpleTube<String>(null);
        final SimpleTube<String> two = new SimpleTube<String>(three) {
            public NextAction<String> processRequest( String request ) {
                ++requestCount;
                return throwAction(new IOException(request));
            }
        };
        final SimpleTube<String> one = new SimpleTube<String>(two) {

            public NextAction<String> processException( Throwable t ) {
                ++exceptionCount;
                // adjust for following call
                --responseCount;
                return processResponse("EXCEPTION");
            }
        };

        final Engine<String> engine = new Engine<String>("testProcessException");
        final String result = runTubelineSync(engine, one, "Howdy");

        assertEquals("EXCEPTION", result);
        assertEquals(0, three.requestCount);
        assertEquals(0, three.responseCount);
        assertEquals(0, three.exceptionCount);
        assertEquals(1, two.requestCount);
        assertEquals(0, two.responseCount);
        assertEquals(0, two.exceptionCount);
        assertEquals(1, one.requestCount);
        assertEquals(0, one.responseCount);
        assertEquals(1, one.exceptionCount);
    }


    public void testDirectionChange()
        throws Exception {

        // here we create a loop over the 3 Tubes. The first Tube takes a response and causes
        // it to go back as the request resulting in 3 iterations.

        final SimpleTube<String> two = createSimpleTubeline(2);
        final SimpleTube<String> three = (SimpleTube<String>)two.getNext();
        assertNotNull(three);
        final SimpleTube<String> one = new SimpleTube<String>(two) {
            int count;

            public NextAction<String> processResponse( String response ) {
                ++responseCount;
                if ( ++count >= 3 ) {
                    return returnWithAction(response);
                }

                return invokeAction(next, response);
            }
        };

        final Engine<String> engine = new Engine<String>("testDirectionChange");
        final String result = runTubelineSync(engine, one, "Howdy");
        assertEquals("Howdy", result);

        // so the request should have been processed once by one.processRequest then by two and three.
        // On return it will be sent back into two and three twice more.

        assertEquals(1, one.requestCount);
        assertEquals(3, two.requestCount);
        assertEquals(3, three.requestCount);
        assertEquals(3, one.responseCount);
        assertEquals(3, two.responseCount);
        assertEquals(0, three.responseCount);
    }


    private <P> P runTubelineSync( Engine<P> engine, Tube<P> tube, P packet ) {
        final Fiber<P> fiber = engine.createFiber();

        return fiber.runSync(tube, packet);
    }


    private <T> SimpleTube<T> createSimpleTubeline( int count ) {
        SimpleTube<T> head = new SimpleTube<T>(null);
        for( int i = count; --i > 0; ) {
            head = new SimpleTube<T>(head);
        }

        return head;
    }
}
