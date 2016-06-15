package org.flowvisor.classifier;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.flowvisor.api.LinkAdvertisement;
import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;
import org.flowvisor.events.ConfigUpdateEvent;
import org.flowvisor.events.FVEvent;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.events.FVEventLoop;
import org.flowvisor.events.FVIOEvent;
import org.flowvisor.events.FVRequestTimeoutEvent;
import org.flowvisor.events.OFKeepAlive;
import org.flowvisor.events.TearDownEvent;
import org.flowvisor.exceptions.BufferFull;
import org.flowvisor.exceptions.MalformedOFMessage;
import org.flowvisor.exceptions.UnhandledEvent;
import org.flowvisor.flows.FlowDB;
import org.flowvisor.flows.FlowMap;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.flows.LinearFlowDB;
import org.flowvisor.flows.NoopFlowDB;
import org.flowvisor.io.FVMessageAsyncStream;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.log.SendRecvDropStats;
import org.flowvisor.log.SendRecvDropStats.FVStatsType;
import org.flowvisor.message.Classifiable;
import org.flowvisor.message.FVError;
import org.flowvisor.message.FVMessageFactory;
import org.flowvisor.message.SanityCheckable;
import org.flowvisor.slicer.FVSlicer;
import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFFeaturesRequest;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalCPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFError.OFHelloFailedCode;
import org.openflow.protocol.statistics.OFStatisticsType;

/**
 * Map OF messages from the switch to the appropriate slice
 *
 * Also handles all of the switch-specific but slice-general state and
 * rewriting.
 *
 * @author capveg
 * @author 
 *
 */

public class FVClassifier implements FVEventHandler, FVSendMsg {

	public static final int MessagesPerRead = 50; // for performance tuning
	FVEventLoop loop;
	SocketChannel sock;
	String switchName;
	boolean doneID;
	FVMessageAsyncStream msgStream;
	OFFeaturesReply switchInfo;
	Map<String, FVSlicer> slicerMap;
	XidTranslator xidTranslator;
	short missSendLength;
	FlowMap switchFlowMap;
	private boolean shutdown;
	Set<Short> activePorts;
	private final FVMessageFactory factory;
	OFKeepAlive keepAlive;
	SendRecvDropStats stats;
	private FlowDB flowDB;
	private boolean wantStatsDescHack;
	String floodPermsSlice; // the slice that has permission to use native
	
	// OPT links between optical switch and supported bandwidth
	// peers in the optical domain are discovered form FeaturesReply 
	List<LinkAdvertisement> circuitPortsBw;;

	// OFPP_FLOOD

	public FVClassifier(FVEventLoop loop, SocketChannel sock) {
		this.loop = loop;
		this.switchName = "unidentified:" + sock.toString();
		this.factory = new FVMessageFactory();
		this.stats = new SendRecvDropStats();
		try {
			this.msgStream = new FVMessageAsyncStream(sock, this.factory, this,
					this.stats);
		} catch (IOException e) {
			FVLog.log(LogLevel.CRIT, this, "IOException in constructor!");
			e.printStackTrace();
		}
		this.sock = sock;
		this.switchInfo = null;
		this.doneID = false;
		this.floodPermsSlice = ""; // disabled, at first
		this.slicerMap = new HashMap<String, FVSlicer>();
		this.xidTranslator = new XidTranslator();
		this.missSendLength = 128;
		this.switchFlowMap = null;
		this.activePorts = new HashSet<Short>();
		this.wantStatsDescHack = true;
		// OPT 
		this.circuitPortsBw = new ArrayList<LinkAdvertisement>();
		FVConfig.watch(this, FVConfig.FLOW_TRACKING);
		updateFlowTrackingConfig();
	}

	public short getMissSendLength() {
		return missSendLength;
	}

	public void setMissSendLength(short missSendLength) {
		this.missSendLength = missSendLength;
	}

	@Override
	public boolean needsConnect() {
		return false; // never want connect events
	}

