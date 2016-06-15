package org.flowvisor.message;

import java.util.List;

import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.exceptions.ActionDisallowedException;
import org.flowvisor.flows.FlowIntersect;
import org.flowvisor.flows.SliceAction;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.slicer.FVSlicer;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFError.OFBadActionCode;
import org.openflow.protocol.OFError.OFBadRequestCode;
import org.openflow.protocol.OFError.OFFlowModFailedCode;
import org.openflow.protocol.action.OFAction;

public class FVFlowMod extends org.openflow.protocol.OFFlowMod implements
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
		FVLog.log(LogLevel.DEBUG, fvSlicer, "recv from controller : ", this);
		//System.out.println("aaaaaaaaaaaa received flow mode from controller");
		FVMessageUtil.translateXid(this, fvClassifier, fvSlicer);

		// make sure that this slice can access this bufferID
		if (! fvSlicer.isBufferIDAllowed(this.getBufferId())) {
			FVLog.log(LogLevel.WARN, fvSlicer,
					"EPERM buffer_id ", this.getBufferId(), " disallowed: "
							, this);
			//System.out.println("aaaaaaaaaaaa inside fvflowmod");
			// ali-test//
			/*
			fvSlicer.sendMsg(FVMessageUtil.makeErrorMsg(
						OFBadRequestCode.OFPBRC_BUFFER_UNKNOWN, this), fvSlicer);
			*/
			//System.out.println("aaaaaaaaaaa switch name "+fvClassifier.getSwitchName());
			
			//System.out.println("aaaaaaaaaaa slice name "+fvSlicer..getSliceName());
			
			fvClassifier.sendMsg(this, fvSlicer);
			
			List<OFAction> actionsList = this.getActions();
			
			//System.out.println("aaaaaaaaaaa slice name "+fvSlicer.getSliceName());
			//System.out.println("aaaaaaaaaaa action list "+actionsList);
			//System.out.println("aaaaaaaaaaa action list size"+actionsList.size());
			
			/*
			for(int i=0;i<actionsList.size();i++)
			{
				System.out.println("aaaaaaaaaaa action list 1 " +actionsList.get(i).toString());
			}
			*/
			
			return;
		}

		// make sure the list of actions is kosher
		List<OFAction> actionsList = this.getActions();
		try {
			actionsList = FVMessageUtil.approveActions(actionsList, this.match,
					fvClassifier, fvSlicer);
		} catch (ActionDisallowedException e) {
			// FIXME : embed the error code in the ActionDisallowedException and
			// pull it out here
			FVLog.log(LogLevel.WARN, fvSlicer, "EPERM bad actions: ", this);
			fvSlicer.sendMsg(FVMessageUtil.makeErrorMsg(
					e.getError(), this), fvSlicer);
			return;
		}
		// expand this match to everything that intersects the flowspace
		List<FlowIntersect> intersections = fvSlicer.getFlowSpace().intersects(
				fvClassifier.getDPID(), this.match);

		int expansions = 0;
        OFFlowMod original = null;;
//	    try {
    		original = this.clone(); // keep an unmodified copy
//        } catch (CloneNotSupportedException c) {
            // will never happen
//        }

		int oldALen = FVMessageUtil.countActionsLen(this.getActions());
		this.setActions(actionsList);
		// set new length as a function of old length and old actions length
		this.setLength((short) (getLength() - oldALen + FVMessageUtil
				.countActionsLen(actionsList)));

		for (FlowIntersect intersect : intersections) {

			if (intersect.getFlowEntry().hasPermissions(
					fvSlicer.getSliceName(), SliceAction.WRITE)) {
				expansions++;
                FVFlowMod newFlowMod = null;   
//                try {
    				newFlowMod = (FVFlowMod) this.clone();
//                } catch (CloneNotSupportedException c) {
                    // never happens
//                }
				// replace match with the intersection
				newFlowMod.setMatch(intersect.getMatch());
				// update flowDBs
				fvSlicer.getFlowRewriteDB().processFlowMods(original,
						newFlowMod);
				fvClassifier.getFlowDB().processFlowMod(newFlowMod,
						fvClassifier.getDPID(), fvSlicer.getSliceName());
				// actually send msg
				fvClassifier.sendMsg(newFlowMod, fvSlicer);
			}
		}
		

		if (expansions == 0) {
			FVLog.log(LogLevel.WARN, fvSlicer, "dropping illegal fm: ", this);
			fvSlicer.sendMsg(FVMessageUtil.makeErrorMsg(
					OFFlowModFailedCode.OFPFMFC_EPERM, this), fvSlicer);
		} else
			FVLog.log(LogLevel.DEBUG, fvSlicer, "expanded fm ", expansions,
					" times: ", this);
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
}
