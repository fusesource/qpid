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

package org.apache.qpid.server.queue;

import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.virtualhost.VirtualHost;

public class ConflationQueue extends SimpleAMQQueue
{
    protected ConflationQueue(UUID id,
                              String name,
                              boolean durable,
                              String owner,
                              boolean autoDelete,
                              boolean exclusive,
                              VirtualHost virtualHost,
                              Map<String, Object> args, String conflationKey)
    {
        super(id, name, durable, owner, autoDelete, exclusive, virtualHost, new ConflationQueueList.Factory(conflationKey), args);
    }

    public String getConflationKey()
    {
        return ((ConflationQueueList) getEntries()).getConflationKey();
    }

}