	@Override
	public boolean needsRead() {
		return true; // always want read events
	}

	@Override
	public boolean needsWrite() {
		if (this.msgStream == null)
			return false;
		return this.msgStream.needsFlush();
	}

	@Override
	public boolean needsAccept() {
		return false;
	}

	public OFFeaturesReply getSwitchInfo() {
		return switchInfo;
	}

	// OPT added support for optical switch - update peer elements 
	// on FEATURESREPLY arrival
	public void setSwitchInfo(OFFeaturesReply switchInfo) {
		this.switchInfo = switchInfo;
		this.activePorts.clear();
		// OPT
		this.circuitPortsBw.clear();
		
		//from the features reply instance created in FV checks 
		// OPT if cports arraylist is null then defines the type of ports
		if (switchInfo.getCPorts() != null){
			for (OFPhysicalCPort phyCPort : switchInfo.getCPorts()){
				this.activePorts.add(phyCPort.getPortNumber());
				// OPT: here I have to update circuitPortsBw
				try {
					Long bw = phyCPort.getBandwidth().get(0);
					LinkAdvertisement la = new LinkAdvertisement(
							this.getDPID(), phyCPort.getPortNumber(), phyCPort.getPeerDatapathId(), phyCPort.getPeerPortNum());
					// First read generic flags 
					// need to do create a enum in OpenflowJ for the bitmask
					float base10;
					float spacing; 
					if ( ( (bw & (1 << 1)) != 0) )
						spacing = 0.1f;
					else
						spacing = 0.05f;
					if ( ( (bw & (1 << 2)) != 0) )
						base10 = 196.7f;
					else
						base10 = 190.7f;
					for (int l=0; l <54; l++) {	// iterate frequencies 
						if ( ( (bw & (1 << (l+10))) != 0) ) { // check bit at position 10,11,...63
							Float freq = base10 - (l*spacing);
							la.setAttribute(freq.toString(), "1");
						}					
					}
					this.circuitPortsBw.add(la);
					System.out.println(la.toString());
				}
				catch (IndexOutOfBoundsException e) {
					FVLog.log(LogLevel.MOBUG, this, "Error reading circuit port bandwidth field.");
				}
				
			}			
		}else{
			for (OFPhysicalPort phyPort : switchInfo.getPorts())
				this.activePorts.add(phyPort.getPortNumber());
		}
		
	}

	public boolean isPortActive(short port) {
		return this.activePorts.contains(port);
	}

	public void addPort(OFPhysicalPort phyPort) {
		for (Iterator<OFPhysicalPort> it = switchInfo.getPorts().iterator(); it
				.hasNext();) {
			// remove stale info, if it exists
			OFPhysicalPort lPort = it.next();
			if (lPort.getPortNumber() == phyPort.getPortNumber()) {
				it.remove();
				break;
			}
		}
		// update new info
		switchInfo.getPorts().add(phyPort);
		this.activePorts.add(phyPort.getPortNumber());
	}

	// OPT add optical port to the switch 
	public void addPort(OFPhysicalCPort phyCPort) {
		for (Iterator<OFPhysicalCPort> it = switchInfo.getCPorts().iterator(); it
				.hasNext();) {
			// remove state info, if it exists
			OFPhysicalCPort lCPort = it.next();
			if (lCPort.getPortNumber() == phyCPort.getPortNumber()) {
				it.remove();
				break;
			}
		}
		// update new info
		switchInfo.getCPorts().add(phyCPort);
		this.activePorts.add(phyCPort.getPortNumber());
	}
	
	
	public void removePort(OFPhysicalPort phyPort) {
		boolean found = false;
		for (Iterator<OFPhysicalPort> it = switchInfo.getPorts().iterator(); it
				.hasNext();) {
			OFPhysicalPort lPort = it.next();
			if (lPort.getPortNumber() == phyPort.getPortNumber()) {
				found = true;
				it.remove();
			}
		}
		if (!found)
			FVLog.log(LogLevel.INFO, this,
					"asked to remove non-existant port: ", phyPort);
		this.activePorts.remove(phyPort.getPortNumber());
	}

