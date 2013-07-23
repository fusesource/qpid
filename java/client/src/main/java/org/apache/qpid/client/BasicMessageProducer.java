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

import java.util.UUID;
import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.MessageConverter;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.util.UUIDGen;
import org.apache.qpid.util.UUIDs;
import org.slf4j.Logger;

public abstract class BasicMessageProducer extends Closeable implements org.apache.qpid.jms.MessageProducer
{


    enum PublishMode { ASYNC_PUBLISH_ALL, SYNC_PUBLISH_PERSISTENT, SYNC_PUBLISH_ALL };

    private final Logger _logger ;

    private AMQConnection _connection;

    private boolean _disableTimestamps;

    /**
     * Priority of messages created by this producer.
     */
    private int _messagePriority = Message.DEFAULT_PRIORITY;

    /**
     * Time to live of messages. Specified in milliseconds but AMQ has 1 second resolution.
     */
    private long _timeToLive;

    /**
     * Delivery mode used for this producer.
     */
    private int _deliveryMode = DeliveryMode.PERSISTENT;

    private AMQDestination _destination;

    /**
     * True if this producer was created from a transacted session
     */
    private boolean _transacted;

    private int _channelId;

    /**
     * This is an id generated by the session and is used to tie individual producers to the session. This means we
     * can deregister a producer with the session when the producer is clsoed. We need to be able to tie producers
     * to the session so that when an error is propagated to the session it can close the producer (meaning that
     * a client that happens to hold onto a producer reference will get an error if he tries to use it subsequently).
     */
    private long _producerId;

    private AMQSession _session;

    private final boolean _immediate;

    private final Boolean _mandatory;

    private boolean _disableMessageId;

    private UUIDGen _messageIdGenerator = UUIDs.newGenerator();

    private String _userID;  // ref user id used in the connection.


    /**
     * The default value for immediate flag used this producer is false. That is, a consumer does
     * not need to be attached to a queue.
     */
    private final boolean _defaultImmediateValue = Boolean.parseBoolean(System.getProperty("qpid.default_immediate", "false"));

    /**
     * The default value for mandatory flag used by this producer is true. That is, server will not
     * silently drop messages where no queue is connected to the exchange for the message.
     */
    private final boolean _defaultMandatoryValue = Boolean.parseBoolean(System.getProperty("qpid.default_mandatory", "true"));

    /**
     * The default value for mandatory flag used by this producer when publishing to a Topic is false. That is, server
     * will silently drop messages where no queue is connected to the exchange for the message.
     */
    private final boolean _defaultMandatoryTopicValue =
            Boolean.parseBoolean(System.getProperty("qpid.default_mandatory_topic",
                    System.getProperties().containsKey("qpid.default_mandatory")
                            ? System.getProperty("qpid.default_mandatory")
                            : "false"));

    private PublishMode publishMode = PublishMode.ASYNC_PUBLISH_ALL;

    protected BasicMessageProducer(Logger logger,AMQConnection connection, AMQDestination destination, boolean transacted, int channelId,
                                   AMQSession session, long producerId, Boolean immediate, Boolean mandatory) throws AMQException
    {
    	_logger = logger;
    	_connection = connection;
        _destination = destination;
        _transacted = transacted;
        _channelId = channelId;
        _session = session;
        _producerId = producerId;
        if (destination != null  && !(destination instanceof AMQUndefinedDestination))
        {
            declareDestination(destination);
        }

        _immediate = immediate == null ? _defaultImmediateValue : immediate;
        _mandatory = mandatory == null
                ? destination == null ? null
                                      : destination instanceof Topic
                                            ? _defaultMandatoryTopicValue
                                            : _defaultMandatoryValue
                : mandatory;

        _userID = connection.getUsername();
        setPublishMode();
    }

    protected AMQConnection getConnection()
    {
        return _connection;
    }

    void setPublishMode()
    {
        // Publish mode could be configured at destination level as well.
        // Will add support for this when we provide a more robust binding URL

        String syncPub = _connection.getSyncPublish();
        // Support for deprecated option sync_persistence
        if (syncPub.equals("persistent") || _connection.getSyncPersistence())
        {
            publishMode = PublishMode.SYNC_PUBLISH_PERSISTENT;
        }
        else if (syncPub.equals("all"))
        {
            publishMode = PublishMode.SYNC_PUBLISH_ALL;
        }

        if (_logger.isDebugEnabled())
        {
        	_logger.debug("MessageProducer " + toString() + " using publish mode : " + publishMode);
        }
    }

