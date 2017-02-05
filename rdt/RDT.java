
/**
 * @author mohamed
 *
 */
package rdt;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import static java.lang.Integer.min;
import static java.lang.Thread.sleep;

public class RDT {

	public static final int MSS = 100; // Max segement size in bytes
	public static final int RTO = 500; // Retransmission Timeout in msec
	public static final int ERROR = -1;
	public static final int MAX_BUF_SIZE = 3;  
	public static final int GBN = 1;   // Go back N protocol
	public static final int SR = 2;    // Selective Repeat
	public static final int protocol = GBN;
	
	public static double lossRate = 0.0;
	public static Random random = new Random(); 
	public static Timer timer = new Timer();	
	
	private DatagramSocket socket; 
	private InetAddress dst_ip;
	private int dst_port;
	private int local_port; 
	
	private RDTBuffer sndBuf;
	private RDTBuffer rcvBuf;
	
	private ReceiverThread rcvThread;

	RDT (String dst_hostname_, int dst_port_, int local_port_) 
	{
		local_port = local_port_;
		dst_port = dst_port_; 
		try {
			 socket = new DatagramSocket(local_port);
			 dst_ip = InetAddress.getByName(dst_hostname_);
		 } catch (IOException e) {
			 System.out.println("RDT constructor: " + e);
		 }
		sndBuf = new RDTBuffer(MAX_BUF_SIZE);
		if (protocol == GBN)
			rcvBuf = new RDTBuffer(1);
		else 
			rcvBuf = new RDTBuffer(MAX_BUF_SIZE);
		rcvThread = new ReceiverThread(rcvBuf, sndBuf, socket, dst_ip, dst_port);
		rcvThread.start();
	}
	
	RDT (String dst_hostname_, int dst_port_, int local_port_, int sndBufSize, int rcvBufSize)
	{
		local_port = local_port_;
		dst_port = dst_port_;
		 try {
			 socket = new DatagramSocket(local_port);
			 dst_ip = InetAddress.getByName(dst_hostname_);
		 } catch (IOException e) {
			 System.out.println("RDT constructor: " + e);
		 }
		sndBuf = new RDTBuffer(sndBufSize);
		if (protocol == GBN)
			rcvBuf = new RDTBuffer(1);
		else 
			rcvBuf = new RDTBuffer(rcvBufSize);
		
		rcvThread = new ReceiverThread(rcvBuf, sndBuf, socket, dst_ip, dst_port);
		rcvThread.start();
	}
	
	public static void setLossRate(double rate) {lossRate = rate;}


	// called by app
	// returns total number of sent bytes  
	public int send(byte[] data, int size) { // size is size of data
		//assume data.length == size
		int i = 0, j;//iterator i will go through all data up to 'size', j is iterator for segment data
		int sent = 0;//for the amount of byes sent

		do { //must go through all given data[size] but make new segment when at MSS
			RDTSegment segment = new RDTSegment(); // initialize a new segment
			int MAX_LEN = RDT.MSS - segment.HDR_SIZE; //max amount of data in segment

			for(j = 0; j < MAX_LEN && i < size; i++, j++){
				segment.data[j] = data[i];
			}
			segment.length = min(MAX_LEN, j);
			segment.printData();

			sent = sent + segment.length; //how i define the sequence numbers (delete these lines later)
			segment.seqNum = sent;

			sndBuf.putNext(segment);
			if(sndBuf.next > sndBuf.size - 1){ //if the buffer is full, send contents and reset buffer
				//System.out.println("Sending buffer contents");
				for(int k = 0; k < sndBuf.size; k++){
					//sndBuf.buf[k].timeoutHandler.run();
					Utility.udp_send(sndBuf.buf[k], socket, dst_ip, dst_port);
				}
				for(int k = 0; k < sndBuf.size; k++){
					sndBuf.buf[k] = null;
				}
				sndBuf.next = sndBuf.base;//move the start of buffer to where 'base' is

			}

		}while(i < size);

		if(sndBuf.next > 0){ //if not all segments were sent yet
			//System.out.println("Sending buffer contents ...");
			for(int k = 0; k < sndBuf.next; k++){ //CHANGE FOR FUTURE USE
				Utility.udp_send(sndBuf.buf[k], socket, dst_ip, dst_port);
			}
			for(int k = 0; k < sndBuf.next; k++){
				sndBuf.buf[k] = null;
			}
			sndBuf.next = sndBuf.base;

		}
		return sent;
	}
	/*
		public int send(byte[] data, int size) {
		//****** complete
		// divide data into segments
		// put each segment into sndBuf
		// send using udp_send() - void udp_send (RDTSegment seg, DatagramSocket socket, InetAddress ip, int port)
		// schedule timeout for segment(s)
		return size;
	}
 	*/


	// called by app
	// receive one segment at a time
	// returns number of bytes copied in buf
	public int receive (byte[] buf, int size){ //buf initially empty and can hold ONE FULL SEGMENT
		//*****  complete
		int i = 0;
		int amount_copy = 0;

		try {
			rcvBuf.semFull.acquire(); //take a full slot
			for(i = 0; i < rcvBuf.buf[0].data.length; i++){ //copy segment's data into our buffer
				buf[i] = rcvBuf.buf[0].data[i];
			}
			rcvBuf.buf[0] = null;
			rcvBuf.semEmpty.release(); // now an empty slot
			amount_copy = i;
		} catch (InterruptedException e){
			System.out.println("Receive: "+ e);
		}

		return amount_copy;
	}
	