	// OPT remove circuit port 
	public void removePort(OFPhysicalCPort phyCPort) {
		boolean found = false;
		for (Iterator<OFPhysicalCPort> it = switchInfo.getCPorts().iterator(); it
				.hasNext();) {
			OFPhysicalCPort lCPort = it.next();
			if (lCPort.getPortNumber() == phyCPort.getPortNumber()) {
				found = true;
				it.remove();
			}
		}
		if (!found)
			FVLog.log(LogLevel.INFO, this,
					"asked to remove non-existant cport: ", phyCPort);
		this.activePorts.remove(phyCPort.getPortNumber());
	}
	
	public FVSlicer getSlicerByName(String sliceName) {
		if (this.slicerMap == null)
			return null; // race condition on shutdown
		synchronized (slicerMap) {
			return slicerMap.get(sliceName);
		}
	}

	public XidTranslator getXidTranslator() {
		return xidTranslator;
	}

	public void setXidTranslator(XidTranslator xidTranslator) {
		this.xidTranslator = xidTranslator;
	}

	/**
	 * on init, send HELLO, delete all flow entries, and send features request
	 *
	 * @throws IOException
	 */
	public void init() throws IOException {
		// send initial handshake
		sendMsg(new OFHello(), this);
		// delete all entries in the flowtable
		OFMatch match = new OFMatch();
		match.setWildcards(OFMatch.OFPFW_ALL);
		OFFlowMod fm = new OFFlowMod();
		fm.setMatch(match);
		fm.setCommand(OFFlowMod.OFPFC_DELETE);
		fm.setOutPort(OFPort.OFPP_NONE);
		fm.setBufferId(0xffffffff); // buffer to NONE
		sendMsg(fm, this);
		// request the switch's features
		sendMsg(new OFFeaturesRequest(), this);
		msgStream.flush();
		
		// OPT
//		TODO:add send CFLOW_MOD when circuit switch joins FV
//		// delete all entries in the circuit flowtable
//		OFConnect connect = new OFConnect();
//		connect.setWildcards(OFConnect.OFPCFW_ALL);
//		OFCFlowMod cfm = new OFCFlowMod();
//		cfm.setConnect(connect);
//		cfm.setCommand(OFCFlowMod.OFPFC_DELETE);
//		sendMsg(cfm, this);
//		// request the switch's features
//		sendMsg(new OFFeaturesRequest(), this);
//		msgStream.flush();
		
		
		

		// Schedule an event to timeout on the features request
		FVRequestTimeoutEvent requestTimeoutEvent = new FVRequestTimeoutEvent(this);
		requestTimeoutEvent.setExpireTime(System.currentTimeMillis() + FVRequestTimeoutEvent.WAIT_TIME);
		loop.addTimer(requestTimeoutEvent);

		int ops = SelectionKey.OP_READ;
		if (msgStream.needsFlush())
			ops |= SelectionKey.OP_WRITE;
		// this now calls FlowVisor.addHandler()
		loop.register(sock, ops, this);
		// start up keep alive events
		this.keepAlive = new OFKeepAlive(this, this, loop);
		this.keepAlive.scheduleNextCheck();

		try {
			this.wantStatsDescHack = FVConfig
					.getBoolean(FVConfig.STATS_DESC_HACK);
			FVConfig.watch(this, FVConfig.STATS_DESC_HACK);
		} catch (ConfigError e) {
			try {
				FVLog.log(LogLevel.WARN, this, "config: "
						+ FVConfig.STATS_DESC_HACK
						+ " not set; defaulting to off");
				this.wantStatsDescHack = false;
				FVConfig.setBoolean(FVConfig.STATS_DESC_HACK, false);
			} catch (ConfigError e1) {
				throw new RuntimeException("Tried to set default "
						+ FVConfig.STATS_DESC_HACK + "=true, but got: " + e1);
			}
		}
		updateFloodPerms();
	}