    void resubscribe() throws AMQException
    {
        if (_destination != null && !(_destination instanceof AMQUndefinedDestination))
        {
            declareDestination(_destination);
        }
    }

    abstract void declareDestination(AMQDestination destination) throws AMQException;

    public void setDisableMessageID(boolean b) throws JMSException
    {
        checkPreConditions();
        checkNotClosed();
        _disableMessageId = b;
    }

    public boolean getDisableMessageID() throws JMSException
    {
        checkNotClosed();

        return _disableMessageId;
    }

    public void setDisableMessageTimestamp(boolean b) throws JMSException
    {
        checkPreConditions();
        _disableTimestamps = b;
    }

    public boolean getDisableMessageTimestamp() throws JMSException
    {
        checkNotClosed();

        return _disableTimestamps;
    }

    public void setDeliveryMode(int i) throws JMSException
    {
        checkPreConditions();
        if ((i != DeliveryMode.NON_PERSISTENT) && (i != DeliveryMode.PERSISTENT))
        {
            throw new JMSException("DeliveryMode must be either NON_PERSISTENT or PERSISTENT. Value of " + i
                                   + " is illegal");
        }

        _deliveryMode = i;
    }

    public int getDeliveryMode() throws JMSException
    {
        checkNotClosed();

        return _deliveryMode;
    }

    public void setPriority(int i) throws JMSException
    {
        checkPreConditions();
        if ((i < 0) || (i > 9))
        {
            throw new IllegalArgumentException("Priority of " + i + " is illegal. Value must be in range 0 to 9");
        }

        _messagePriority = i;
    }

    public int getPriority() throws JMSException
    {
        checkNotClosed();

        return _messagePriority;
    }

    public void setTimeToLive(long l) throws JMSException
    {
        checkPreConditions();
        if (l < 0)
        {
            throw new IllegalArgumentException("Time to live must be non-negative - supplied value was " + l);
        }

        _timeToLive = l;
    }

    public long getTimeToLive() throws JMSException
    {
        checkNotClosed();

        return _timeToLive;
    }

    protected AMQDestination getAMQDestination()
    {
        return _destination;
    }

    /**
     * The Destination used for this consumer, if specified upon creation.
     */
    public Destination getDestination() throws JMSException
    {
        checkNotClosed();

        return _destination;
    }

    public void close() throws JMSException
    {
        setClosed();
        _session.deregisterProducer(_producerId);
    }

