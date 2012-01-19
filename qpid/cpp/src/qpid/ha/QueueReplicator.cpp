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

#include "QueueReplicator.h"
#include "ReplicatingSubscription.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include <boost/shared_ptr.hpp>
#include <ostream>

namespace {
const std::string QPID_REPLICATOR_("qpid.replicator-");
const std::string TYPE_NAME("qpid.queue-replicator");
const std::string QPID_SYNC_FREQUENCY("qpid.sync_frequency");
}

namespace qpid {
namespace ha {
using namespace broker;
using namespace framing;

const std::string QueueReplicator::DEQUEUE_EVENT_KEY("qpid.dequeue-event");
const std::string QueueReplicator::POSITION_EVENT_KEY("qpid.position-event");

std::string QueueReplicator::replicatorName(const std::string& queueName) {
    return QPID_REPLICATOR_ + queueName;
}

std::ostream& operator<<(std::ostream& o, const QueueReplicator& qr) {
    return o << "HA: Backup queue " << qr.queue->getName() << ": ";
}

QueueReplicator::QueueReplicator(boost::shared_ptr<Queue> q, boost::shared_ptr<Link> l)
    : Exchange(replicatorName(q->getName()), 0, q->getBroker()), queue(q), link(l)
{
    QPID_LOG(info, *this << "Created, settings: " << q->getSettings());

    queue->getBroker()->getLinks().declare(
        link->getHost(), link->getPort(),
        false,              // durable
        queue->getName(),   // src
        getName(),          // dest
        "",                 // key
        false,              // isQueue
        false,              // isLocal
        "",                 // id/tag
        "",                 // excludes
        false,              // dynamic
        0,                  // sync?
        boost::bind(&QueueReplicator::initializeBridge, this, _1, _2)
    );
}

QueueReplicator::~QueueReplicator() {
    // FIXME aconway 2011-12-21: causes race condition? Restore.
//     queue->getBroker()->getLinks().destroy(
//         link->getHost(), link->getPort(), queue->getName(), getName(), string());
}

// Called in a broker connection thread when the bridge is created.
void QueueReplicator::initializeBridge(Bridge& bridge, SessionHandler& sessionHandler)
{
    framing::AMQP_ServerProxy peer(sessionHandler.out);
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());
    framing::FieldTable settings;

    // FIXME aconway 2011-12-09: Failover optimization removed.
    // There was code here to re-use messages already on the backup
    // during fail-over. This optimization was removed to simplify
    // the logic till we get the basic replication stable, it
    // can be re-introduced later. Last revision with the optimization:
    // r1213258 | QPID-3603: Fix QueueReplicator subscription parameters.

    // Clear out any old messages, reset the queue to start replicating fresh.
    queue->purge();
    queue->setPosition(0);

    settings.setInt(ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION, 1);
    // TODO aconway 2011-12-19: optimize.
    settings.setInt(QPID_SYNC_FREQUENCY, 1);
    peer.getMessage().subscribe(args.i_src, args.i_dest, 0/*accept-explicit*/, 1/*not-acquired*/, false, "", 0, settings);
    peer.getMessage().flow(getName(), 0, 0xFFFFFFFF);
    peer.getMessage().flow(getName(), 1, 0xFFFFFFFF);
    QPID_LOG(debug, *this << "Activated bridge from " << args.i_src << " to " << args.i_dest);
}

namespace {
template <class T> T decodeContent(Message& m) {
    std::string content;
    m.getFrames().getContent(content);
    Buffer buffer(const_cast<char*>(content.c_str()), content.size());
    T result;
    result.decode(buffer);
    return result;
}
}

void QueueReplicator::dequeue(SequenceNumber n,  const sys::Mutex::ScopedLock&) {
    // Thread safe: only calls thread safe Queue functions.
    if (queue->getPosition() >= n) { // Ignore dequeus we  haven't reached yet
        QueuedMessage message;
        if (queue->acquireMessageAt(n, message)) {
            queue->dequeue(0, message);
            QPID_LOG(trace, *this << "Dequeued message "<< message.position);
        }
    }
}

// Called in connection thread of the queues bridge to primary.
void QueueReplicator::route(Deliverable& msg, const std::string& key, const FieldTable*)
{
    sys::Mutex::ScopedLock l(lock);
    if (key == DEQUEUE_EVENT_KEY) {
        SequenceSet dequeues = decodeContent<SequenceSet>(msg.getMessage());
        QPID_LOG(trace, *this << "Received dequeues: " << dequeues);
        //TODO: should be able to optimise the following
        for (SequenceSet::iterator i = dequeues.begin(); i != dequeues.end(); i++)
            dequeue(*i, l);
    } else if (key == POSITION_EVENT_KEY) {
        SequenceNumber position = decodeContent<SequenceNumber>(msg.getMessage());
        QPID_LOG(trace, *this << "Advance position: from " << queue->getPosition()
                 << " to " << position);
        assert(queue->getPosition() <= position);
         //TODO aconway 2011-12-14: Optimize this?
        for (SequenceNumber i = queue->getPosition(); i < position; ++i)
            dequeue(i,l);
        queue->setPosition(position);
    } else {
        msg.deliverTo(queue);
        QPID_LOG(trace, *this << "Enqueued message " << queue->getPosition());
    }
}

// Unused Exchange methods.
bool QueueReplicator::bind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::unbind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::isBound(boost::shared_ptr<Queue>, const std::string* const, const FieldTable* const) { return false; }
std::string QueueReplicator::getType() const { return TYPE_NAME; }

}} // namespace qpid::broker