	/*
	 * Parse the flood_perms out of the config
	 *
	 * Check "switches.$dpid.flood_perms" if we know $dpid, else check
	 * "switches.default.flood_perms" also add it to the watch list
	 */

	void updateFloodPerms() {
		String dpid;
		if (this.doneID)
			dpid = FlowSpaceUtil.dpidToString(this.getDPID());
		else
			dpid = FVConfig.SWITCHES_DEFAULT;
		try {
			String entry = FVConfig.SWITCHES + FVConfig.FS + dpid + FVConfig.FS
					+ FVConfig.FLOOD_PERM;
			this.floodPermsSlice = FVConfig.getString(entry);
			FVLog.log(LogLevel.DEBUG, this, "giving flood perms to slice: "
					+ this.floodPermsSlice);
			// note: watch() is smart and won't double enter this
			FVConfig.watch(this, entry);
		} catch (ConfigError e) {
			// do nothing if no entry
		}
	}

	public void registerPong() {
		this.keepAlive.registerPong();
	}

	@Override
	public String getName() {
		return "classifier-" + switchName;
	}

	@Override
	public long getThreadContext() {
		return loop.getThreadContext();
	}


	/**
	 * @return the circuitPortsBw
	 */
	public List<LinkAdvertisement> getCircuitPortsBw() {
	    return circuitPortsBw;
	}
	
	@Override
	public void handleEvent(FVEvent e) throws UnhandledEvent {
		if (this.shutdown) {
			FVLog.log(LogLevel.WARN, this, "is shutdown: ignoring: " + e);
			return;
		}
		if (Thread.currentThread().getId() != this.getThreadContext()) {
			// this event was sent from a different thread context
			loop.queueEvent(e); // queue event
			return; // and process later
		}
		if (e instanceof FVIOEvent)
			handleIOEvent((FVIOEvent) e);
		else if (e instanceof OFKeepAlive)
			handleKeepAlive(e);
		else if (e instanceof ConfigUpdateEvent)
			updateConfig((ConfigUpdateEvent) e);
		else if (e instanceof TearDownEvent)
			this.tearDown();
		else if (e instanceof FVRequestTimeoutEvent)
			handleRequestTimeout();
		else
			throw new UnhandledEvent(e);
	}

	private void handleRequestTimeout(){
		if (switchName.startsWith("unidentified")){
			// Haven't got reply yet, tear down socket
			tearDown();
		}

	}

	private void handleKeepAlive(FVEvent e) {
		if (!this.keepAlive.isAlive()) {
			FVLog.log(LogLevel.WARN, this, "keepAlive timeout");
			this.tearDown();
			return;
		}
		this.keepAlive.sendPing();
		this.keepAlive.scheduleNextCheck();
	}

	/**
	 * Something in the config has changed; figure out what and re-cache it
	 *
	 * @param e
	 */
	private void updateConfig(ConfigUpdateEvent e) {
		String config = e.getConfig();
		FVLog.log(LogLevel.DEBUG, this, "got update: ", config);
		if (config.equals(FVConfig.FLOWSPACE)) {
			// update ourselves first
			connectToControllers(); // re-figure out who we should connect to
			// then tell everyone who depends on us (causality important :-)
			for (FVSlicer fvSlicer : slicerMap.values())
				this.loop.queueEvent(new ConfigUpdateEvent(e).setDst(fvSlicer));
		} else if (config.equals(FVConfig.FLOW_TRACKING)) {
			updateFlowTrackingConfig();
		} else if (config.endsWith(FVConfig.FLOOD_PERM)) {
			this.updateFloodPerms();
		} else {
			FVLog.log(LogLevel.WARN, this, "ignoring unknown config update: ",
					e.getConfig());
		}
	}

