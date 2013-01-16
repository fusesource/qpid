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
package org.apache.qpid.server.configuration.startup;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsGatherer;

public class VirtualHostRecovererTest extends TestCase
{
    public void testCreate()
    {
        StatisticsGatherer statisticsGatherer = mock(StatisticsGatherer.class);
        SecurityManager securityManager = mock(SecurityManager.class);
        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        Broker parent = mock(Broker.class);
        when(parent.getSecurityManager()).thenReturn(securityManager);

        VirtualHostRecoverer recoverer = new VirtualHostRecoverer(statisticsGatherer);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.CONFIGURATION, "/path/to/virtualhost.xml");
        when(entry.getAttributes()).thenReturn(attributes);

        VirtualHost host = recoverer.create(null, entry, parent);

        assertNotNull("Null is returned", host);
        assertEquals("Unexpected name", getName(), host.getName());
    }

    public void testCreateWithoutMandatoryAttributesResultsInException()
    {
        StatisticsGatherer statisticsGatherer = mock(StatisticsGatherer.class);
        SecurityManager securityManager = mock(SecurityManager.class);
        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        Broker parent = mock(Broker.class);
        when(parent.getSecurityManager()).thenReturn(securityManager);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.CONFIGURATION, "/path/to/virtualhost.xml");

        VirtualHostRecoverer recoverer = new VirtualHostRecoverer(statisticsGatherer);

        //TODO: configuration is made mandatory temporarily, it will became optional later.
        String[] mandatoryAttributes = {VirtualHost.NAME, VirtualHost.CONFIGURATION};
        for (String name : mandatoryAttributes)
        {
            Map<String, Object> copy = new HashMap<String, Object>(attributes);
            copy.remove(name);
            when(entry.getAttributes()).thenReturn(copy);
            try
            {
                recoverer.create(null, entry, parent);
                fail("Cannot create a virtual host without a manadatory attribute " + name);
            }
            catch(IllegalArgumentException e)
            {
                // pass
            }
        }
    }
}