    public void send(Message message) throws JMSException
    {
        checkPreConditions();
        checkInitialDestination();

        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, message, _deliveryMode, _messagePriority, _timeToLive, _mandatory, _immediate);
        }
    }

    public void send(Message message, int deliveryMode) throws JMSException
    {
        checkPreConditions();
        checkInitialDestination();

        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, message, deliveryMode, _messagePriority, _timeToLive, _mandatory, _immediate);
        }
    }

    public void send(Message message, int deliveryMode, boolean immediate) throws JMSException
    {
        checkPreConditions();
        checkInitialDestination();
        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, message, deliveryMode, _messagePriority, _timeToLive, _mandatory, immediate);
        }
    }

    public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException
    {
        checkPreConditions();
        checkInitialDestination();
        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, message, deliveryMode, priority, timeToLive, _mandatory, _immediate);
        }
    }

    public void send(Destination destination, Message message) throws JMSException
    {
        checkPreConditions();
        checkDestination(destination);
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, message, _deliveryMode, _messagePriority, _timeToLive,
                    _mandatory == null
                            ? destination instanceof Topic
                                ? _defaultMandatoryTopicValue
                                : _defaultMandatoryValue
                            : _mandatory,
                     _immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive)
        throws JMSException
    {
        checkPreConditions();
        checkDestination(destination);
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, message, deliveryMode, priority, timeToLive,
                    _mandatory == null
                            ? destination instanceof Topic
                                ? _defaultMandatoryTopicValue
                                : _defaultMandatoryValue
                            : _mandatory,
                    _immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive,
                     boolean mandatory) throws JMSException
    {
        checkPreConditions();
        checkDestination(destination);
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, message, deliveryMode, priority, timeToLive, mandatory, _immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive,
                     boolean mandatory, boolean immediate) throws JMSException
    {
        checkPreConditions();
        checkDestination(destination);
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, message, deliveryMode, priority, timeToLive, mandatory, immediate);
        }
    }

    private AbstractJMSMessage convertToNativeMessage(Message message) throws JMSException
    {
        if (message instanceof AbstractJMSMessage)
        {
            return (AbstractJMSMessage) message;
        }
        else
        {
            AbstractJMSMessage newMessage;

            if (message instanceof BytesMessage)
            {
                newMessage = new MessageConverter(_session, (BytesMessage) message).getConvertedMessage();
            }
            else if (message instanceof MapMessage)
            {
                newMessage = new MessageConverter(_session, (MapMessage) message).getConvertedMessage();
            }
            else if (message instanceof ObjectMessage)
            {
                newMessage = new MessageConverter(_session, (ObjectMessage) message).getConvertedMessage();
            }
            else if (message instanceof TextMessage)
            {
                newMessage = new MessageConverter(_session, (TextMessage) message).getConvertedMessage();
            }
            else if (message instanceof StreamMessage)
            {
                newMessage = new MessageConverter(_session, (StreamMessage) message).getConvertedMessage();
            }
            else
            {
                newMessage = new MessageConverter(_session, message).getConvertedMessage();
            }

            if (newMessage != null)
            {
                return newMessage;
            }
            else
            {
                throw new JMSException("Unable to send message, due to class conversion error: "
                                       + message.getClass().getName());
            }
        }
    }

    private void validateDestination(Destination destination) throws JMSException
    {
        if (!(destination instanceof AMQDestination))
        {
            throw new InvalidDestinationException("Unsupported destination class: "
                                   + ((destination != null) ? destination.getClass() : null));
        }

        AMQDestination amqDestination = (AMQDestination) destination;
        if(!amqDestination.isExchangeExistsChecked())
        {
            try
            {
                declareDestination(amqDestination);
            }
            catch(Exception e)
            {
                JMSException ex = new InvalidDestinationException("Error validating destination");
                ex.initCause(e);
                ex.setLinkedException(e);

                throw ex;
            }
            amqDestination.setExchangeExistsChecked(true);
        }
    }

    /**
     * The caller of this method must hold the failover mutex.
     *
     * @param destination
     * @param origMessage
     * @param deliveryMode
     * @param priority
     * @param timeToLive
     * @param mandatory
     * @param immediate
     *
     * @throws JMSException
     */
    protected void sendImpl(AMQDestination destination, Message origMessage, int deliveryMode, int priority, long timeToLive,
                            boolean mandatory, boolean immediate) throws JMSException
    {
        checkTemporaryDestination(destination);
        origMessage.setJMSDestination(destination);

        AbstractJMSMessage message = convertToNativeMessage(origMessage);

        UUID messageId = null;
        if (_disableMessageId)
        {
            message.setJMSMessageID((UUID)null);
        }
        else
        {
            messageId = _messageIdGenerator.generate();
            message.setJMSMessageID(messageId);
        }

        try
        {
            sendMessage(destination, origMessage, message, messageId, deliveryMode, priority, timeToLive, mandatory, immediate);
        }
        catch (TransportException e)
        {
            throw getSession().toJMSException("Exception whilst sending:" + e.getMessage(), e);
        }

        if (message != origMessage)
        {
            _logger.debug("Updating original message");
            origMessage.setJMSPriority(message.getJMSPriority());
            origMessage.setJMSTimestamp(message.getJMSTimestamp());
            if (_logger.isDebugEnabled())
            {
            	_logger.debug("Setting JMSExpiration:" + message.getJMSExpiration());
            }
            origMessage.setJMSExpiration(message.getJMSExpiration());
            origMessage.setJMSMessageID(message.getJMSMessageID());
        }

        if (_transacted)
        {
            _session.markDirty();
        }
    }

    abstract void sendMessage(AMQDestination destination, Message origMessage, AbstractJMSMessage message,
                              UUID messageId, int deliveryMode, int priority, long timeToLive, boolean mandatory,
                              boolean immediate) throws JMSException;

    private void checkTemporaryDestination(AMQDestination destination) throws JMSException
    {
        if (destination instanceof TemporaryDestination)
        {
            _logger.debug("destination is temporary destination");
            TemporaryDestination tempDest = (TemporaryDestination) destination;
            if (tempDest.getSession().isClosed())
            {
                _logger.debug("session is closed");
                throw new JMSException("Session for temporary destination has been closed");
            }

            if (tempDest.isDeleted())
            {
                _logger.debug("destination is deleted");
                throw new JMSException("Cannot send to a deleted temporary destination");
            }
        }
    }

    private void checkPreConditions() throws JMSException
    {
        checkNotClosed();

        if ((_session == null) || _session.isClosed())
        {
            throw new javax.jms.IllegalStateException("Invalid Session");
        }
        if(_session.getAMQConnection().isClosed())
        {
            throw new javax.jms.IllegalStateException("Connection closed");
        }
    }

    private void checkInitialDestination() throws JMSException
    {
        if (_destination == null)
        {
            throw new UnsupportedOperationException("Destination is null");
        }
        checkValidQueue();
    }

    private void checkDestination(Destination suppliedDestination) throws JMSException
    {
        if ((_destination != null) && (suppliedDestination != null))
        {
            throw new UnsupportedOperationException(
                    "This message producer was created with a Destination, therefore you cannot use an unidentified Destination");
        }

        if(suppliedDestination instanceof AMQQueue)
        {
            AMQQueue destination = (AMQQueue) suppliedDestination;
            checkValidQueue(destination);
        }
        if (suppliedDestination == null)
        {
            throw new InvalidDestinationException("Supplied Destination was invalid");
        }

    }

    private void checkValidQueue() throws JMSException
    {
        if(_destination instanceof AMQQueue)
        {
            checkValidQueue((AMQQueue) _destination);
        }
    }

    private void checkValidQueue(AMQQueue destination) throws JMSException
    {
        if (!destination.isCheckedForQueueBinding() && validateQueueOnSend())
        {
            if (getSession().isStrictAMQP())
            {
                getLogger().warn("AMQP does not support destination validation before publish");
                destination.setCheckedForQueueBinding(true);
            }
            else
            {
                if (isBound(destination))
                {
                    destination.setCheckedForQueueBinding(true);
                }
                else
                {
                    throw new InvalidDestinationException("Queue: " + destination.getQueueName()
                        + " is not a valid destination (no binding on server)");
                }
            }
        }
    }

    private boolean validateQueueOnSend()
    {
        return _connection.validateQueueOnSend();
    }

    /**
     * The session used to create this producer
     */
    public AMQSession getSession()
    {
        return _session;
    }

    public boolean isBound(AMQDestination destination) throws JMSException
    {
        try
        {
            return _session.isQueueBound(destination.getExchangeName(), null, destination.getRoutingKey());
        }
        catch (TransportException e)
        {
            throw getSession().toJMSException("Exception whilst checking destination binding:" + e.getMessage(), e);
        }
    }

    /**
     * If true, messages will not get a timestamp.
     */
    protected boolean isDisableTimestamps()
    {
        return _disableTimestamps;
    }

    protected void setDisableTimestamps(boolean disableTimestamps)
    {
        _disableTimestamps = disableTimestamps;
    }

    protected void setDestination(AMQDestination destination)
    {
        _destination = destination;
    }

    protected int getChannelId()
    {
        return _channelId;
    }

    protected void setChannelId(int channelId)
    {
        _channelId = channelId;
    }

    protected void setSession(AMQSession session)
    {
        _session = session;
    }

    protected String getUserID()
    {
        return _userID;
    }

    protected void setUserID(String userID)
    {
        _userID = userID;
    }

    protected PublishMode getPublishMode()
    {
        return publishMode;
    }

    protected void setPublishMode(PublishMode publishMode)
    {
        this.publishMode = publishMode;
    }

    Logger getLogger()
    {
        return _logger;
    }

}
