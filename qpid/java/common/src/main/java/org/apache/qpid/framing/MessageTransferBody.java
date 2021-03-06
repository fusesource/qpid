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

/*
 * This file is auto-generated by Qpid Gentools v.0.1 - do not modify.
 * Supported AMQP version:
  *   0-9
  *   0-91
  *   8-0
  */

package org.apache.qpid.framing;

public interface MessageTransferBody extends EncodableAMQDataBlock, AMQMethodBody
{

    public AMQShortString getAppId();

    public FieldTable getApplicationHeaders();

    public Content getBody();

    public AMQShortString getContentEncoding();

    public AMQShortString getContentType();

    public AMQShortString getCorrelationId();

    public short getDeliveryMode();

    public AMQShortString getDestination();

    public AMQShortString getExchange();

    public long getExpiration();

    public boolean getImmediate();

    public AMQShortString getMessageId();

    public short getPriority();

    public boolean getRedelivered();

    public AMQShortString getReplyTo();

    public AMQShortString getRoutingKey();

    public byte[] getSecurityToken();

    public int getTicket();

    public long getTimestamp();

    public AMQShortString getTransactionId();

    public long getTtl();

    public AMQShortString getUserId();
}
