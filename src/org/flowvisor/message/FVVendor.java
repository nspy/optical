package org.flowvisor.message;

import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.flows.FlowEntry;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.slicer.FVSlicer;
import org.openflow.protocol.OFCFlowMod;
import org.openflow.protocol.OFConnect;
import org.openflow.protocol.OFVendor;



//import com.sun.corba.se.impl.ior.ByteBuffer;
import java.nio.ByteBuffer;

public class FVVendor extends OFVendor implements Classifiable, Slicable {

	@Override
	public void classifyFromSwitch(FVClassifier fvClassifier) {
		// Just blindly forward vendor messages
		
		
		FVMessageUtil.untranslateXidAndSend(this, fvClassifier);
	}

	@Override
	public void sliceFromController(FVClassifier fvClassifier, FVSlicer fvSlicer) {
		// Just blindly forward vendor messages
	       ////ali-test
			//System.out.println("aaaaaaaaa inside vendor message size "+this.getLengthU());
			//System.out.println("aaaaaaaaa inside vendor message type "+this.getType());
			//System.out.println("aaaaaaaaa inside vendor message vendor "+this.getVendor());
			//System.out.println("aaaaaaaaa inside vendor message data "+this.getData().length);
			
			
			ByteBuffer bdata=ByteBuffer.wrap(this.getData());
			int t=bdata.getInt();
			//System.out.println("aaaaaaaaa inside vendor message data 2 "+t);
			
			
			if (t==2)
			{
				short in_port=bdata.getShort();
				short out_port=bdata.getShort();
				float wavelength=bdata.getFloat();
				//System.out.println("aaaaaaaaa POWER EQUALIZATION MESSAGE aaaaaaa"+in_port);
				//System.out.println("aaaaaaaaa inside vendor message in_port "+in_port);
				//System.out.println("aaaaaaaaa inside vendor message in_port "+out_port);
				//System.out.println("aaaaaaaaa inside vendor message wavelength "+wavelength);
				boolean matchingPowerEqualizationResult=matchPowerEqualization(in_port,out_port,wavelength,fvSlicer);
				//System.out.println("aaaaaaaaa matching power equalization result: "+matchingPowerEqualizationResult);
				if(matchingPowerEqualizationResult)
					FVMessageUtil.translateXidAndSend(this, fvClassifier, fvSlicer);
				
			}
			else
				FVMessageUtil.translateXidAndSend(this, fvClassifier, fvSlicer);
	}
	
	public boolean matchPowerEqualization(short in_port,short out_port,float wavelength, FVSlicer slicer){
		boolean res=true;		
		
		///get the dpid
	    String[] slicer_name_parts=slicer.getName().split("dpid=");
	    String dpid=slicer_name_parts[1];
	    //System.out.println("aaaaaaaaaaa DPID= "+dpid);
	    
	    /// matching with considering wavelength
	    /* 
	    res=flowspaceExist(slicer, dpid,in_port,wavelength);
	    if(res)
	    	res=flowspaceExist(slicer, dpid,out_port,wavelength);
	    */
	    
	    /// matching without considering wavelength
	    res=flowspaceExistWithoutWavelengthMatching(slicer, dpid,in_port,wavelength);
	    if(res)
	    	res=flowspaceExistWithoutWavelengthMatching(slicer, dpid,out_port,wavelength);
	    
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
				float flowEntry_wavelength= flowEntry.getRuleMatch().getWavelength();				
				if ((flowEntry_port==port)&&(flowEntry_wavelength==wavelength))
				{
					res=true;
					break;
				}
			}		
			  				
		}
		return res;
	}
	
	public boolean flowspaceExistWithoutWavelengthMatching(FVSlicer slicer, String dpid,int port,float wavelength)
	{
		boolean res=false;
		for (FlowEntry flowEntry : slicer.getFlowSpace().getRules())
		{
			String flowEntry_dpid=FlowSpaceUtil.dpidToString(flowEntry.getDpid());
			if(flowEntry_dpid.equals(dpid))
			{
				int flowEntry_port= flowEntry.getRuleMatch().getInputPort();
				float flowEntry_wavelength= flowEntry.getRuleMatch().getWavelength();				
				//if ((flowEntry_port==port)&&(flowEntry_wavelength==wavelength))
				if (flowEntry_port==port)
				{
					res=true;
					break;
				}
			}		
			  				
		}
		return res;
	}

}
