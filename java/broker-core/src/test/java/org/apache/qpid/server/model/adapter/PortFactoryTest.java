/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.model.adapter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.test.utils.QpidTestCase;

public class PortFactoryTest extends QpidTestCase
{
    private UUID _portId = UUID.randomUUID();
    private int _portNumber = 123;
    private Set<String> _tcpStringSet = Collections.singleton(Transport.TCP.name());
    private Set<Transport> _tcpTransportSet = Collections.singleton(Transport.TCP);
    private Set<String> _sslStringSet = Collections.singleton(Transport.SSL.name());
    private Set<Transport> _sslTransportSet = Collections.singleton(Transport.SSL);

    private Map<String, Object> _attributes = new HashMap<String, Object>();

    private Broker _broker = mock(Broker.class);
    private KeyStore _keyStore = mock(KeyStore.class);
    private TrustStore _trustStore = mock(TrustStore.class);
    private String _authProviderName = "authProvider";
    private AuthenticationProvider _authProvider = mock(AuthenticationProvider.class);
    private PortFactory _portFactory;

    @Override
    protected void setUp() throws Exception
    {
        when(_broker.findAuthenticationProviderByName(_authProviderName)).thenReturn(_authProvider);

        setTestSystemProperty(BrokerProperties.PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_EXCLUDES, null);
        setTestSystemProperty(BrokerProperties.PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_INCLUDES, null);
        _portFactory = new PortFactory();

        _attributes.put(Port.PORT, _portNumber);
        _attributes.put(Port.TRANSPORTS, _tcpStringSet);
        _attributes.put(Port.AUTHENTICATION_PROVIDER, _authProviderName);
        _attributes.put(Port.TCP_NO_DELAY, "true");
        _attributes.put(Port.RECEIVE_BUFFER_SIZE, "1");
        _attributes.put(Port.SEND_BUFFER_SIZE, "2");
        _attributes.put(Port.BINDING_ADDRESS, "127.0.0.1");
    }

    public void testDefaultProtocols()
    {
        Collection<Protocol> protocols = _portFactory.getDefaultProtocols();
        EnumSet<Protocol> expected = EnumSet.of(Protocol.AMQP_0_8, Protocol.AMQP_0_9, Protocol.AMQP_0_9_1, Protocol.AMQP_0_10,
                Protocol.AMQP_1_0);
        assertEquals("Unexpected protocols", new HashSet<Protocol>(expected), new HashSet<Protocol>(protocols));
    }

    public void testDefaultProtocolsWhenProtocolExcludeSystemPropertyIsSet()
    {
        setTestSystemProperty(BrokerProperties.PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_EXCLUDES, Protocol.AMQP_1_0.name() + ","
                + Protocol.AMQP_0_10.name());
        _portFactory = new PortFactory();
        Collection<Protocol> protocols = _portFactory.getDefaultProtocols();
        EnumSet<Protocol> expected = EnumSet.of(Protocol.AMQP_0_8, Protocol.AMQP_0_9, Protocol.AMQP_0_9_1);
        assertEquals("Unexpected protocols", new HashSet<Protocol>(expected), new HashSet<Protocol>(protocols));
    }

    public void testDefaultProtocolsWhenProtocolIncludeSystemPropertyIsSet()
    {
        setTestSystemProperty(BrokerProperties.PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_EXCLUDES, Protocol.AMQP_1_0.name() + ","
                + Protocol.AMQP_0_10.name() + "," + Protocol.AMQP_0_9_1.name());
        setTestSystemProperty(BrokerProperties.PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_INCLUDES, Protocol.AMQP_0_10.name() + ","
                + Protocol.AMQP_0_9_1.name());
        _portFactory = new PortFactory();
        Collection<Protocol> protocols = _portFactory.getDefaultProtocols();
        EnumSet<Protocol> expected = EnumSet.of(Protocol.AMQP_0_8, Protocol.AMQP_0_9, Protocol.AMQP_0_9_1, Protocol.AMQP_0_10);
        assertEquals("Unexpected protocols", new HashSet<Protocol>(expected), new HashSet<Protocol>(protocols));
    }