	private synchronized void updateFlowTrackingConfig() {
		try {
			if (FVConfig.getBoolean(FVConfig.FLOW_TRACKING))
				this.flowDB = new LinearFlowDB(this);
			else
				this.flowDB = new NoopFlowDB();
		} catch (ConfigError e) {
			// default to flow tracking == off
			this.flowDB = new NoopFlowDB();
		}
	}

	void handleIOEvent(FVIOEvent e) {
		int ops = e.getSelectionKey().readyOps();

		try {
			// read stuff, if need be
			if ((ops & SelectionKey.OP_READ) != 0) {
				List<OFMessage> newMsgs = msgStream
						.read(FVClassifier.MessagesPerRead);
				if (newMsgs != null) {
					for (OFMessage m : newMsgs) {
						if (m == null) {
							FVLog.log(LogLevel.ALERT, this,
									"got an unparsable OF Message ",
									"(msgStream.read() returned a null):",
									"trying to ignore it");
							continue;
						}
						FVLog.log(LogLevel.DEBUG, this, "read ", m);
						if ((m instanceof SanityCheckable)
								&& (!((SanityCheckable) m).isSane())) {
							FVLog.log(LogLevel.WARN, this,
									"msg failed sanity check; dropping: ", m);
							continue;
						}
						if (switchInfo != null) {
							classifyOFMessage(m);
							// mark this channel as still alive
							this.keepAlive.registerPong();
						} else
							handleOFMessage_unidenitified(m);

					}
				} else {
					throw new IOException("got EOF from other side");
				}
			}
			// write stuff if need be
			if ((ops & SelectionKey.OP_WRITE) != 0)
				msgStream.flush();
		} catch (IOException e1) {
			// connection to switch died; tear it down
			FVLog.log(LogLevel.INFO, this,
					"got IO exception; closing because : ", e1);
			this.tearDown();
			return;
		}
		// no need to setup for next select; done in eventloop
	}

	/**
	 * Close all slice connections and cleanup
	 */
	@Override
	public void tearDown() {
		FVLog.log(LogLevel.WARN, this, "tearing down");
		this.loop.unregister(this.sock, this);
		this.shutdown = true;
		try {
			this.sock.close();
			// shutdown each of the connections to the controllers
			Map<String, FVSlicer> tmpMap = slicerMap;
			slicerMap = null; // to prevent tearDown(slice) corruption
			for (FVSlicer fvSlicer : tmpMap.values())
				fvSlicer.closeDown(false);
		} catch (IOException e) {
			FVLog.log(LogLevel.WARN, this, "weird error on close:: ", e);
		}
		FVConfig.unwatch(this, FVConfig.FLOWSPACE); // unregister for FS updates
		FVConfig.unwatch(this, FVConfig.FLOW_TRACKING);
		FVConfig.unwatch(this, FVConfig.STATS_DESC_HACK);

		if (this.doneID)
			FVConfig.unwatch(this, FVConfig.SWITCHES + FVConfig.FS
					+ FlowSpaceUtil.dpidToString(getDPID()) + FVConfig.FS
					+ FVConfig.FLOOD_PERM);
		FVConfig
				.unwatch(this, FVConfig.SWITCHES + FVConfig.FS
						+ FVConfig.SWITCHES_DEFAULT + FVConfig.FS
						+ FVConfig.FLOOD_PERM);
		this.msgStream = null; // trick GC; prob not needed
	}

	/**
	 * Main function Pass this message on to the appropriate Slicer as defined
	 * by XID, FlowSpace, config, etc.
	 *
	 * @param m
	 */
	private void classifyOFMessage(OFMessage msg) {
		FVLog.log(LogLevel.DEBUG, this, "received from switch: ", msg);
		((Classifiable) msg).classifyFromSwitch(this); // msg specific handling
	}

