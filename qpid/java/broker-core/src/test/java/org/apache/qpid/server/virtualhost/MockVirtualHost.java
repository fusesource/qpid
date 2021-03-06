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
package org.apache.qpid.server.virtualhost;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.protocol.LinkRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.DtxRegistry;

import java.util.UUID;

public class MockVirtualHost implements VirtualHost
{
    private String _name;

    public MockVirtualHost(String name)
    {
        _name = name;
    }

    public void close()
    {

    }

    @Override
    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return null;
    }

    public AuthenticationManager getAuthenticationManager()
    {
        return null;
    }

    public DtxRegistry getDtxRegistry()
    {
        return null;
    }

    public VirtualHostConfiguration getConfiguration()
    {
        return null;
    }

    public IConnectionRegistry getConnectionRegistry()
    {
        return null;
    }

    public int getHouseKeepingActiveCount()
    {
        return 0;
    }

    public long getHouseKeepingCompletedTaskCount()
    {
        return 0;
    }

    public int getHouseKeepingPoolSize()
    {
        return 0;
    }

    public long getHouseKeepingTaskCount()
    {
        return 0;
    }

    public MessageStore getMessageStore()
    {
        return null;
    }

    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return null;
    }

    public String getName()
    {
        return _name;
    }

    public QueueRegistry getQueueRegistry()
    {
        return null;
    }

    @Override
    public AMQQueue getQueue(String name)
    {
        return null;
    }

    @Override
    public AMQQueue getQueue(UUID id)
    {
        return null;
    }

    @Override
    public Collection<AMQQueue> getQueues()
    {
        return null;
    }

    @Override
    public int removeQueue(AMQQueue queue) throws AMQException
    {
        return 0;
    }

    @Override
    public AMQQueue createQueue(UUID id,
                                String queueName,
                                boolean durable,
                                String owner,
                                boolean autoDelete,
                                boolean exclusive,
                                boolean deleteOnNoConsumer,
                                Map<String, Object> arguments) throws AMQException
    {
        return null;
    }

    @Override
    public Exchange createExchange(UUID id,
                                   String exchange,
                                   String type,
                                   boolean durable,
                                   boolean autoDelete,
                                   String alternateExchange) throws AMQException
    {
        return null;
    }

    @Override
    public void removeExchange(Exchange exchange, boolean force) throws AMQException
    {
    }

    @Override
    public Exchange getExchange(String name)
    {
        return null;
    }

    @Override
    public Exchange getExchange(UUID id)
    {
        return null;
    }

    @Override
    public Exchange getDefaultExchange()
    {
        return null;
    }

    @Override
    public Collection<Exchange> getExchanges()
    {
        return null;
    }

    @Override
    public Collection<ExchangeType<? extends Exchange>> getExchangeTypes()
    {
        return null;
    }

    public SecurityManager getSecurityManager()
    {
        return null;
    }

    @Override
    public void addVirtualHostListener(VirtualHostListener listener)
    {
    }

    public LinkRegistry getLinkRegistry(String remoteContainerId)
    {
        return null;
    }

    public ScheduledFuture<?> scheduleTask(long delay, Runnable timeoutTask)
    {
        return null;
    }

    public void scheduleHouseKeepingTask(long period, HouseKeepingTask task)
    {

    }

    public void setHouseKeepingPoolSize(int newSize)
    {

    }


    public long getCreateTime()
    {
        return 0;
    }

    public UUID getId()
    {
        return null;
    }

    public boolean isDurable()
    {
        return false;
    }

    public StatisticsCounter getDataDeliveryStatistics()
    {
        return null;
    }

    public StatisticsCounter getDataReceiptStatistics()
    {
        return null;
    }

    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return null;
    }

    public StatisticsCounter getMessageReceiptStatistics()
    {
        return null;
    }

    public void initialiseStatistics()
    {

    }

    public void registerMessageDelivered(long messageSize)
    {

    }

    public void registerMessageReceived(long messageSize, long timestamp)
    {

    }

    public void resetStatistics()
    {

    }

    public State getState()
    {
        return State.ACTIVE;
    }

    public void block()
    {
    }

    public void unblock()
    {
    }
}