    public void testCreatePortWithMinimumAttributes()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PORT, 1);
        attributes.put(Port.AUTHENTICATION_PROVIDER, _authProviderName);
        Port port = _portFactory.createPort(_portId, _broker, attributes);

        assertNotNull(port);
        assertTrue(port instanceof AmqpPortAdapter);
        assertEquals("Unexpected port", 1, port.getPort());
        assertEquals("Unexpected transports", Collections.singleton(PortFactory.DEFAULT_TRANSPORT), port.getTransports());
        assertEquals("Unexpected protocols", _portFactory.getDefaultProtocols(), port.getProtocols());
        assertEquals("Unexpected send buffer size", PortFactory.DEFAULT_AMQP_SEND_BUFFER_SIZE,
                port.getAttribute(Port.SEND_BUFFER_SIZE));
        assertEquals("Unexpected receive buffer size", PortFactory.DEFAULT_AMQP_RECEIVE_BUFFER_SIZE,
                port.getAttribute(Port.RECEIVE_BUFFER_SIZE));
        assertEquals("Unexpected need client auth", PortFactory.DEFAULT_AMQP_NEED_CLIENT_AUTH,
                port.getAttribute(Port.NEED_CLIENT_AUTH));
        assertEquals("Unexpected want client auth", PortFactory.DEFAULT_AMQP_WANT_CLIENT_AUTH,
                port.getAttribute(Port.WANT_CLIENT_AUTH));
        assertEquals("Unexpected tcp no delay", PortFactory.DEFAULT_AMQP_TCP_NO_DELAY, port.getAttribute(Port.TCP_NO_DELAY));
        assertEquals("Unexpected binding", PortFactory.DEFAULT_AMQP_BINDING, port.getAttribute(Port.BINDING_ADDRESS));
    }

    public void testCreateAmqpPort()
    {
        createAmqpPortTestImpl(false, false, false, null, null);
    }

    public void testCreateAmqpPortUsingSslFailsWithoutKeyStore()
    {
        try
        {
            createAmqpPortTestImpl(true, false, false, null, null);
            fail("expected exception due to lack of SSL keystore");
        }
        catch(IllegalConfigurationException e)
        {
            //expected
        }
    }

    public void testCreateAmqpPortUsingSslSucceedsWithKeyStore()
    {
        String keyStoreName = "myKeyStore";
        when(_broker.findKeyStoreByName(keyStoreName)).thenReturn(_keyStore);

        createAmqpPortTestImpl(true, false, false, keyStoreName, null);
    }

    public void testCreateAmqpPortNeedingClientAuthFailsWithoutTrustStore()
    {
        String keyStoreName = "myKeyStore";
        when(_broker.findKeyStoreByName(keyStoreName)).thenReturn(_keyStore);

        when(_broker.findTrustStoreByName(any(String.class))).thenReturn(null);

        try
        {
            createAmqpPortTestImpl(true, true, false, keyStoreName, null);
            fail("expected exception due to lack of SSL truststore");
        }
        catch(IllegalConfigurationException e)
        {
            //expected
        }
    }

    public void testCreateAmqpPortNeedingClientAuthSucceedsWithTrustStore()
    {
        String keyStoreName = "myKeyStore";
        when(_broker.findKeyStoreByName(keyStoreName)).thenReturn(_keyStore);

        String trustStoreName = "myTrustStore";
        when(_broker.findTrustStoreByName(trustStoreName)).thenReturn(_trustStore);

        createAmqpPortTestImpl(true, true, false, keyStoreName, new String[]{trustStoreName});
    }

    public void testCreateAmqpPortWantingClientAuthFailsWithoutTrustStore()
    {
        String keyStoreName = "myKeyStore";
        when(_broker.findKeyStoreByName(keyStoreName)).thenReturn(_keyStore);

        try
        {
            createAmqpPortTestImpl(true, false, true, keyStoreName, null);
            fail("expected exception due to lack of SSL truststore");
        }
        catch(IllegalConfigurationException e)
        {
            //expected
        }
    }

    public void testCreateAmqpPortWantingClientAuthSucceedsWithTrustStore()
    {
        String keyStoreName = "myKeyStore";
        when(_broker.findKeyStoreByName(keyStoreName)).thenReturn(_keyStore);

        String trustStoreName = "myTrustStore";
        when(_broker.findTrustStoreByName(trustStoreName)).thenReturn(_trustStore);

        createAmqpPortTestImpl(true, false, true, keyStoreName, new String[]{trustStoreName});
    }

    public void createAmqpPortTestImpl(boolean useSslTransport, boolean needClientAuth, boolean wantClientAuth,
                                       String keystoreName, String[] trustStoreNames)
    {
        Set<Protocol> amqp010ProtocolSet = Collections.singleton(Protocol.AMQP_0_10);
        Set<String> amqp010StringSet = Collections.singleton(Protocol.AMQP_0_10.name());
        _attributes.put(Port.PROTOCOLS, amqp010StringSet);

        if(useSslTransport)
        {
            _attributes.put(Port.TRANSPORTS, _sslStringSet);
        }

        if(needClientAuth)
        {
            _attributes.put(Port.NEED_CLIENT_AUTH, "true");
        }

        if(wantClientAuth)
        {
            _attributes.put(Port.WANT_CLIENT_AUTH, "true");
        }

        if(keystoreName != null)
        {
            _attributes.put(Port.KEY_STORE, keystoreName);
        }

        if(trustStoreNames != null)
        {
            _attributes.put(Port.TRUST_STORES, Arrays.asList(trustStoreNames));
        }

        Port port = _portFactory.createPort(_portId, _broker, _attributes);

        assertNotNull(port);
        assertTrue(port instanceof AmqpPortAdapter);
        assertEquals(_portId, port.getId());
        assertEquals(_portNumber, port.getPort());
        if(useSslTransport)
        {
            assertEquals(_sslTransportSet, port.getTransports());
        }
        else
        {
            assertEquals(_tcpTransportSet, port.getTransports());
        }
        assertEquals(amqp010ProtocolSet, port.getProtocols());
        assertEquals("Unexpected send buffer size", 2, port.getAttribute(Port.SEND_BUFFER_SIZE));
        assertEquals("Unexpected receive buffer size", 1, port.getAttribute(Port.RECEIVE_BUFFER_SIZE));
        assertEquals("Unexpected need client auth", needClientAuth, port.getAttribute(Port.NEED_CLIENT_AUTH));
        assertEquals("Unexpected want client auth", wantClientAuth, port.getAttribute(Port.WANT_CLIENT_AUTH));
        assertEquals("Unexpected tcp no delay", true, port.getAttribute(Port.TCP_NO_DELAY));
        assertEquals("Unexpected binding", "127.0.0.1", port.getAttribute(Port.BINDING_ADDRESS));
    }

    public void testCreateNonAmqpPort()
    {
        Set<Protocol> nonAmqpProtocolSet = Collections.singleton(Protocol.JMX_RMI);
        Set<String> nonAmqpStringSet = Collections.singleton(Protocol.JMX_RMI.name());
        _attributes = new HashMap<String, Object>();
        _attributes.put(Port.PROTOCOLS, nonAmqpStringSet);
        _attributes.put(Port.AUTHENTICATION_PROVIDER, _authProviderName);
        _attributes.put(Port.PORT, _portNumber);
        _attributes.put(Port.TRANSPORTS, _tcpStringSet);

        Port port = _portFactory.createPort(_portId, _broker, _attributes);

        assertNotNull(port);
        assertFalse("Port should be a PortAdapter, not its AMQP-specific subclass", port instanceof AmqpPortAdapter);
        assertEquals(_portId, port.getId());
        assertEquals(_portNumber, port.getPort());
        assertEquals(_tcpTransportSet, port.getTransports());
        assertEquals(nonAmqpProtocolSet, port.getProtocols());
        assertNull("Unexpected send buffer size", port.getAttribute(Port.SEND_BUFFER_SIZE));
        assertNull("Unexpected receive buffer size", port.getAttribute(Port.RECEIVE_BUFFER_SIZE));
        assertNull("Unexpected need client auth", port.getAttribute(Port.NEED_CLIENT_AUTH));
        assertNull("Unexpected want client auth", port.getAttribute(Port.WANT_CLIENT_AUTH));
        assertNull("Unexpected tcp no delay", port.getAttribute(Port.TCP_NO_DELAY));
        assertNull("Unexpected binding", port.getAttribute(Port.BINDING_ADDRESS));
    }

    public void testCreateNonAmqpPortWithPartiallySetAttributes()
    {
        Set<Protocol> nonAmqpProtocolSet = Collections.singleton(Protocol.JMX_RMI);
        Set<String> nonAmqpStringSet = Collections.singleton(Protocol.JMX_RMI.name());
        _attributes = new HashMap<String, Object>();
        _attributes.put(Port.PROTOCOLS, nonAmqpStringSet);
        _attributes.put(Port.AUTHENTICATION_PROVIDER, _authProviderName);
        _attributes.put(Port.PORT, _portNumber);

        Port port = _portFactory.createPort(_portId, _broker, _attributes);

        assertNotNull(port);
        assertFalse("Port should be a PortAdapter, not its AMQP-specific subclass", port instanceof AmqpPortAdapter);
        assertEquals(_portId, port.getId());
        assertEquals(_portNumber, port.getPort());
        assertEquals(Collections.singleton(PortFactory.DEFAULT_TRANSPORT), port.getTransports());
        assertEquals(nonAmqpProtocolSet, port.getProtocols());
        assertNull("Unexpected send buffer size", port.getAttribute(Port.SEND_BUFFER_SIZE));
        assertNull("Unexpected receive buffer size", port.getAttribute(Port.RECEIVE_BUFFER_SIZE));
        assertNull("Unexpected need client auth", port.getAttribute(Port.NEED_CLIENT_AUTH));
        assertNull("Unexpected want client auth", port.getAttribute(Port.WANT_CLIENT_AUTH));
        assertNull("Unexpected tcp no delay", port.getAttribute(Port.TCP_NO_DELAY));
        assertNull("Unexpected binding", port.getAttribute(Port.BINDING_ADDRESS));
    }

    public void testCreateMixedAmqpAndNonAmqpThrowsException()
    {
        Set<String> mixedProtocolSet = new HashSet<String>(Arrays.asList(Protocol.AMQP_0_10.name(), Protocol.JMX_RMI.name()));
        _attributes.put(Port.PROTOCOLS, mixedProtocolSet);

        try
        {
            _portFactory.createPort(_portId, _broker, _attributes);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testCreateRMIPortWhenAnotherRMIPortAlreadyExists()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PORT, 1);
        attributes.put(Port.NAME, getTestName());
        attributes.put(Port.TRANSPORTS, Collections.singleton(Transport.TCP));
        attributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.RMI));

        Port rmiPort = mock(Port.class);
        when(rmiPort.getProtocols()).thenReturn(Collections.singleton(Protocol.RMI));
        when(_broker.getPorts()).thenReturn(Collections.singletonList(rmiPort));

        try
        {
            _portFactory.createPort(_portId, _broker, attributes);
            fail("RMI port creation should fail as another one already exist");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testCreateRMIPortRequestingSslFails()
    {
        String keyStoreName = "myKeyStore";

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PORT, 1);
        attributes.put(Port.NAME, getTestName());
        attributes.put(Port.TRANSPORTS, Collections.singleton(Transport.SSL));
        attributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.RMI));
        _attributes.put(Port.KEY_STORE, keyStoreName);

        when(_broker.findKeyStoreByName(keyStoreName)).thenReturn(_keyStore);

        try
        {
            _portFactory.createPort(_portId, _broker, attributes);
            fail("RMI port creation should fail due to requesting SSL");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }
}
