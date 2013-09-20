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

#ifndef QPID_LINEARSTORE_JOURNALLOG_H_
#define QPID_LINEARSTORE_JOURNALLOG_H_

#include <string>

namespace qpid {
namespace qls_jrnl {

class JournalLog
{
public:
    typedef enum _log_level
    {
        LOG_TRACE = 0,
        LOG_DEBUG,
        LOG_INFO,
        LOG_NOTICE,
        LOG_WARN,
        LOG_ERROR,
        LOG_CRITICAL
    } log_level_t;

protected:
    JournalLog();
    virtual ~JournalLog();

public:
    virtual void log(log_level_t level, const std::string& jid, const std::string& log_stmt) const;
    virtual void log(log_level_t level, const char* jid, const char* const log_stmt) const;
    static const char* log_level_str(log_level_t ll);
};

}} // namespace qpid::qls_jrnl

#endif // QPID_LINEARSTORE_JOURNALLOG_H_
