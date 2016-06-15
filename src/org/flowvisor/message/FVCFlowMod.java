package org.flowvisor.message;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.flowvisor.api.LinkAdvertisement;
import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.config.ConfFlowMapEntry;
import org.flowvisor.exceptions.ActionDisallowedException;
import org.flowvisor.flows.FlowEntry;
import org.flowvisor.flows.FlowIntersect;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.flows.SliceAction;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.slicer.FVSlicer;
import org.openflow.protocol.OFCFlowMod;
import org.openflow.protocol.OFConnect;
import org.openflow.protocol.OFPhysicalCPort;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFError.OFBadActionCode;
import org.openflow.protocol.OFError.OFBadRequestCode;
import org.openflow.protocol.OFError.OFCFlowModFailedCode;
import org.openflow.protocol.OFError.OFFlowModFailedCode;
import org.openflow.protocol.action.OFAction;

public class FVCFlowMod extends org.openflow.protocol.OFCFlowMod implements
		Classifiable, Slicable, Cloneable {

	@Override
	public void classifyFromSwitch(FVClassifier fvClassifier) {
		FVMessageUtil.dropUnexpectedMesg(this, fvClassifier);
	}

	/**
	 * FlowMod slicing
	 *
	 * 1) make sure all actions are ok
	 *
	 * 2) expand this FlowMod to the intersection of things in the given match
	 * and the slice's flowspace
	 */

	@Override
	public void sliceFromController(FVClassifier fvClassifier, FVSlicer fvSlicer) {
		FVLog.log(LogLevel.DEBUG, fvSlicer, "recv from controller: ", this);
		FVMessageUtil.translateXid(this, fvClassifier, fvSlicer);

//		// make sure that this slice can access this bufferID
//		if (! fvSlicer.isBufferIDAllowed(this.getBufferId())) {
//			FVLog.log(LogLevel.WARN, fvSlicer,
//					"EPERM buffer_id ", this.getBufferId(), " disallowed: "
//							, this);
//			fvSlicer.sendMsg(FVMessageUtil.makeErrorMsg(
//						OFBadRequestCode.OFPBRC_BUFFER_UNKNOWN, this), fvSlicer);
//			return;
//		}

		// make sure the list of actions is kosher
		// TODO no match field in OFCFlowMod
//		List<OFAction> actionsList = this.getActions();
//		try {
//			actionsList = FVMessageUtil.approveActions(actionsList, this.match,
//					fvClassifier, fvSlicer);
//		} catch (ActionDisallowedException e) {
//			// FIXME : embed the error code in the ActionDisallowedException and
//			// pull it out here
//			FVLog.log(LogLevel.WARN, fvSlicer, "EPERM bad actions: ", this);
//			fvSlicer.sendMsg(FVMessageUtil.makeErrorMsg(
//					e.getError(), this), fvSlicer);
//			return;
//		}
		// expand this match to everything that intersects the flowspace
		
		// TODO no match field in OFCFlowMod 
//		List<FlowIntersect> intersections = fvSlicer.getFlowSpace().intersects(
//				fvClassifier.getDPID(), this.match);

		int expansions = 0;
        OFCFlowMod original = null;
//	    try {
    		original = this.clone(); // keep an unmodified copy
//        } catch (CloneNotSupportedException c) {
            // will never happen
        	
//        }

//		int oldALen = FVMessageUtil.countActionsLen(this.getActions());
//		this.setActions(actionsList);
//		// set new length as a function of old length and old actions length
//		this.setLength((short) (getLength() - oldALen + FVMessageUtil
//				.countActionsLen(actionsList)));
//
//		for (FlowIntersect intersect : intersections) {
//
//			if (intersect.getFlowEntry().hasPermissions(
//					fvSlicer.getSliceName(), SliceAction.WRITE)) {
//				expansions++;
////                FVFlowMod newFlowMod = null;   
//				FVCFlowMod newCFlowMod = (FVCFlowMod) this.clone();
////                try {
////    				newFlowMod = (FVFlowMod) this.clone();
////                } catch (CloneNotSupportedException c) {
////                    // never happens
////                }
//                
//				// replace match with the intersection
//				newCFlowMod.setMatch(intersect.getMatch());
//				// update flowDBs
//				fvSlicer.getFlowRewriteDB().processFlowMods(original,
//						newCFlowMod);
//				fvClassifier.getFlowDB().processFlowMod(newFlowMod,
//						fvClassifier.getDPID(), fvSlicer.getSliceName());
//				// actually send msg
//				fvClassifier.sendMsg(newFlowMod, fvSlicer);
//			}
//		}
		
//FIXME:needed??? cflowmod error type?
    	/*
		if (expansions == 0) {
			FVLog.log(LogLevel.WARN, fvSlicer, "dropping illegal cfm: ", this);
			fvSlicer.sendMsg(FVMessageUtil.makeErrorMsg(
					OFCFlowModFailedCode.OFPCFMFC_MISMATCH, this), fvSlicer);
		} else
			FVLog.log(LogLevel.DEBUG, fvSlicer, "expanded cfm ", expansions,
					" times: ", this);
		*/
    		//ali-test//
    		//fvSlicer.getFlowSpace().
    		//System.out.println("aaaaaa inside fvcflowmod before sending message to switch");
    		//System.out.println("aaaaaa flow space rules"+fvSlicer.getFlowSpace().countRules());
    		//System.out.println("aaaaaa flow space rules size"+fvSlicer.getFlowSpace().getRules().size());
    		//System.out.println("aaaaaa fvSlicer name "+fvSlicer.getName());
    		//System.out.println("aaaaaa fvSlicer slice name "+fvSlicer.getSliceName());
    		//System.out.println("aaaaaa fvSlicer slice name cports"+fvSlicer.getCPorts().toString());
    		//System.out.println("aaaaaa fvSlicer slice name cports size"+fvSlicer.getCPorts().size());
    		
    		
    			for (FlowEntry flowEntry : fvSlicer.getFlowSpace().getRules())
    			{
    				//System.out.println("aaaaaa fvSlicer of match port : "+flowEntry.getRuleMatch().getInputPort());
    				//System.out.println("aaaaaa fvSlicer of match wavelength : "+flowEntry.getRuleMatch().getWavelength());
    				for(int i=0;i<flowEntry.getActionsList().size();i++)
    				{
    					//if(flowEntry.getActionsList().get(i).)
    					//System.out.println("aaaaaa fvSlicer of match : "+flowEntry.getActionsList().get(i).toString());
    				}    				
    			}
    		//System.out.println("aaaaaaaa cflow mode message information: ");
    		//System.out.println("aaaaaaa message length"+original.getLengthU());
    		OFConnect connect=original.getConnect();
    		//System.out.println("aaaaaaa number of components: "+connect.getNum_components());
    		//System.out.println("aaaaaaa number of input wport: "+connect.getIn_wport().size());
    		//System.out.println("aaaaaaa number of output wport: "+connect.getOut_wport().size());
    		//System.out.println("aaaaaaa in wports:");
    		for(int i=0;i<connect.getIn_wport().size();i++)
    		{
    			//System.out.println("aaaaaaa in port: "+connect.getIn_wport().get(i).getPort_num()+" wavelength: "+connect.getIn_wport().get(i).getWavelength());
    			List<Float> waves=parse_bandwidth(connect.getIn_wport().get(i).getWavelength());
    			/*
    			for(int j=0;j<waves.size();j++)
    			{
    				System.out.println("aaaaaaa in port frequency: "+waves.get(j));
    			}
    			*/
    		}
    		
    		for(int i=0;i<connect.getOut_wport().size();i++)
    		{
    			//System.out.println("aaaaaaa in port: "+connect.getOut_wport().get(i).getPort_num()+" wavelength: "+connect.getOut_wport().get(i).getWavelength());
    			List<Float> waves=parse_bandwidth(connect.getOut_wport().get(i).getWavelength());
    			/*
    			for(int j=0;j<waves.size();j++)
    			{
    				System.out.println("aaaaaaa out port frequency: "+waves.get(j));
    			}
    			*/
    		}
    		boolean matchCflowModResult=matchCflowMod(original, fvSlicer);
    		//System.out.println("aaaaaaaa matching cflowmod "+matchCflowModResult);
    		
    		//System.out.println("aaaaaa fvSlicer slice name cports size"+fvSlicer.getCPorts().size());
    		
    		//fvSlicer.get
    		/// check controller can send cflow_mod
    		if (matchCflowModResult)
    		{
    		   System.out.println("aaaaaaaaaaaa matching cflow mode is successful");
    		   fvClassifier.sendMsg(original, fvSlicer);
    		   //System.out.println("aaaaaaa inside fvcflowmod after sending message to switch");
    		}
    		else  ///// otherwise, send error message to controller 
    		{
    			System.out.println("aaaaaaaaaaaa matching cflow mode is not successful");
    			FVLog.log(LogLevel.WARN, fvSlicer, "dropping illegal cfm: ", this);
    			fvSlicer.sendMsg(FVMessageUtil.makeErrorMsg(
    					OFFlowModFailedCode.OFPFMFC_EPERM, this), fvSlicer);
    		}
    		
    		
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return super.toString() + ";actions="
				+ FVMessageUtil.actionsToString(this.getActions());
	}
	
	// ali-test//
	public boolean matchCflowMod(OFCFlowMod message, FVSlicer slicer){
		boolean res=true;
		
		OFConnect connect=message.getConnect();
		
		///get the dpid
	    String[] slicer_name_parts=slicer.getName().split("dpid=");
	    String dpid=slicer_name_parts[1];
	    //System.out.println("aaaaaaaaaaa DPID= "+dpid);
		
		///// match in ports 
	    
		for(int i=0;i<connect.getIn_wport().size() && res;i++)
		{
			int cflowMod_port=connect.getIn_wport().get(i).getwPort();
			//long cflowMod_wave=connect.getIn_wport().get(i).getWavelength();
			
			List<Float> waves=parse_bandwidth(connect.getIn_wport().get(i).getWavelength());
			
			//System.out.println("aaaaaaa in port: "+connect.getIn_wport().get(i).getPort_num()+" wavelength: "+connect.getIn_wport().get(i).getWavelength());
			
			for(int j=0;j<waves.size()&& res;j++)
			{
				//System.out.println("aaaaaaa out port frequency: "+waves.get(j));
				//System.out.println("aaaaaaaaaa matching dpid: "+dpid+"  port: "+cflowMod_port+"  wavelength: "+waves.get(j));
				res=flowspaceExist(slicer,dpid,cflowMod_port,(float)waves.get(j));
				//System.out.println("aaaaaaaaaa matching result: "+res);
			}		
			
		}	
		
   	    ///// match out ports 
		
		for(int i=0;i<connect.getOut_wport().size() && res;i++)
		{
			int cflowMod_port=connect.getOut_wport().get(i).getwPort();
			//long cflowMod_wave=connect.getIn_wport().get(i).getWavelength();
			
			List<Float> waves=parse_bandwidth(connect.getOut_wport().get(i).getWavelength());
			
			//System.out.println("aaaaaaa in port: "+connect.getIn_wport().get(i).getPort_num()+" wavelength: "+connect.getIn_wport().get(i).getWavelength());
			
			for(int j=0;j<waves.size()&& res;j++)
			{
				//System.out.println("aaaaaaa out port frequency: "+waves.get(j));
				//System.out.println("aaaaaaaaaa matching dpid: "+dpid+"  port: "+cflowMod_port+"  wavelength: "+waves.get(j));
				res=flowspaceExist(slicer,dpid,cflowMod_port,(float)waves.get(j));
				//System.out.println("aaaaaaaaaa matching result: "+res);
			}			
			
		}	
		//
		
		return res;
	}
	
	public boolean flowspaceExist(FVSlicer slicer, String dpid,int port,float wavelength)
	{
		boolean res=false;
		for (FlowEntry flowEntry : slicer.getFlowSpace().getRules())
		{
			String flowEntry_dpid=FlowSpaceUtil.dpidToString(flowEntry.getDpid());
			if(flowEntry_dpid.equals(dpid))
			{
				int flowEntry_port= flowEntry.getRuleMatch().getInputPort();
				short flowEntry_wavelength= flowEntry.getRuleMatch().getDataLayerVirtualLan();				
				if ((flowEntry_port==port)&&(flowEntry_wavelength==wavelength))
				{
					res=true;
					break;
				}
			}		
			  				
		}
		return res;
	}
	
public List<Float> parse_bandwidth(long bw)
{
	//BigInteger bigInt=new 
	List<Float> res=new ArrayList<Float>();	
	
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
	
	/// ali-test// this is to disable l-band //need to be removed 
	spacing = 0.1f;
	base10 = 196.7f;
	/////////
	
	String bits=Long.toBinaryString(bw);
	
	//// append zeros to bits on left
	String bits64="";
	for(int i=0;i<64-bits.length();i++)
		bits64+='0';
	for(int i=0;i<bits.length();i++)
		bits64+=bits.charAt(i);
	//System.out.println("aaaaaaaaaa binary bits64= "+bits64);
	/// reverse each 8 bits ///
	char[] bits64Array=bits64.toCharArray();
	for(int i=0;i<8;i++)
	{
		int firstindex=i*8;
		int lastindex=i*8+7;
		while (firstindex<lastindex)
		{
			char t=bits64Array[firstindex];
			bits64Array[firstindex]=bits64Array[lastindex];
			bits64Array[lastindex]=t;
			firstindex++;
			lastindex--;
		}
			
	}
	bits64=String.valueOf(bits64Array);
	//System.out.println("aaaaaaaaaa binary bits64 after reversing= "+bits64);
	/*
	while (bits.length()<64)
		bits=bits+'0';
	for(int i=bits.length()-1;i>=0;i--)
		bits2=bits2+bits.charAt(i);
	System.out.println("aaaaaaaaaa binary bw2= "+bits2);
	*/
	for(int l=0; l <54; l++){
		
		try{
			//System.out.println("aaaaaaaaaa bit= "+bits2.charAt(l+10));
			if(bits64.charAt(l+10)=='1')
			{
				//System.out.println("aaaaaaaaaa freqqqq= "+(base10 - (l*spacing)));
				Float freq = base10 - (l*spacing);
				res.add(freq);
				
			}
		   } catch (Exception ex)
		      {
			    break;
		      }
	}
	/*
	for (int l=0; l <54; l++) {	// iterate frequencies 
		long lll=(1 << (l+10));
		System.out.println("aaaaaaaaaa lll)= "+lll);
		System.out.println("aaaaaaaaaa binary bw= "+Long.toBinaryString(lll));
		if ( ( (bw & lll) != 0) ) { // check bit at position 10,11,...63
			System.out.println("aaaaaaaaaa l= "+l);
			System.out.println("aaaaaaaaaa bw= "+bw);
			System.out.println("aaaaaaaaaa (bw & (1 << (l+10)))= "+(bw & (1 << (l+10))));
			Float freq = base10 - (l*spacing);
			res.add(freq);
			
		}					
	}
	*/
	return res;
}

///
/*
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
 
 ///
  
  */

}
