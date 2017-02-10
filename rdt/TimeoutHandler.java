/**
 * @author mhefeeda
 *
 */

package rdt;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

class TimeoutHandler extends TimerTask {
	RDTBuffer sndBuf;
	RDTSegment seg;
	DatagramSocket socket;
	InetAddress ip;
	int port;

	TimeoutHandler (RDTBuffer sndBuf_, RDTSegment s, DatagramSocket sock,
			InetAddress ip_addr, int p) {
		sndBuf = sndBuf_;
		seg = s;
		socket = sock;
		ip = ip_addr;
		port = p;
	}
	public void run() {

		System.out.println(System.currentTimeMillis()+ ":Timeout for seg: " + seg.seqNum);
		System.out.flush();
		/*System.out.print("Buffer: ");
		for(int i = 0; i < sndBuf.size && sndBuf.buf[i] != null; i++)System.out.print(sndBuf.buf[i].seqNum + " ");
		System.out.println("");*/ //for debugging

		// complete
		switch(RDT.protocol){
			case RDT.GBN: //default for current implementation
				for(int i = 0; i < sndBuf.size && sndBuf.buf[i] != null; i++){ //resend whole window
					//if(sndBuf.buf[i].ackReceived) continue; //still need to resend
					System.out.println("Resending segment: " + sndBuf.buf[i].seqNum);
					Utility.udp_send(sndBuf.buf[i], socket, ip, port); //resend each segment in buffer
				}
				break;
			case RDT.SR:

				break;
			default:
				System.out.println("Error in TimeoutHandler:run(): unknown protocol");
		}

	}
} // end TimeoutHandler class