	/**
	 * State machine for switches before we know which switch it is
	 *
	 * Wait for FEATURES_REPLY; ignore everything else
	 *
	 * @param m
	 *            incoming message
	 */
	private void handleOFMessage_unidenitified(OFMessage m) {
		System.out.println("handleOFMessage_unidenitified: " + m.toString() + " len: " + m.getLength());
		
		switch (m.getType()) {
		case HELLO: // aleady sent our hello; just NOOP here
			if (m.getVersion() != OFMessage.OFP_VERSION) {
				FVLog.log(LogLevel.WARN, this,
						"Mismatched version from switch ", sock, " Got: ", m
								.getVersion(), " Wanted: ",
						OFMessage.OFP_VERSION);
				FVError fvError = (FVError) this.factory
						.getMessage(OFType.ERROR);
				fvError.setErrorCode(OFHelloFailedCode.OFPHFC_INCOMPATIBLE);
				fvError.setVersion(m.getVersion());
				String errmsg = "we only support version "
						+ Integer.toHexString(OFMessage.OFP_VERSION)
						+ " and you are not it";
				fvError.setError(errmsg.getBytes());
				fvError.setErrorIsAscii(true);
				fvError.setLength((short) (FVError.MINIMUM_LENGTH + errmsg
						.length()));
				this.sendMsg(fvError, this);
				tearDown();
			}
			break;
		case ECHO_REQUEST:
			OFMessage echo_reply = new OFEchoReply();
			echo_reply.setXid(m.getXid());
			sendMsg(echo_reply, this);
			break;
		case FEATURES_REPLY:
			// OPT
			System.out.println("##### FEATURES_REPLY message received. Length: " + m.getLengthU());
//			System.out.println(m.toString());
			this.setSwitchInfo((OFFeaturesReply) m);
			
//			OFStatisticsRequest stats = new OFStatisticsRequest();
//			stats.setStatisticType(OFStatisticsType.DESC);
			
			switchName = "dpid=" + FlowSpaceUtil.dpidToString(this.getDPID());
			FVLog.log(LogLevel.INFO, this, "identified switch as " + switchName
					+ " on " + this.sock);
			FVConfig.watch(this, FVConfig.FLOWSPACE); // register for FS updates
			// FIXME
			this.connectToControllers(); // connect to controllers
			doneID = true;
			updateFloodPerms();
			break;
		default:
			FVLog.log(LogLevel.WARN, this, "Got unknown message type " + m
					+ " to unidentified switch");
		}
		
		
		
	}

	/**
	 * Figure out which slices have access to the switch and spawn a Slicer
	 * EventHandler for each of them. Also, close the connection to any slice
	 * that is no longer listed
	 *
	 * Also make a connection for the topology discovery daemon here if
	 * configured
	 *
	 * Assumes The switch is already been identified;
	 *
	 */
	private void connectToControllers() {
		Set<String> newSlices;
		synchronized (FVConfig.class) {
			this.switchFlowMap = FlowSpaceUtil.getSubFlowMap(this.switchInfo
					.getDatapathId());
			// this.switchFlowMap = FVConfig.getFlowSpaceFlowMap();
			newSlices = FlowSpaceUtil.getSlicesByDPID(this.switchFlowMap,
					this.switchInfo.getDatapathId());
		}
		StringBuffer strbuf = new StringBuffer();
		for (String sliceName : newSlices) {
			if (strbuf.length() > 0) // prune the last
				strbuf.append(',');
			strbuf.append(sliceName);
		}

		FVLog.log(LogLevel.DEBUG, this, "slices with access=", strbuf
				.toString());
		// foreach slice, make sure it has access to this switch
		for (String sliceName : newSlices) {
			if (slicerMap == null)
				throw new NullPointerException("slicerMap is null!?");
			if (!slicerMap.containsKey(sliceName)) {
				FVLog.log(LogLevel.INFO, this,
						"making new connection to slice " + sliceName);
				FVSlicer newSlicer = new FVSlicer(this.loop, this, sliceName);
				slicerMap.put(sliceName, newSlicer); // create new slicer in
				// this same EventLoop
				newSlicer.init(); // and start it up
			}
		}

		// foreach slice with previous access, make sure it still has access
		List<String> deletelist = new LinkedList<String>();
		for (String sliceName : slicerMap.keySet()) {
			if (!newSlices.contains(sliceName)) {
				// this slice no longer has access to this switch
				FVLog.log(LogLevel.INFO, this,
						"disconnecting: removed from FlowSpace: " + sliceName);
				slicerMap.get(sliceName).closeDown(false);
				deletelist.add(sliceName);
			}
		}
		// delete anything we marked in prev pass
		// should be able to do this in one loop, but can't
		// seem to iterate over a Map's keys and del inline
		for (String deleteSlice : deletelist)
			slicerMap.remove(deleteSlice);
	}

