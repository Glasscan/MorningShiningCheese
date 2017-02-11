Jonathan Wong
jmw35

The rdt directory should contain:
  RDT.java
  RDTSegment.java
  TimeoutHandler.java
  Utility.java
  TestCLient.java
  TestServer.java
  Their respective class files, and this txt file.

Known Issues:
  The printing of data from RDT.receive is sequential and may overlap with
  some text from other System.out statements.

Testing and debugging:
  The directory was compiled via: javac rdt/*.java
  and run on twp terminals.
  one as: java rdt/TestServer localhost 5000 3000
  the other: java rdt/TestClient localhost 3000 5000

Implementation

*None of the bonus implementations were attempted

**Currently the delay RTO is set at 2000 to make debugging easier.

*** To view the sliding window, uncomment lines 260-262 in RDT.java

RDT.java

- The protocols GBN and SR must be manually set from within the file. This is
also true for the MSS.

- The RDT.send() function will make a total of ceiling(Data/MSS) segments
- Each segment is given a sequence number, seqNum, based on total of the data
sent and the length of the current segment. That is, if we send 3 segments of
10 bytes each, the first will have seqNum = 10, the second will have seqNum = 20
and the third will have seqNum = 30.
- The segments/packets are sent via the provided udp_send method
- After being sent, each segment's timeoutHandler is scheduled for repeated
execution. These will occur until the respective ACKs are received from the
server.

- The RDT.receive() is solely used for printing out the data that is received
at either end. If the rcvBuf is not empty then we take the data and copy it into
a buffer before freeing the copied data.


- The RDTBuffer.putNext() method was modified to account for the base. The
reason for this is that I wanted to have a 0-index sliding window. This way
the value located at sndBuf.buf[0] will always be the oldest unACKed segment.

- RDTBuffer.getNext() simply returns the "Next" segment.


- ReceiverThread.run() continuously checks for incoming data and packs them
into a new segment. If the segment is corrupted, we ignore it and wait for
another incoming segment.
- If the segment contains an ACK we check to see if there was a corresponding
seqNum in the sndBuf. The mutex is required as to not alter data while another
thread is accessing it. If there was a match then we mark ackReceived for that
statement as true and cancel the timeoutHandler for that segment.
- The window will slide ONLY if the oldest segment in the buffer has been ACKed.
- If the segment contains data then we send an appropriate ACK statement and
store the received into rcvBuf.


RDTSegment.java

- Due to my implementation, containsACK() will be true when ackNum > 0. The
default value for any segment is ackNum = 0, so any segment that did not
explicitly receive and ack will have this value.

- containsData returns true of length > 0. In the RDT.send() method, the length
is defined by the amount of data. If there was any data at all, then length > 0.

- computeChecksum() and isValid() and used almost as provided. I decided not
to include the checksum itself in the calculation.


TimeoutHandler.java

  case RDT.GBN
  - when the timeoutHandler is run with protocol GBN, every single segment that
  was stored in the sdnBuf is resent. If we have multiple timeouts, then a
  segment may be resent numerous times at once.

  case RDT.SR
  - when run using SR, only that one segment that timed out will be resent.
