package org.flowvisor.message;

import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.config.FVConfig;
import org.flowvisor.events.ConfigUpdateEvent;
import org.flowvisor.exceptions.UnhandledEvent;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.ofswitch.TopologyConnection;
import org.flowvisor.slicer.FVSlicer;
import org.openflow.protocol.OFCPortStatus;

/**
 * Send the port status message to each slice that uses this port
 *
 * extends OpenFlow v1.0 using Circuit Specification v0.3
 * 
 * @author Nikolaos Efstathiou (nikolaos.efstathiou@bristol.ac.uk) - Feb 21, 2013
 */

public class FVCPortStatus extends OFCPortStatus implements Classifiable,
		Slicable, TopologyControllable {

	@Override
	public void classifyFromSwitch(FVClassifier fvClassifier) {
		System.out.println("aaaaaaaaaaaaaa classify cport status");
		
		/*
		FVSlicer fvSlicer = FVMessageUtil.untranslateXid(this, fvClassifier);
		fvSlicer.sendMsg(this, fvClassifier);
		*/
		
		Short cport = Short.valueOf(this.getDesc().getPortNumber());
		System.out.println("aaaaaaaaaa cport status, cport: "+cport);
		System.out.println("aaaaaaaaaa cport status, xid: "+this.getXid());
		
		for (FVSlicer fvSlicer : fvClassifier.getSlicers()) {
			if (fvSlicer.portInSlice(cport)) {
				fvSlicer.sendMsg(this, fvClassifier);
			}
		}
		
		/*
		Short cport = Short.valueOf(this.getDesc().getPortNumber());

		byte reason = this.getReason();
		boolean updateSlicers = false;

		if (reason == OFPortReason.OFPPR_ADD.ordinal()) {
			FVLog.log(LogLevel.INFO, fvClassifier, "dynamically adding cport "
					+ cport);
			fvClassifier.addPort(this.getDesc()); // new port dynamically added
			updateSlicers = true;
		} else if (reason == OFPortReason.OFPPR_DELETE.ordinal()) {
			FVLog.log(LogLevel.INFO, fvClassifier, "dynamically removing cport "
					+ cport);
			fvClassifier.addPort(this.getDesc());
			updateSlicers = true;
		} else if (reason == OFPortReason.OFPPR_MODIFY.ordinal()) {
			// replace/update the port definition
			FVLog.log(LogLevel.INFO, fvClassifier, "modifying cport " + cport);
			fvClassifier.removePort(this.getDesc());
			fvClassifier.addPort(this.getDesc());
		} else if (reason == OFPortReason.OFPPR_BW_DOWN.ordinal()) {//FIXME:BW_DOWN action
			// replace/update the port definition
			FVLog.log(LogLevel.INFO, fvClassifier, "bandwidth not available in cport " + cport);
//			fvClassifier.removePort(this.getDesc());
//			fvClassifier.addPort(this.getDesc());
		} else if (reason == OFPortReason.OFPPR_BW_MODIFY.ordinal()) {//FIXME:BW_MODIFY action
			// bandwidth usage has changed added/deleted
			FVLog.log(LogLevel.INFO, fvClassifier, "bandwidth changed in cport " + cport);
//			fvClassifier.removePort(this.getDesc());
//			fvClassifier.addPort(this.getDesc());
		} else {
			FVLog.log(LogLevel.CRIT, fvClassifier, "unknown reason " + reason
					+ " in cport_status msg: " + this);
		}

		if (updateSlicers) {
			for (FVSlicer fvSlicer : fvClassifier.getSlicers()) {
				try {
					fvSlicer.handleEvent(new ConfigUpdateEvent(fvSlicer,
							FVConfig.FLOWSPACE));
				} catch (UnhandledEvent e) {
					FVLog
							.log(LogLevel.CRIT, fvSlicer,
									"tried to process ConfigUpdateEvent, but got: "
											+ e);
				}
			}
		}

		for (FVSlicer fvSlicer : fvClassifier.getSlicers()) {
			if (fvSlicer.portInSlice(cport)) {
				fvSlicer.sendMsg(this, fvClassifier);
			}
		}
		*/
	}

	@Override
	public void sliceFromController(FVClassifier fvClassifier, FVSlicer fvSlicer) {
		FVMessageUtil.dropUnexpectedMesg(this, fvSlicer);
	}

	
	
//	TODO:methods in topologyController for Cports!
	/**
	 * Got a dynamically added/removed port,e.g., from an HP add or remove it
	 * from the list of things we poke for topology
	 */
	@Override
	public void topologyController(TopologyConnection topologyConnection) {
		if (this.reason == OFPortReason.OFPPR_ADD.ordinal())
			topologyConnection.addPort(getDesc());
		else if (this.reason == OFPortReason.OFPPR_DELETE.ordinal())
			topologyConnection.removePort(getDesc());
	}
}