	public FlowMap getSwitchFlowMap() {
		return switchFlowMap;
	}

	public void setSwitchFlowMap(FlowMap switchFlowMap) {
		this.switchFlowMap = switchFlowMap;
	}

	/**
	 * Called by FVSlicer to tell us to forget about them
	 *
	 * @param sliceName
	 */
	public void tearDownSlice(String sliceName) {
		if (slicerMap != null) {
			slicerMap.remove(sliceName);
			FVLog.log(LogLevel.DEBUG, this, "tore down slice " + sliceName
					+ " on request");
		}
	}

	public String getSwitchName() {
		return this.switchName;
	}

	public String getConnectionName() {
		return FlowSpaceUtil.connectionToString(sock);
	}

	/**
	 * @return This switch's DPID
	 */
	public long getDPID() {
		if (this.switchInfo == null)
			return -1;
		return this.switchInfo.getDatapathId();
	}

	/**
	 * Send a message to the switch connected to this classifier
	 *
	 * @param msg
	 *            OFMessage
	 */

	public void sendMsg(OFMessage msg, FVSendMsg from) {
		if (this.msgStream != null) {
			FVLog.log(LogLevel.DEBUG, this, "send to switch:", msg);
			try {
				this.msgStream.testAndWrite(msg);
			} catch (BufferFull e) {
				FVLog.log(LogLevel.CRIT, this,
						"framing BUG; tearing down: got ", e);
				this.loop.queueEvent(new TearDownEvent(this, this));
				this.stats.increment(FVStatsType.DROP, from, msg);
			} catch (MalformedOFMessage e) {
				FVLog.log(LogLevel.CRIT, this, "BUG: bad msg: ", e);
				this.stats.increment(FVStatsType.DROP, from, msg);
			} catch (IOException e) {
				FVLog.log(LogLevel.WARN, this,
						"restarting connection, got IO error: ", e);
				this.tearDown();
			}
		} else {
			FVLog
					.log(LogLevel.WARN, this, "dropping msg: no connection: ",
							msg);
			this.stats.increment(FVStatsType.DROP, from, msg);
		}

	}

	public boolean isIdentified() {
		return this.switchInfo != null;
	}

	public Collection<FVSlicer> getSlicers() {
		// TODO: figure out if this is a copy and could have SYNCH issues
		return slicerMap.values();
	}

	@Override
	public void dropMsg(OFMessage msg, FVSendMsg from) {
		this.stats.increment(FVStatsType.DROP, from, msg);
	}

	@Override
	public SendRecvDropStats getStats() {
		return stats;
	}

	public void setFlowDB(FlowDB flowDB) {
		this.flowDB = flowDB;
	}

	public FlowDB getFlowDB() {
		return flowDB;
	}

	public SocketChannel getSocketChannel() {
		return this.sock;
	}

	public boolean wantStatsDescHack() {
		// TODO make this a configurable option
		return wantStatsDescHack;
	}

	/**
	 * @return the floodPermsSlice
	 */
	public String getFloodPermsSlice() {
		return floodPermsSlice;
	}


}