	// called by app
	public void close() {
		// OPTIONAL: close the connection gracefully
		// you can use TCP-style connection termination process
	}
	
}  // end RDT class 


class RDTBuffer {
	public RDTSegment[] buf;
	public int size;	
	public int base;
	public int next;
	public Semaphore semMutex; // for mutual exclusion
	public Semaphore semFull; // #of full slots
	public Semaphore semEmpty; // #of Empty slots
	
	RDTBuffer (int bufSize) { //default is bufSize = 3 for sndBuf, 1 for sndBuf(GBN) on client
		buf = new RDTSegment[bufSize];
		for (int i=0; i<bufSize; i++) //initialize all to 0
			buf[i] = null;
		size = bufSize;
		base = next = 0;
		semMutex = new Semaphore(1, true);
		semFull =  new Semaphore(0, true);
		semEmpty = new Semaphore(bufSize, true);
	}

	
	// Put a segment in the next available slot in the buffer
	public void putNext(RDTSegment seg) {		
		try {
			semEmpty.acquire(); // wait for an empty slot 
			semMutex.acquire(); // wait for mutex 
				buf[next%size] = seg;
				next++;
			semMutex.release();
			semEmpty.release(); //for use in RDT.receive
			semFull.release(); // increase #of full slots -> for use by RDT.receive
		} catch(InterruptedException e) {
			System.out.println("Buffer put(): " + e);
		}
	}
	
	// return the next in-order segment
	public RDTSegment getNext() {
		//Complete
		return buf[(next-1)%size];  //returns the 'next' segment to be sent
	}
	
	// Put a segment in the *right* slot based on seg.seqNum
	// used by receiver in Selective Repeat
	public void putSeqNum (RDTSegment seg) {
		// ***** complete

	}
	
	// for debugging
	public void dump() {
		System.out.println("Dumping the receiver buffer ...");
		// Complete, if you want to 
		
	}
} // end RDTBuffer class



class ReceiverThread extends Thread { //working in background
	RDTBuffer rcvBuf, sndBuf;
	DatagramSocket socket;
	InetAddress dst_ip;
	int dst_port;

	int set_syn = 0; //set sequence number

	ReceiverThread (RDTBuffer rcv_buf, RDTBuffer snd_buf, DatagramSocket s,
			InetAddress dst_ip_, int dst_port_) {
		rcvBuf = rcv_buf;
		sndBuf = snd_buf;
		socket = s;
		dst_ip = dst_ip_;
		dst_port = dst_port_;
	}

	// *** complete
	// Essentially:  while(cond==true){  // may loop for ever if you will not implement RDT::close()
	//                socket.receive(pkt)
	//                seg = make a segment from the pkt
	//                verify checksum of seg
	//	              if seg contains ACK, process it potentially removing segments from sndBuf
	//                if seg contains data, put the data in rcvBuf and do any necessary
	//                             stuff (e.g, send ACK)
	//
	public void run() {
		byte[] buffer = new byte[RDT.MSS];// buffer at most needs MSS
		DatagramPacket pack = new DatagramPacket(buffer, buffer.length);
		while(true){
			try {
				socket.receive(pack);
				RDTSegment segment = new RDTSegment();
				byte[] data = pack.getData();
				makeSegment(segment, data);
				System.out.print("This was the segment's data: ");
				segment.printData();
				/*if(segment.computeChecksum() != segment.checksum){ //corrupted checksum?
					System.out.println("Throwing out corrupted segment: SeqNum: " + segment.seqNum);
					continue;
				}*/

				if(segment.containsAck()){ //update the send buffer by removing the corresponding seqNum
					System.out.println("Got the ACK for: " + segment.ackNum);//for debugging
					for(int i = 0; i < sndBuf.size; i++){
						if(sndBuf.buf[i].seqNum == segment.ackNum && sndBuf.buf != null){
							sndBuf.buf[i] = null;
						}
					}
					continue; //done here
				}
				if(segment.containsData()){ //counterpart to containsAck
					RDTSegment ackSegment = new RDTSegment();
					ackSegment.ackNum = segment.seqNum;
					System.out.println("Receiever Thread Found Data! ");// for debugging
					rcvBuf.putNext(ackSegment);
					System.out.println("Sending ACK: " + ackSegment.ackNum);
					Utility.udp_send(ackSegment, socket, dst_ip, dst_port);
				}

			} catch (IOException e) {
				System.out.println("UDP Rec: " + e);
			}

		}
	}


//	 create a segment from received bytes 
	void makeSegment(RDTSegment seg, byte[] payload) {
	
		seg.seqNum = Utility.byteToInt(payload, RDTSegment.SEQ_NUM_OFFSET);
		seg.ackNum = Utility.byteToInt(payload, RDTSegment.ACK_NUM_OFFSET);
		seg.flags  = Utility.byteToInt(payload, RDTSegment.FLAGS_OFFSET);
		seg.checksum = Utility.byteToInt(payload, RDTSegment.CHECKSUM_OFFSET);
		seg.rcvWin = Utility.byteToInt(payload, RDTSegment.RCV_WIN_OFFSET);
		seg.length = Utility.byteToInt(payload, RDTSegment.LENGTH_OFFSET);
		//Note: Unlike C/C++, Java does not support explicit use of pointers! 
		// we have to make another copy of the data
		// This is not effecient in protocol implementation
		for (int i=0; i< seg.length; i++)
			seg.data[i] = payload[i + RDTSegment.HDR_SIZE]; 
	}
	
} // end ReceiverThread class
//server port 5000 -> rdt/TestServer localhost 3000 5000
//client port 3000