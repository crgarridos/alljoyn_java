/**
 * Copyright 2013, Qualcomm Innovation Center, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.alljoyn.bus;

import junit.framework.TestCase;

public class ObjectSecurityTest extends TestCase{
    public ObjectSecurityTest(String name) {
        super(name);
    }

    static {
        System.loadLibrary("alljoyn_java");
    }

    public class InsecureService implements InsecureInterface, BusObject {
        public String InsecurePing(String inStr) throws BusException { return inStr; }
    }

    public class SecureOffService implements SecureOffInterface, BusObject {
        public String Ping(String str) throws BusException { return str; }
    }

    public class SrpAuthListener implements AuthListener {
        public boolean requestPasswordFlag;
        public boolean completedFlag;

        public SrpAuthListener() {
            requestPasswordFlag = false;
            completedFlag = false;
        }

        public boolean requested(String mechanism, String authPeer, int count, String userName,
                AuthRequest[] requests) {
            assertTrue("ALLJOYN_SRP_KEYX".equals(mechanism));
            for(AuthRequest request : requests){
                if(request instanceof PasswordRequest) {
                    ((PasswordRequest) request).setPassword("123456".toCharArray());
                    requestPasswordFlag = true;
                }
            }
            return true;
        }

        public void completed(String mechanism, String authPeer, boolean authenticated) {
            assertTrue("ALLJOYN_SRP_KEYX".equals(mechanism));
            assertTrue(authenticated);
            completedFlag = authenticated;
        }
    }


    BusAttachment serviceBus;
    BusAttachment clientBus;
    SrpAuthListener serviceAuthListener;
    SrpAuthListener clientAuthListener;

    //static private String INTERFACE_NAME = "org.alljoyn.test.objectSecurity.interface";
    //static private String WELLKNOWN_NAME = "org.alljoyn.test.objectSecurity";
    static private String OBJECT_PATH    = "/org/alljoyn/test/objectSecurity";


    public void setUp() throws Exception {
        serviceBus = new BusAttachment("ObjectSecurityTestClient");
        serviceAuthListener = new SrpAuthListener();
        assertEquals(Status.OK, serviceBus.registerAuthListener("ALLJOYN_SRP_KEYX", serviceAuthListener));
        assertEquals(Status.OK, serviceBus.connect());
        serviceBus.clearKeyStore();

        clientBus = new BusAttachment("ObjectSecurityTestService");
        clientAuthListener = new SrpAuthListener();
        assertEquals(Status.OK, clientBus.registerAuthListener("ALLJOYN_SRP_KEYX", clientAuthListener));
        assertEquals(Status.OK, clientBus.connect());
        clientBus.clearKeyStore();
    }

    public void tearDown() throws Exception {
        if (serviceBus != null) {
            serviceBus.disconnect();
            serviceBus.release();
            serviceBus = null;
        }

        if (clientBus != null) {
            clientBus.disconnect();
            clientBus.release();
            clientBus = null;
        }
    }

    /*
     * This test takes an interface that has no security and make it secure
     * using object based security.
     */
    public void testInsecureInterfaceSecureObject1() {
        InsecureService insecureService = new InsecureService();
        assertEquals(Status.OK, serviceBus.registerBusObject(insecureService, OBJECT_PATH, true));

        ProxyBusObject proxy = new ProxyBusObject(clientBus, serviceBus.getUniqueName(), OBJECT_PATH, BusAttachment.SESSION_ID_ANY, new Class<?>[] {InsecureInterface.class}, true);

        InsecureInterface ifac = proxy.getInterface(InsecureInterface.class);

        try {
            assertEquals("alljoyn", ifac.InsecurePing("alljoyn"));
        } catch (BusException e) {
            e.printStackTrace();
            // we don't expect to have a BusException if we have on fail
            assertTrue(false);
        }
        assertTrue(proxy.isSecure());
        assertTrue(serviceBus.isBusObjectSecure(insecureService));

        assertTrue(serviceAuthListener.requestPasswordFlag);
        assertTrue(clientAuthListener.requestPasswordFlag);
        assertTrue(serviceAuthListener.completedFlag);
        assertTrue(clientAuthListener.completedFlag);
    }

    /*
     * This test takes an interface that has no security and make it secure
     * using object based security.
     */
    public void testInsecureInterfaceSecureObject2() {
        InsecureService insecureService = new InsecureService();
        assertEquals(Status.OK, serviceBus.registerBusObject(insecureService, OBJECT_PATH, true));

        ProxyBusObject proxy = clientBus.getProxyBusObject(serviceBus.getUniqueName(), OBJECT_PATH, BusAttachment.SESSION_ID_ANY, new Class<?>[] {InsecureInterface.class}, true);

        InsecureInterface ifac = proxy.getInterface(InsecureInterface.class);

        try {
            assertEquals("alljoyn", ifac.InsecurePing("alljoyn"));
        } catch (BusException e) {
            e.printStackTrace();
            // we don't expect to have a BusException if we have on fail
            assertTrue(false);
        }
        assertTrue(proxy.isSecure());
        assertTrue(serviceBus.isBusObjectSecure(insecureService));

        assertTrue(serviceAuthListener.requestPasswordFlag);
        assertTrue(clientAuthListener.requestPasswordFlag);
        assertTrue(serviceAuthListener.completedFlag);
        assertTrue(clientAuthListener.completedFlag);
    }

    /*
     * This test takes an interface that has security == off and tries to make
     * the object secure using object based security. Since the interface
     * explicitly states that the interface security is off it should not
     * attempt to use authentication when making a method call.
     */
    public void testSecureOffInterfaceSecureObject() {
        SecureOffService secureOffService = new SecureOffService();
        assertEquals(Status.OK, serviceBus.registerBusObject(secureOffService, OBJECT_PATH, true));

        ProxyBusObject proxy = clientBus.getProxyBusObject(serviceBus.getUniqueName(), OBJECT_PATH, BusAttachment.SESSION_ID_ANY, new Class<?>[] {SecureOffInterface.class}, true);

        SecureOffInterface ifac = proxy.getInterface(SecureOffInterface.class);

        try {
            assertEquals("alljoyn", ifac.Ping("alljoyn"));
        } catch (BusException e) {
            e.printStackTrace();
            // we don't expect to have a BusException if we have on fail
            assertTrue(false);
        }
        /*
         * security was stated as true when proxyBusObject was created
         */
        assertTrue(proxy.isSecure());
        assertTrue(serviceBus.isBusObjectSecure(secureOffService));

        /*
         * authlistener should not have been called because interface security
         * annotation is @security(value='off')
         */
        assertFalse(serviceAuthListener.requestPasswordFlag);
        assertFalse(clientAuthListener.requestPasswordFlag);
        assertFalse(serviceAuthListener.completedFlag);
        assertFalse(clientAuthListener.completedFlag);
    }

    // TODO add tests that verify inherit security value.
}
