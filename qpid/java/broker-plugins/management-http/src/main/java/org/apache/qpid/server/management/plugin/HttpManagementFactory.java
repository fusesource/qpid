/*
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
 */
package org.apache.qpid.server.management.plugin;

import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.plugin.PluginFactory;

public class HttpManagementFactory implements PluginFactory
{

    @Override
    public Plugin createInstance(UUID id, Map<String, Object> attributes, Broker broker)
    {
        if (!HttpManagement.PLUGIN_TYPE.equals(attributes.get(PLUGIN_TYPE)))
        {
            return null;
        }

        return new HttpManagement(id, broker, attributes);
    }

    @Override
    public String getType()
    {
        return "HTTP Management";
    }
}
