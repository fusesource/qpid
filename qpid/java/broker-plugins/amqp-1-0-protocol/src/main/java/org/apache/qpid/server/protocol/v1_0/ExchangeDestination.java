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
package org.apache.qpid.server.protocol.v1_0;

import java.util.List;
import org.apache.qpid.AMQException;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.amqp_1_0.type.messaging.Rejected;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusExpiryPolicy;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.txn.ServerTransaction;

public class ExchangeDestination implements ReceivingDestination, SendingDestination
{
    private static final Accepted ACCEPTED = new Accepted();
    public static final Rejected REJECTED = new Rejected();
    private static final Outcome[] OUTCOMES = { ACCEPTED, REJECTED};

    private Exchange _exchange;
    private TerminusDurability _durability;
    private TerminusExpiryPolicy _expiryPolicy;

    public ExchangeDestination(Exchange exchange, TerminusDurability durable, TerminusExpiryPolicy expiryPolicy)
    {
        _exchange = exchange;
        _durability = durable;
        _expiryPolicy = expiryPolicy;
    }

    public Outcome[] getOutcomes()
    {
        return OUTCOMES;
    }

    public Outcome send(final Message_1_0 message, ServerTransaction txn)
    {
        final InstanceProperties instanceProperties =
            new InstanceProperties()
            {

                @Override
                public Object getProperty(final Property prop)
                {
                    switch(prop)
                    {
                        case MANDATORY:
                            return false;
                        case REDELIVERED:
                            return false;
                        case PERSISTENT:
                            return message.isPersistent();
                        case IMMEDIATE:
                            return false;
                        case EXPIRATION:
                            return message.getExpiration();
                    }
                    return null;
                }};

        int enqueues = _exchange.send(message, instanceProperties, txn, null);


        return enqueues == 0 ? REJECTED : ACCEPTED;
    }

    TerminusDurability getDurability()
    {
        return _durability;
    }

    TerminusExpiryPolicy getExpiryPolicy()
    {
        return _expiryPolicy;
    }

    public int getCredit()
    {
        // TODO - fix
        return 20000;
    }

    public Exchange getExchange()
    {
        return _exchange;
    }
}
