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
package org.apache.qpid.server.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapValueConverter
{

    public static String getStringAttribute(String name, Map<String,Object> attributes, String defaultVal)
    {
        final Object value = attributes.get(name);
        return value == null ? defaultVal : String.valueOf(value);
    }

    public static String getStringAttribute(String name, Map<String, Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getStringAttribute(name, attributes, null);
    }

    private static void assertMandatoryAttribute(String name, Map<String, Object> attributes)
    {
        if (!attributes.containsKey(name))
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not found");
        }
    }

    public static Map<String,Object> getMapAttribute(String name, Map<String,Object> attributes, Map<String,Object> defaultVal)
    {
        final Object value = attributes.get(name);
        if(value == null)
        {
            return defaultVal;
        }
        else if(value instanceof Map)
        {
            @SuppressWarnings("unchecked")
            Map<String,Object> retVal = (Map<String,Object>) value;
            return retVal;
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Map");
        }
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <E extends Enum> E getEnumAttribute(Class<E> clazz, String name, Map<String,Object> attributes, E defaultVal)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultVal;
        }
        else if(clazz.isInstance(obj))
        {
            return (E) obj;
        }
        else if(obj instanceof String)
        {
            return (E) Enum.valueOf(clazz, (String)obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type " + clazz.getSimpleName());
        }
    }

    public static <E extends Enum<?>> E getEnumAttribute(Class<E> clazz, String name, Map<String,Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getEnumAttribute(clazz, name, attributes, null);
    }

    public static Boolean getBooleanAttribute(String name, Map<String,Object> attributes, Boolean defaultValue)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Boolean)
        {
            return (Boolean) obj;
        }
        else if(obj instanceof String)
        {
            return Boolean.parseBoolean((String) obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Boolean");
        }
    }


    public static boolean getBooleanAttribute(String name, Map<String, Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getBooleanAttribute(name, attributes, null);
    }

    public static Integer getIntegerAttribute(String name, Map<String,Object> attributes, Integer defaultValue)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Number)
        {
            return ((Number) obj).intValue();
        }
        else if(obj instanceof String)
        {
            return Integer.valueOf((String) obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Integer");
        }
    }

    public static Integer getIntegerAttribute(String name, Map<String,Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getIntegerAttribute(name, attributes, null);
    }

    public static Long getLongAttribute(String name, Map<String,Object> attributes, Long defaultValue)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Number)
        {
            return ((Number) obj).longValue();
        }
        else if(obj instanceof String)
        {
            return Long.valueOf((String) obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Long");
        }
    }

    public static <T> Set<T> getSetAttribute(String name, Map<String,Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getSetAttribute(name, attributes, Collections.<T>emptySet());
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> getSetAttribute(String name, Map<String,Object> attributes, Set<T> defaultValue)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Set)
        {
            return (Set<T>) obj;
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Set");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> Set<T> getEnumSetAttribute(String name, Map<String,Object> attributes, Class<T> clazz)
    {
        Object obj = attributes.get(name);
        String[] items= null;
        if(obj == null)
        {
            return null;
        }
        else if(obj instanceof Set)
        {
            Set<?> data= (Set<?>) obj;
            items = new String[data.size()];
            int i = 0;
            boolean sameType = true;
            for (Object object : data)
            {
                items[i++] = String.valueOf(object);
                if (clazz != object.getClass())
                {
                    sameType = false;
                }
            }
            if (sameType)
            {
                return (Set<T>)data;
            }
        }
        else if (obj instanceof String)
        {
            items = ((String)obj).split(",");
        }
        else if (obj instanceof String[])
        {
            items = (String[])obj;
        }
        else if (obj instanceof Object[])
        {
            Object[] objects = (Object[])obj;
            items = new String[objects.length];
            for (int i = 0; i < objects.length; i++)
            {
                items[i] = String.valueOf(objects[i]);
            }
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + "["+ obj + "] cannot be converted into set of enum of " + clazz);
        }
        Set<T> set = new HashSet<T>();
        for (int i = 0; i < items.length; i++)
        {
            T item = (T)Enum.valueOf(clazz, items[i]);
            set.add(item);
        }
        return set;
    }
}