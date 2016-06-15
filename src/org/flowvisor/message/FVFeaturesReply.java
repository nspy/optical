package org.flowvisor.message;

import java.util.ArrayList;
import java.util.List;

import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.ofswitch.OFSwitchAcceptor;
import org.flowvisor.ofswitch.TopologyConnection;
import org.flowvisor.slicer.FVSlicer;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalCPort;

public class FVFeaturesReply extends org.openflow.protocol.OFFeaturesReply
		implements Classifiable, Slicable, TopologyControllable {

	/**
	 * Prune the listed ports to only those that appear in the slice
	 */
	@Override
	public void classifyFromSwitch(FVClassifier fvClassifier) {
		FVSlicer fvSlicer = FVMessageUtil.untranslateXid(this, fvClassifier);
		if (fvSlicer == null) {
			FVLog.log(LogLevel.WARN, fvClassifier,
					" dropping msg with un-untranslatable xid: " + this);
			return;
		}
		//System.out.println("aaaaaaaaaaaaaaaaaaaa 0000000"+this.getLengthU());
		
		if (fvClassifier.getSwitchInfo().getCPorts() != null){
			//ali-debug
			this.pruneCPorts(fvSlicer); // remove ports that are not part of slice
		}else{
			//ali-debug
			this.prunePorts(fvSlicer); // remove ports that are not part of slice
		}
		
		// TODO: rewrite DPID if this is a virtual switch
		//System.out.println("aaaaaaaaaaaaaaaaaaaa 111111111"+this.getLengthU());
		fvSlicer.sendMsg(this, fvClassifier);
	}

	// rewrite the ports list to only the set of ports allowed by the slice
	// definition
	private void prunePorts(FVSlicer fvSlicer) {
		List<OFPhysicalPort> newPorts = new ArrayList<OFPhysicalPort>();
		for (OFPhysicalPort phyPort : this.getPorts()) {
			if (fvSlicer.getPorts().contains(phyPort.getPortNumber()))
				newPorts.add(phyPort);
		}
		this.setPorts(newPorts);
	}

	// rewrite the ports list to only the set of ports allowed by the slice
	// definition
	private void pruneCPorts(FVSlicer fvSlicer) {
		
		List<OFPhysicalCPort> newCPorts = new ArrayList<OFPhysicalCPort>();
		for (OFPhysicalCPort phyCPort : this.getCPorts()) {
			if (fvSlicer.getCPorts().contains(phyCPort.getPortNumber()))
			{
				newCPorts.add(phyCPort);
				//System.out.println("aaaaaaaaaaaa pruning CPorts: cport name "+phyCPort.getName());
				//System.out.println("aaaaaaaaaaaa pruning CPorts: cport name "+phyCPort.getName());
			}
		}
		//System.out.println("aaaaaaaaaaaa pruning CPorts: num "+newCPorts.size());
		//System.out.println("aaaaaaaaaaaa pruning CPorts: num "+newCPorts..size());
		Integer t=new Integer(newCPorts.size());
		this.n_cports=Byte.valueOf(t.toString());
		this.setCPorts(newCPorts);
		//System.out.println("aaaaaaaaaaaa number of cports : num "+newCPorts.size());
	}	
	
	@Override
	public void sliceFromController(FVClassifier fvClassifier, FVSlicer fvSlicer) {
		FVMessageUtil.dropUnexpectedMesg(this, fvSlicer);
	}

	@Override
	public String toString() {
		if(this.getPorts() != null)
			return super.toString() + ";ports=" + this.getPorts().size();
		else 
			return super.toString() + ";cports=" + this.getCPorts().size();
	}

	/**
	 * If a topologyConnection gets this message, then register it
	 *
	 */
	@Override
	public void topologyController(TopologyConnection topologyConnection) {
		topologyConnection.setFeaturesReply(this);
	}
}
