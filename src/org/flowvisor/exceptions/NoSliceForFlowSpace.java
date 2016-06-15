package org.flowvisor.exceptions;

import java.util.List;

public class NoSliceForFlowSpace extends FVException {

	private List<Integer> missingIDs;
	private List<Integer> allIDs;

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public NoSliceForFlowSpace(String msg){
		super(msg);

	}

	public NoSliceForFlowSpace(List<Integer> missingIDs, List<Integer> allIDs){
		super("");
		this.missingIDs = missingIDs;
		this.allIDs = allIDs;
	}

	public static String CreateErrorMessage(List<String> missingIDs, List<String> allIDs){
		String error = "The following FlowSpace IDs had missing slices: ||";
		for (int i = 0; i < missingIDs.size(); i++){
			error += missingIDs.get(i);
			if(i != missingIDs.size() - 1 ){
				error += ",";
			}
		}
		error += "|| ";

		error += "The following FlowSpace IDs were modified or created: ||";
		for(int i = 0; i < allIDs.size(); i++){
			error += allIDs.get(i);
			if(i != allIDs.size() - 1){
				error += ",";
			}
		}

		return error += "||";
	}

	public List<Integer> getAllIDs() {
		return this.allIDs;
	}

	public List<Integer> getMissingIDs() {
		return missingIDs;
	}

	public String getJSONMessage(){
		String msg = "FlowSpaces with missing slices:||";
		for(int i = 0; i < missingIDs.size(); i++){
			msg += missingIDs.get(i);
			if(i != missingIDs.size() - 1){
				msg += ",";
			}
		}
		return msg + "||";
	}
}
