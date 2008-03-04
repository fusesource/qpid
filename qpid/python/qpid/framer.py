#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

import struct, socket
from packer import Packer

FIRST_SEG = 0x08
LAST_SEG = 0x04
FIRST_FRM = 0x02
LAST_FRM = 0x01

class Frame:

  HEADER = "!2BHxBH4x"
  MAX_PAYLOAD = 65535 - struct.calcsize(HEADER)

  def __init__(self, flags, type, track, channel, payload):
    if len(payload) > Frame.MAX_PAYLOAD:
      raise ValueError("max payload size exceeded: %s" % len(payload))
    self.flags = flags
    self.type = type
    self.track = track
    self.channel = channel
    self.payload = payload

  def isFirstSegment(self):
    return bool(FIRST_SEG & self.flags)

  def isLastSegment(self):
    return bool(LAST_SEG & self.flags)

  def isFirstFrame(self):
    return bool(FIRST_FRM & self.flags)

  def isLastFrame(self):
    return bool(LAST_FRM & self.flags)

  def __str__(self):
    return "%s%s%s%s %s %s %s %r" % (int(self.isFirstSegment()),
                                     int(self.isLastSegment()),
                                     int(self.isFirstFrame()),
                                     int(self.isLastFrame()),
                                     self.type,
                                     self.track,
                                     self.channel,
                                     self.payload)

class Closed(Exception): pass

class Framer(Packer):

  HEADER="!4s4B"

  def __init__(self, sock):
    self.sock = sock

  def aborted(self):
    return False

  def write(self, buf):
#    print "OUT: %r" % buf
    while buf:
      try:
        n = self.sock.send(buf)
      except socket.timeout:
        if self.aborted():
          raise Closed()
        else:
          continue
      buf = buf[n:]

  def read(self, n):
    data = ""
    while len(data) < n:
      try:
        s = self.sock.recv(n - len(data))
      except socket.timeout:
        if self.aborted():
          raise Closed()
        else:
          continue
      except socket.error, e:
        if data != "":
          raise e
        else:
          raise Closed()
      if len(s) == 0:
        raise Closed()
#      print "IN: %r" % s
      data += s
    return data

  def read_header(self):
    return self.unpack(Framer.HEADER)

  def write_header(self, major, minor):
    self.pack(Framer.HEADER, "AMQP", 1, 1, major, minor)

  def write_frame(self, frame):
    size = len(frame.payload) + struct.calcsize(Frame.HEADER)
    track = frame.track & 0x0F
    self.pack(Frame.HEADER, frame.flags, frame.type, size, track, frame.channel)
    self.write(frame.payload)

  def read_frame(self):
    flags, type, size, track, channel = self.unpack(Frame.HEADER)
    payload = self.read(size - struct.calcsize(Frame.HEADER))
    return Frame(flags, type, track, channel, payload)
