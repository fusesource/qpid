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
package org.apache.qpid.client;

import org.apache.qpid.AMQConnectionFailureException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUndeliveredException;
import org.apache.qpid.AMQUnresolvedAddressException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverProtectedOperation;
import org.apache.qpid.client.failover.FailoverRetrySupport;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.*;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ChannelLimitReachedException;
import org.apache.qpid.jms.Connection;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.jms.FailoverPolicy;
import org.apache.qpid.url.URLSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AMQConnection extends Closeable implements Connection, QueueConnection, TopicConnection, Referenceable
{
    private static final Logger _logger = LoggerFactory.getLogger(AMQConnection.class);

    private AtomicInteger _idFactory = new AtomicInteger(0);

    /**
     * This is the "root" mutex that must be held when doing anything that could be impacted by failover. This must be
     * held by any child objects of this connection such as the session, producers and consumers.
     */
    private final Object _failoverMutex = new Object();

    private final Object _sessionCreationLock = new Object();

    /**
     * A channel is roughly analogous to a session. The server can negotiate the maximum number of channels per session
     * and we must prevent the client from opening too many. Zero means unlimited.
     */
    private long _maximumChannelCount;

    /** The maximum size of frame supported by the server */
    private long _maximumFrameSize;

    /**
     * The protocol handler dispatches protocol events for this connection. For example, when the connection is dropped
     * the handler deals with this. It also deals with the initial dispatch of any protocol frames to their appropriate
     * handler.
     */
    private AMQProtocolHandler _protocolHandler;

    /** Maps from session id (Integer) to AMQSession instance */
    private final Map<Integer, AMQSession> _sessions = new LinkedHashMap<Integer, AMQSession>();

    private String _clientName;

    /** The user name to use for authentication */
    private String _username;

    /** The password to use for authentication */
    private String _password;

    /** The virtual path to connect to on the AMQ server */
    private String _virtualHost;

    private ExceptionListener _exceptionListener;

    private ConnectionListener _connectionListener;

    private ConnectionURL _connectionURL;

    /**
     * Whether this connection is started, i.e. whether messages are flowing to consumers. It has no meaning for message
     * publication.
     */
    private boolean _started;

    /** Policy dictating how to failover */
    private FailoverPolicy _failoverPolicy;

    /*
     * _Connected should be refactored with a suitable wait object.
     */
    private boolean _connected;

    /*
     * The last error code that occured on the connection. Used to return the correct exception to the client
     */
    private AMQException _lastAMQException = null;

    /*
     * The connection meta data
     */
    private QpidConnectionMetaData _connectionMetaData;

    /** Configuration info for SSL */
    private SSLConfiguration _sslConfiguration;

    private AMQShortString _defaultTopicExchangeName = ExchangeDefaults.TOPIC_EXCHANGE_NAME;
    private AMQShortString _defaultQueueExchangeName = ExchangeDefaults.DIRECT_EXCHANGE_NAME;
    private AMQShortString _temporaryTopicExchangeName = ExchangeDefaults.TOPIC_EXCHANGE_NAME;
    private AMQShortString _temporaryQueueExchangeName = ExchangeDefaults.DIRECT_EXCHANGE_NAME;

    /** Thread Pool for executing connection level processes. Such as returning bounced messages. */
    private final ExecutorService _taskPool = Executors.newCachedThreadPool();
    private static final long DEFAULT_TIMEOUT = 1000 * 30;
    private ProtocolVersion _protocolVersion;


    /**
     * @param broker      brokerdetails
     * @param username    username
     * @param password    password
     * @param clientName  clientid
     * @param virtualHost virtualhost
     *
     * @throws AMQException
     * @throws URLSyntaxException
     */
    public AMQConnection(String broker, String username, String password, String clientName, String virtualHost)
            throws AMQException, URLSyntaxException
    {
        this(new AMQConnectionURL(
                ConnectionURL.AMQ_PROTOCOL + "://" + username + ":" + password + "@"
                + ((clientName == null) ? "" : clientName) + "/" + virtualHost + "?brokerlist='"
                + AMQBrokerDetails.checkTransport(broker) + "'"), null);
    }

    /**
     * @param broker      brokerdetails
     * @param username    username
     * @param password    password
     * @param clientName  clientid
     * @param virtualHost virtualhost
     *
     * @throws AMQException
     * @throws URLSyntaxException
     */
    public AMQConnection(String broker, String username, String password, String clientName, String virtualHost,
                         SSLConfiguration sslConfig) throws AMQException, URLSyntaxException
    {
        this(new AMQConnectionURL(
                ConnectionURL.AMQ_PROTOCOL + "://" + username + ":" + password + "@"
                + ((clientName == null) ? "" : clientName) + "/" + virtualHost + "?brokerlist='"
                + AMQBrokerDetails.checkTransport(broker) + "'"), sslConfig);
    }

    public AMQConnection(String host, int port, String username, String password, String clientName, String virtualHost)
            throws AMQException, URLSyntaxException
    {
        this(host, port, false, username, password, clientName, virtualHost, null);
    }

    public AMQConnection(String host, int port, String username, String password, String clientName, String virtualHost,
                         SSLConfiguration sslConfig) throws AMQException, URLSyntaxException
    {
        this(host, port, false, username, password, clientName, virtualHost, sslConfig);
    }

    public AMQConnection(String host, int port, boolean useSSL, String username, String password, String clientName,
                         String virtualHost, SSLConfiguration sslConfig) throws AMQException, URLSyntaxException
    {
        this(new AMQConnectionURL(
                useSSL
                ? (ConnectionURL.AMQ_PROTOCOL + "://" + username + ":" + password + "@"
                   + ((clientName == null) ? "" : clientName) + virtualHost + "?brokerlist='tcp://" + host + ":" + port
                   + "'" + "," + ConnectionURL.OPTIONS_SSL + "='true'")
                : (ConnectionURL.AMQ_PROTOCOL + "://" + username + ":" + password + "@"
                   + ((clientName == null) ? "" : clientName) + virtualHost + "?brokerlist='tcp://" + host + ":" + port
                   + "'" + "," + ConnectionURL.OPTIONS_SSL + "='false'")), sslConfig);
    }

    public AMQConnection(String connection) throws AMQException, URLSyntaxException
    {
        this(new AMQConnectionURL(connection), null);
    }

    public AMQConnection(String connection, SSLConfiguration sslConfig) throws AMQException, URLSyntaxException
    {
        this(new AMQConnectionURL(connection), sslConfig);
    }

    public AMQConnection(ConnectionURL connectionURL, SSLConfiguration sslConfig) throws AMQException
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Connection:" + connectionURL);
        }

        _sslConfiguration = sslConfig;
        if (connectionURL == null)
        {
            throw new IllegalArgumentException("Connection must be specified");
        }

        _connectionURL = connectionURL;

        _clientName = connectionURL.getClientName();
        _username = connectionURL.getUsername();
        _password = connectionURL.getPassword();

        _protocolVersion = connectionURL.getProtocolVersion();

        setVirtualHost(connectionURL.getVirtualHost());

        if (connectionURL.getDefaultQueueExchangeName() != null)
        {
            _defaultQueueExchangeName = connectionURL.getDefaultQueueExchangeName();
        }

        if (connectionURL.getDefaultTopicExchangeName() != null)
        {
            _defaultTopicExchangeName = connectionURL.getDefaultTopicExchangeName();
        }

        if (connectionURL.getTemporaryQueueExchangeName() != null)
        {
            _temporaryQueueExchangeName = connectionURL.getTemporaryQueueExchangeName();
        }

        if (connectionURL.getTemporaryTopicExchangeName() != null)
        {
            _temporaryTopicExchangeName = connectionURL.getTemporaryTopicExchangeName();
        }

        _failoverPolicy = new FailoverPolicy(connectionURL);

        _protocolHandler = new AMQProtocolHandler(this);

        // We are not currently connected
        _connected = false;

        Exception lastException = new Exception();
        lastException.initCause(new ConnectException());

        while (!_connected && _failoverPolicy.failoverAllowed())
        {
            try
            {
                makeBrokerConnection(_failoverPolicy.getNextBrokerDetails());
                lastException = null;
                _connected = true;
            }
            catch (Exception e)
            {
                lastException = e;

                //We need to change protocol handler here as an error during the connect will not
                // cause the StateManager to be replaced. So the state is out of sync on reconnect
                // This can be seen when a exception occurs during connection. i.e. log4j NoSuchMethod. (using < 1.2.12)
                _protocolHandler.setStateManager(new AMQStateManager());

                if (_logger.isInfoEnabled())
                {
                    _logger.info("Unable to connect to broker at " + _failoverPolicy.getCurrentBrokerDetails(),
                                 e.getCause());
                }
            }
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Are we connected:" + _connected);
        }

        if (!_connected)
        {
            String message = null;

            if (lastException != null)
            {
                if (lastException.getCause() != null)
                {
                    message = lastException.getCause().getMessage();
                }
                else
                {
                    message = lastException.getMessage();
                }
            }

            if ((message == null) || message.equals(""))
            {
                if (message == null)
                {
                    message = "Unable to Connect";
                }
                else // can only be "" if getMessage() returned it therfore lastException != null
                {
                    message = "Unable to Connect:" + lastException.getClass();
                }
            }

            AMQException e = new AMQConnectionFailureException(message);

            if (lastException != null)
            {
                if (lastException instanceof UnresolvedAddressException)
                {
                    e = new AMQUnresolvedAddressException(message, _failoverPolicy.getCurrentBrokerDetails().toString());
                }

                e.initCause(lastException);
            }

            throw e;
        }

        _connectionMetaData = new QpidConnectionMetaData(this);
    }

    protected boolean checkException(Throwable thrown)
    {
        Throwable cause = thrown.getCause();

        if (cause == null)
        {
            cause = thrown;
        }

        return ((cause instanceof ConnectException) || (cause instanceof UnresolvedAddressException));
    }

    protected AMQConnection(String username, String password, String clientName, String virtualHost)
    {
        _clientName = clientName;
        _username = username;
        _password = password;
        setVirtualHost(virtualHost);
    }

    private void setVirtualHost(String virtualHost)
    {
        if (virtualHost.startsWith("/"))
        {
            virtualHost = virtualHost.substring(1);
        }

        _virtualHost = virtualHost;
    }

    private void makeBrokerConnection(BrokerDetails brokerDetail) throws IOException, AMQException
    {
        final Set<AMQState> openOrClosedStates =
                EnumSet.of(AMQState.CONNECTION_OPEN, AMQState.CONNECTION_CLOSED);
        try
        {

            TransportConnection.getInstance(brokerDetail).connect(_protocolHandler, brokerDetail);
             // this blocks until the connection has been set up or when an error
             // has prevented the connection being set up

            //_protocolHandler.attainState(AMQState.CONNECTION_OPEN);
            AMQState state = _protocolHandler.attainState(openOrClosedStates);
            if(state == AMQState.CONNECTION_OPEN)
            {

                _failoverPolicy.attainedConnection();

                // Again this should be changed to a suitable notify
                _connected = true;
            }
        }
        catch (AMQException e)
        {
            _lastAMQException = e;
            throw e;
        }
    }

    public boolean attemptReconnection(String host, int port)
    {
        BrokerDetails bd = new AMQBrokerDetails(host, port, _sslConfiguration);

        _failoverPolicy.setBroker(bd);

        try
        {
            makeBrokerConnection(bd);

            return true;
        }
        catch (Exception e)
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Unable to connect to broker at " + bd);
            }

            attemptReconnection();
        }

        return false;
    }

    public boolean attemptReconnection()
    {
        while (_failoverPolicy.failoverAllowed())
        {
            try
            {
                makeBrokerConnection(_failoverPolicy.getNextBrokerDetails());

                return true;
            }
            catch (Exception e)
            {
                if (!(e instanceof AMQException))
                {
                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Unable to connect to broker at " + _failoverPolicy.getCurrentBrokerDetails(), e);
                    }
                }
                else
                {
                    if (_logger.isInfoEnabled())
                    {
                        _logger.info(e.getMessage() + ":Unable to connect to broker at "
                                     + _failoverPolicy.getCurrentBrokerDetails());
                    }
                }
            }
        }

        // connection unsuccessful
        return false;
    }

    /**
     * Get the details of the currently active broker
     *
     * @return null if no broker is active (i.e. no successful connection has been made, or the BrokerDetail instance
     *         otherwise
     */
    public BrokerDetails getActiveBrokerDetails()
    {
        return _failoverPolicy.getCurrentBrokerDetails();
    }

    public boolean failoverAllowed()
    {
        if (!_connected)
        {
            return false;
        }
        else
        {
            return _failoverPolicy.failoverAllowed();
        }
    }

    public org.apache.qpid.jms.Session createSession(final boolean transacted, final int acknowledgeMode) throws JMSException
    {
        return createSession(transacted, acknowledgeMode, AMQSession.DEFAULT_PREFETCH_HIGH_MARK);
    }

    public org.apache.qpid.jms.Session createSession(final boolean transacted, final int acknowledgeMode, final int prefetch)
            throws JMSException
    {
        return createSession(transacted, acknowledgeMode, prefetch, prefetch);
    }

    public org.apache.qpid.jms.Session createSession(final boolean transacted, final int acknowledgeMode,
                                                     final int prefetchHigh, final int prefetchLow) throws JMSException
    {
        synchronized(_sessionCreationLock)
        {
        checkNotClosed();

        if (channelLimitReached())
        {
            throw new ChannelLimitReachedException(_maximumChannelCount);
        }

        return new FailoverRetrySupport<org.apache.qpid.jms.Session, JMSException>(
                new FailoverProtectedOperation<org.apache.qpid.jms.Session, JMSException>()
                {
                    public org.apache.qpid.jms.Session execute() throws JMSException, FailoverException
                    {
                        int channelId = _idFactory.incrementAndGet();

                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("Write channel open frame for channel id " + channelId);
                        }

                        // We must create the session and register it before actually sending the frame to the server to
                        // open it, so that there is no window where we could receive data on the channel and not be set
                        // up to handle it appropriately.
                        AMQSession session =
                                new AMQSession(AMQConnection.this, channelId, transacted, acknowledgeMode, prefetchHigh,
                                               prefetchLow);
                        // _protocolHandler.addSessionByChannel(channelId, session);
                        registerSession(channelId, session);

                        boolean success = false;
                        try
                        {
                            createChannelOverWire(channelId, prefetchHigh, prefetchLow, transacted);
                            success = true;
                        }
                        catch (AMQException e)
                        {
                            JMSException jmse = new JMSException("Error creating session: " + e);
                            jmse.setLinkedException(e);
                            throw jmse;
                        }
                        finally
                        {
                            if (!success)
                            {
                                deregisterSession(channelId);
                            }
                        }

                        if (_started)
                        {
                            try
                            {
                                session.start();
                            }
                            catch (AMQException e)
                            {
                                throw new JMSAMQException(e);
                            }
                        }

                        return session;
                    }
                }, this).execute();
        }
    }

    private void createChannelOverWire(int channelId, int prefetchHigh, int prefetchLow, boolean transacted)
            throws AMQException, FailoverException
    {

        ChannelOpenBody channelOpenBody = getProtocolHandler().getMethodRegistry().createChannelOpenBody(null);

        // TODO: Be aware of possible changes to parameter order as versions change.

        _protocolHandler.syncWrite(channelOpenBody.generateFrame(channelId),  ChannelOpenOkBody.class);

        BasicQosBody basicQosBody = getProtocolHandler().getMethodRegistry().createBasicQosBody(0,prefetchHigh,false);

        // todo send low water mark when protocol allows.
        // todo Be aware of possible changes to parameter order as versions change.
        _protocolHandler.syncWrite(basicQosBody.generateFrame(channelId),BasicQosOkBody.class);

        if (transacted)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Issuing TxSelect for " + channelId);
            }

            TxSelectBody body = getProtocolHandler().getMethodRegistry().createTxSelectBody();

            // TODO: Be aware of possible changes to parameter order as versions change.
            _protocolHandler.syncWrite(body.generateFrame(channelId), TxSelectOkBody.class);
        }
    }

    private void reopenChannel(int channelId, int prefetchHigh, int prefetchLow, boolean transacted)
            throws AMQException, FailoverException
    {
        try
        {
            createChannelOverWire(channelId, prefetchHigh, prefetchLow, transacted);
        }
        catch (AMQException e)
        {
            deregisterSession(channelId);
            throw new AMQException("Error reopening channel " + channelId + " after failover: " + e, e);
        }
    }

    public void setFailoverPolicy(FailoverPolicy policy)
    {
        _failoverPolicy = policy;
    }

    public FailoverPolicy getFailoverPolicy()
    {
        return _failoverPolicy;
    }

    /**
     * Returns an AMQQueueSessionAdaptor which wraps an AMQSession and throws IllegalStateExceptions where specified in
     * the JMS spec
     *
     * @param transacted
     * @param acknowledgeMode
     *
     * @return QueueSession
     *
     * @throws JMSException
     */
    public QueueSession createQueueSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        return new AMQQueueSessionAdaptor(createSession(transacted, acknowledgeMode));
    }

    /**
     * Returns an AMQTopicSessionAdapter which wraps an AMQSession and throws IllegalStateExceptions where specified in
     * the JMS spec
     *
     * @param transacted
     * @param acknowledgeMode
     *
     * @return TopicSession
     *
     * @throws JMSException
     */
    public TopicSession createTopicSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        return new AMQTopicSessionAdaptor(createSession(transacted, acknowledgeMode));
    }

    private boolean channelLimitReached()
    {
        return (_maximumChannelCount != 0) && (_sessions.size() == _maximumChannelCount);
    }

    public String getClientID() throws JMSException
    {
        checkNotClosed();

        return _clientName;
    }

    public void setClientID(String clientID) throws JMSException
    {
        checkNotClosed();
        // in AMQP it is not possible to change the client ID. If one is not specified
        // upon connection construction, an id is generated automatically. Therefore
        // we can always throw an exception.
        throw new IllegalStateException("Client name cannot be changed after being set");
    }

    public ConnectionMetaData getMetaData() throws JMSException
    {
        checkNotClosed();

        return _connectionMetaData;

    }

    public ExceptionListener getExceptionListener() throws JMSException
    {
        checkNotClosed();

        return _exceptionListener;
    }

    public void setExceptionListener(ExceptionListener listener) throws JMSException
    {
        checkNotClosed();
        _exceptionListener = listener;
    }

    /**
     * Start the connection, i.e. start flowing messages. Note that this method must be called only from a single thread
     * and is not thread safe (which is legal according to the JMS specification).
     *
     * @throws JMSException
     */
    public void start() throws JMSException
    {
        checkNotClosed();
        if (!_started)
        {
            final Iterator it = _sessions.entrySet().iterator();
            while (it.hasNext())
            {
                final AMQSession s = (AMQSession) ((Map.Entry) it.next()).getValue();
                try
                {
                    s.start();
                }
                catch (AMQException e)
                {
                    throw new JMSAMQException(e);
                }
            }

            _started = true;
        }
    }

    public void stop() throws JMSException
    {
        checkNotClosed();
        if (_started)
        {
            for (Iterator i = _sessions.values().iterator(); i.hasNext();)
            {
                try
                {
                    ((AMQSession) i.next()).stop();
                }
                catch (AMQException e)
                {
                    throw new JMSAMQException(e);
                }
            }

            _started = false;
        }
    }

    public void close() throws JMSException
    {
        close(DEFAULT_TIMEOUT);
    }

    public void close(long timeout) throws JMSException
    {
        close(new ArrayList<AMQSession>(_sessions.values()),timeout);
    }

    public void close(List<AMQSession> sessions, long timeout) throws JMSException
    {
        synchronized(_sessionCreationLock)
        {
            if(!sessions.isEmpty())
            {
                AMQSession session = sessions.remove(0);
                synchronized(session.getMessageDeliveryLock())
                {
                    close(sessions, timeout);
                }
            }
            else
            {
            synchronized (getFailoverMutex())
            {
                if (!_closed.getAndSet(true))
                {
                    try
                    {
                        long startCloseTime = System.currentTimeMillis();

                        _taskPool.shutdown();
                        closeAllSessions(null, timeout, startCloseTime);

                        if (!_taskPool.isTerminated())
                        {
                            try
                            {
                                // adjust timeout
                                long taskPoolTimeout = adjustTimeout(timeout, startCloseTime);

                                _taskPool.awaitTermination(taskPoolTimeout, TimeUnit.MILLISECONDS);
                            }
                            catch (InterruptedException e)
                            {
                                _logger.info("Interrupted while shutting down connection thread pool.");
                            }
                        }

                        // adjust timeout
                        timeout = adjustTimeout(timeout, startCloseTime);

                        _protocolHandler.closeConnection(timeout);

                        //If the taskpool hasn't shutdown by now then give it shutdownNow.
                        // This will interupt any running tasks.
                        if (!_taskPool.isTerminated())
                        {
                            List<Runnable> tasks = _taskPool.shutdownNow();
                            for (Runnable r : tasks)
                            {
                                _logger.warn("Connection close forced taskpool to prevent execution:" + r);
                            }
                        }
                    }
                    catch (AMQException e)
                    {
                        JMSException jmse = new JMSException("Error closing connection: " + e);
                        jmse.setLinkedException(e);
                        throw jmse;
                    }
                }
            }
            }
        }
    }

    private long adjustTimeout(long timeout, long startTime)
    {
        long now = System.currentTimeMillis();
        timeout -= now - startTime;
        if (timeout < 0)
        {
            timeout = 0;
        }

        return timeout;
    }

    /**
     * Marks all sessions and their children as closed without sending any protocol messages. Useful when you need to
     * mark objects "visible" in userland as closed after failover or other significant event that impacts the
     * connection. <p/> The caller must hold the failover mutex before calling this method.
     */
    private void markAllSessionsClosed()
    {
        final LinkedList sessionCopy = new LinkedList(_sessions.values());
        final Iterator it = sessionCopy.iterator();
        while (it.hasNext())
        {
            final AMQSession session = (AMQSession) it.next();

            session.markClosed();
        }

        _sessions.clear();
    }

    /**
     * Close all the sessions, either due to normal connection closure or due to an error occurring.
     *
     * @param cause if not null, the error that is causing this shutdown <p/> The caller must hold the failover mutex
     *              before calling this method.
     */
    private void closeAllSessions(Throwable cause, long timeout, long starttime) throws JMSException
    {
        final LinkedList sessionCopy = new LinkedList(_sessions.values());
        final Iterator it = sessionCopy.iterator();
        JMSException sessionException = null;
        while (it.hasNext())
        {
            final AMQSession session = (AMQSession) it.next();
            if (cause != null)
            {
                session.closed(cause);
            }
            else
            {
                try
                {
                    if (starttime != -1)
                    {
                        timeout = adjustTimeout(timeout, starttime);
                    }

                    session.close(timeout);
                }
                catch (JMSException e)
                {
                    _logger.error("Error closing session: " + e);
                    sessionException = e;
                }
            }
        }

        _sessions.clear();
        if (sessionException != null)
        {
            throw sessionException;
        }
    }

    public ConnectionConsumer createConnectionConsumer(Destination destination, String messageSelector,
                                                       ServerSessionPool sessionPool, int maxMessages) throws JMSException
    {
        checkNotClosed();

        return null;
    }

    public ConnectionConsumer createConnectionConsumer(Queue queue, String messageSelector, ServerSessionPool sessionPool,
                                                       int maxMessages) throws JMSException
    {
        checkNotClosed();

        return null;
    }

    public ConnectionConsumer createConnectionConsumer(Topic topic, String messageSelector, ServerSessionPool sessionPool,
                                                       int maxMessages) throws JMSException
    {
        checkNotClosed();

        return null;
    }

    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector,
                                                              ServerSessionPool sessionPool, int maxMessages) throws JMSException
    {
        // TODO Auto-generated method stub
        checkNotClosed();

        return null;
    }

    public long getMaximumChannelCount() throws JMSException
    {
        checkNotClosed();

        return _maximumChannelCount;
    }

    public void setConnectionListener(ConnectionListener listener)
    {
        _connectionListener = listener;
    }

    public ConnectionListener getConnectionListener()
    {
        return _connectionListener;
    }

    public void setMaximumChannelCount(long maximumChannelCount)
    {
        _maximumChannelCount = maximumChannelCount;
    }

    public void setMaximumFrameSize(long frameMax)
    {
        _maximumFrameSize = frameMax;
    }

    public long getMaximumFrameSize()
    {
        return _maximumFrameSize;
    }

    public Map getSessions()
    {
        return _sessions;
    }

    public String getUsername()
    {
        return _username;
    }

    public String getPassword()
    {
        return _password;
    }

    public String getVirtualHost()
    {
        return _virtualHost;
    }

    public AMQProtocolHandler getProtocolHandler()
    {
        return _protocolHandler;
    }

    public boolean started()
    {
        return _started;
    }

    public void bytesSent(long writtenBytes)
    {
        if (_connectionListener != null)
        {
            _connectionListener.bytesSent(writtenBytes);
        }
    }

    public void bytesReceived(long receivedBytes)
    {
        if (_connectionListener != null)
        {
            _connectionListener.bytesReceived(receivedBytes);
        }
    }

    /**
     * Fire the preFailover event to the registered connection listener (if any)
     *
     * @param redirect true if this is the result of a redirect request rather than a connection error
     *
     * @return true if no listener or listener does not veto change
     */
    public boolean firePreFailover(boolean redirect)
    {
        boolean proceed = true;
        if (_connectionListener != null)
        {
            proceed = _connectionListener.preFailover(redirect);
        }

        return proceed;
    }

    /**
     * Fire the preResubscribe event to the registered connection listener (if any). If the listener vetoes
     * resubscription then all the sessions are closed.
     *
     * @return true if no listener or listener does not veto resubscription.
     *
     * @throws JMSException
     */
    public boolean firePreResubscribe() throws JMSException
    {
        if (_connectionListener != null)
        {
            boolean resubscribe = _connectionListener.preResubscribe();
            if (!resubscribe)
            {
                markAllSessionsClosed();
            }

            return resubscribe;
        }
        else
        {
            return true;
        }
    }

    /** Fires a failover complete event to the registered connection listener (if any). */
    public void fireFailoverComplete()
    {
        if (_connectionListener != null)
        {
            _connectionListener.failoverComplete();
        }
    }

    /**
     * In order to protect the consistency of the connection and its child sessions, consumers and producers, the
     * "failover mutex" must be held when doing any operations that could be corrupted during failover.
     *
     * @return a mutex. Guaranteed never to change for the lifetime of this connection even if failover occurs.
     */
    public final Object getFailoverMutex()
    {
        return _failoverMutex;
    }

    /**
     * If failover is taking place this will block until it has completed. If failover is not taking place it will
     * return immediately.
     *
     * @throws InterruptedException
     */
    public void blockUntilNotFailingOver() throws InterruptedException
    {
        _protocolHandler.blockUntilNotFailingOver();
    }

    /**
     * Invoked by the AMQProtocolSession when a protocol session exception has occurred. This method sends the exception
     * to a JMS exception listener, if configured, and propagates the exception to sessions, which in turn will
     * propagate to consumers. This allows synchronous consumers to have exceptions thrown to them.
     *
     * @param cause the exception
     */
    public void exceptionReceived(Throwable cause)
    {

        if (_logger.isDebugEnabled())
        {
            _logger.debug("exceptionReceived done by:" + Thread.currentThread().getName(), cause);
        }

        final JMSException je;
        if (cause instanceof JMSException)
        {
            je = (JMSException) cause;
        }
        else
        {
            if (cause instanceof AMQException)
            {
                je =
                        new JMSException(Integer.toString(((AMQException) cause).getErrorCode().getCode()),
                                         "Exception thrown against " + toString() + ": " + cause);
            }
            else
            {
                je = new JMSException("Exception thrown against " + toString() + ": " + cause);
            }

            if (cause instanceof Exception)
            {
                je.setLinkedException((Exception) cause);
            }
        }

        // in the case of an IOException, MINA has closed the protocol session so we set _closed to true
        // so that any generic client code that tries to close the connection will not mess up this error
        // handling sequence
        if (cause instanceof IOException)
        {
            _closed.set(true);
        }

        if (_exceptionListener != null)
        {
            _exceptionListener.onException(je);
        }
        else
        {
            _logger.error("Throwable Received but no listener set: " + cause.getMessage());
        }

        if (!(cause instanceof AMQUndeliveredException) && !(cause instanceof AMQAuthenticationException))
        {
            try
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Closing AMQConnection due to :" + cause.getMessage());
                }

                _closed.set(true);
                closeAllSessions(cause, -1, -1); // FIXME: when doing this end up with RejectedExecutionException from executor.
            }
            catch (JMSException e)
            {
                _logger.error("Error closing all sessions: " + e, e);
            }

        }
        else
        {
            _logger.info("Not a hard-error connection not closing: " + cause.getMessage());
        }
    }

    void registerSession(int channelId, AMQSession session)
    {
        _sessions.put(channelId, session);
    }

    void deregisterSession(int channelId)
    {
        _sessions.remove(channelId);
    }

    /**
     * For all sessions, and for all consumers in those sessions, resubscribe. This is called during failover handling.
     * The caller must hold the failover mutex before calling this method.
     */
    public void resubscribeSessions() throws JMSException, AMQException, FailoverException
    {
        ArrayList sessions = new ArrayList(_sessions.values());
        _logger.info(MessageFormat.format("Resubscribing sessions = {0} sessions.size={1}", sessions, sessions.size())); // FIXME: removeKey?
        for (Iterator it = sessions.iterator(); it.hasNext();)
        {
            AMQSession s = (AMQSession) it.next();
            // _protocolHandler.addSessionByChannel(s.getChannelId(), s);
            reopenChannel(s.getChannelId(), s.getDefaultPrefetchHigh(), s.getDefaultPrefetchLow(), s.getTransacted());
            s.resubscribe();
        }
    }

    public String toString()
    {
        StringBuffer buf = new StringBuffer("AMQConnection:\n");
        if (_failoverPolicy.getCurrentBrokerDetails() == null)
        {
            buf.append("No active broker connection");
        }
        else
        {
            BrokerDetails bd = _failoverPolicy.getCurrentBrokerDetails();
            buf.append("Host: ").append(String.valueOf(bd.getHost()));
            buf.append("\nPort: ").append(String.valueOf(bd.getPort()));
        }

        buf.append("\nVirtual Host: ").append(String.valueOf(_virtualHost));
        buf.append("\nClient ID: ").append(String.valueOf(_clientName));
        buf.append("\nActive session count: ").append((_sessions == null) ? 0 : _sessions.size());

        return buf.toString();
    }

    public String toURL()
    {
        return _connectionURL.toString();
    }

    public Reference getReference() throws NamingException
    {
        return new Reference(AMQConnection.class.getName(), new StringRefAddr(AMQConnection.class.getName(), toURL()),
                             AMQConnectionFactory.class.getName(), null); // factory location
    }

    public SSLConfiguration getSSLConfiguration()
    {
        return _sslConfiguration;
    }

    public AMQShortString getDefaultTopicExchangeName()
    {
        return _defaultTopicExchangeName;
    }

    public void setDefaultTopicExchangeName(AMQShortString defaultTopicExchangeName)
    {
        _defaultTopicExchangeName = defaultTopicExchangeName;
    }

    public AMQShortString getDefaultQueueExchangeName()
    {
        return _defaultQueueExchangeName;
    }

    public void setDefaultQueueExchangeName(AMQShortString defaultQueueExchangeName)
    {
        _defaultQueueExchangeName = defaultQueueExchangeName;
    }

    public AMQShortString getTemporaryTopicExchangeName()
    {
        return _temporaryTopicExchangeName;
    }

    public AMQShortString getTemporaryQueueExchangeName()
    {
        return _temporaryQueueExchangeName; // To change body of created methods use File | Settings | File Templates.
    }

    public void setTemporaryTopicExchangeName(AMQShortString temporaryTopicExchangeName)
    {
        _temporaryTopicExchangeName = temporaryTopicExchangeName;
    }

    public void setTemporaryQueueExchangeName(AMQShortString temporaryQueueExchangeName)
    {
        _temporaryQueueExchangeName = temporaryQueueExchangeName;
    }

    public void performConnectionTask(Runnable task)
    {
        _taskPool.execute(task);
    }

    public AMQSession getSession(int channelId)
    {
        return _sessions.get(channelId);
    }

    public ProtocolVersion getProtocolVersion()
    {
        return _protocolVersion;
    }

    public void setProtocolVersion(ProtocolVersion protocolVersion)
    {
        _protocolVersion = protocolVersion;
        _protocolHandler.getProtocolSession().setProtocolVersion(protocolVersion);
    }

    public boolean isFailingOver()
    {
        return (_protocolHandler.getFailoverLatch() != null);
    }
}
