/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.framing;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;

public class AMQFrameDecodingException extends AMQException
{
    public AMQFrameDecodingException(String message)
    {
        super(message);
    }

    public AMQFrameDecodingException(String message, Throwable t)
    {
        super(message, t);
    }

    public AMQFrameDecodingException(Logger log, String message)
    {
        super(log, message);
    }

    public AMQFrameDecodingException(Logger log, String message, Throwable t)
    {
        super(log, message, t);
    }

}
