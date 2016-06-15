package org.flowvisor.io;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.List;

import org.flowvisor.classifier.FVSendMsg;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.exceptions.BufferFull;
import org.flowvisor.exceptions.MalformedOFMessage;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.log.SendRecvDropStats;
import org.flowvisor.log.SendRecvDropStats.FVStatsType;
import org.openflow.io.OFMessageAsyncStream;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.OFMessageFactory;

public class FVMessageAsyncStream extends OFMessageAsyncStream {
	FVEventHandler source;
	FVSendMsg sender;
	SendRecvDropStats stats;
	int consecutiveDropped;
	static int DroppedMessageThreshold = 1000;

	public FVMessageAsyncStream(SocketChannel sock,
			OFMessageFactory messageFactory, FVEventHandler source,
			SendRecvDropStats stats) throws IOException {
		super(sock, messageFactory);
		// OF messages are small, so this is
		// a big performance boost
//		sock.socket().setTcpNoDelay(true);
		// OPT: just testing
		sock.socket().setTcpNoDelay(false);
//		sock.socket().setSendBufferSize(1024 * 1024);
		this.source = source;
		this.sender = (FVSendMsg) source; // FIXME: currently assumes all
											// FVEventHandlersimplement
											// FVSendMsg
		this.stats = stats;
		this.consecutiveDropped = 0;
	}

	public void testAndWrite(OFMessage m) throws BufferFull,
			MalformedOFMessage, IOException {
		System.out.println("FVMessageAsync.testAndWrite11: " + m.getType().toString() + 
				" Length: " + m.getLengthU() + ":" + m.getLength()); 
		this.flush();    // OPT: for testing - flush buffer 
		if (m.getType() == OFType.ECHO_REQUEST){
			m.setLength((short) 8);
			m.setLengthU(8);
		}
		if (m.getType() == OFType.FEATURES_REPLY){
			// OPT: compute and set the size of the FEATURES_REPLY message 
			//OFFeaturesReply mm = (OFFeaturesReply) m;
			//short n = mm.getN_cports();
			//if(n==0) n=(short)mm.getPorts().size();
			//short m_length = (short) (32 + (n * 80));
			//m.setLength(m_length);
			//m.setLengthU(m_length);
			System.out.println("About to send OF message11: " + m.getType().toString() + 
					" Length: " + m.getLengthU() + ":" + m.getLength());
		}
		int len = m.getLengthU();
		if (this.outBuf.remaining() < len) {
			System.out.println("Buffer remaining bytes: " + this.outBuf.remaining());
			this.flush(); // try a quick write to flush buffer
			if (this.outBuf.remaining() < len) {
				// drop message; throw error if we've dropped too many
				if (this.stats != null)
					this.stats.increment(FVStatsType.DROP, this.sender, m);
				this.consecutiveDropped++;
				FVLog.log(LogLevel.WARN, source,
						"wanted to write " + m.getLengthU() + " bytes to "
								+ outBuf.capacity()
								+ " byte buffer, but only have space for "
								+ outBuf.remaining() + " :: dropping msg " + m);
				if (consecutiveDropped > DroppedMessageThreshold) {
					throw new BufferFull("dropped more than "
							+ DroppedMessageThreshold
							+ " in a row; resetting connection");
				}
				return;
			} else
				FVLog.log(LogLevel.WARN, source,
						"Emergency buffer flush: was full, now ",
						outBuf.remaining(), " of ", outBuf.capacity(),
						" bytes free");
		}
		//ali-debug
		super.flush();
		//ali-debug
		//System.out.println("aaaaaaaaaa "+len);		
		//System.out.println("aaaaaaaaaa "+this.outBuf.position());
		//System.out.println("aaaaaaaaaa "+this.outBuf.remaining());
		this.outBuf.clear();
		//System.out.println("aaaaaaaaaa "+this.outBuf.reset());
		
		int start = this.outBuf.position();
		//ali-debug
		//if(len!=12)
		//if(m.getType()!=OFType.ERROR)
		//if(m.getType()!=OFType.FEATURES_REPLY)
		//if(m.getLength()!=8)
		//if(m.getType()!=OFType.ECHO_REQUEST)
		len = m.getLengthU();
		//System.out.println("aaaaaaaaaa len11  "+len);
		//OFMessage m2=new OFMessage();
		//Object a=(Object)m;
		//a.
		//((Object)m).clone();
		super.write(m);
		//if(len==992)
		//m.setLengthU(992);
		
		len = m.getLengthU();
		//System.out.println("aaaaaaaaaa len22  "+len);	
		//super.flush(); // OPT: send message now
		
		if (this.stats != null)
			this.stats.increment(FVStatsType.SEND, (FVSendMsg) source, m);
		
		this.consecutiveDropped = 0;
		int wrote = this.outBuf.position() - start;
		if (len != wrote) { // was the packet correctly written
			// no! back it out and throw an error
			this.outBuf.position(start);
			//ali-debug
			//System.out.println("aaaaaaaaaaaa "+len);
			//System.out.println("aaaaaaaaaaaa "+wrote);
			//System.out.println("aaaaaaaaaaaa "+start);
			//System.out.println("aaaaaaaaaaaa "+this.outBuf.position());
			FVLog.log(LogLevel.CRIT, null, "dropping bad OF Message: " + m);
			throw new MalformedOFMessage("len=" + len + ",wrote=" + wrote
					+ " msg=" + m);
		}
	}

	@Override
	public List<OFMessage> read(int limit) throws IOException {
		List<OFMessage> list = super.read(limit);
		if (list != null)
			for (OFMessage m : list)
			{
				System.out.println("source: " + this.source.toString() + " MSG type: " + m.getType());
				this.stats.increment(FVStatsType.RECV, this.sender, m);
				//System.out.println("hahahahahahhahaha");
			}
		return list;

	}
}
