package org.flowvisor.api;

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.List;

import org.flowvisor.exceptions.FlowEntryNotFound;
import org.flowvisor.exceptions.NoSliceForFlowSpace;
import org.flowvisor.exceptions.PermissionDeniedException;
import org.flowvisor.flows.FlowEntry;

public interface FVUserAPIJSON extends FVUserAPI {

	/**
	 * Lists all the flowspace this user has control over
	 *
	 * @return
	 */
	public Collection<FlowEntry> listFlowSpace();

	Collection<Integer> changeFlowSpace(List<FlowSpaceChangeRequest> changes)
			throws PermissionDeniedException, FlowEntryNotFound, NoSliceForFlowSpace;

	public Boolean registerTopologyEventCallback(String URL, String method, String eventType) throws MalformedURLException;

	public boolean deregisterTopologyEventCallback(String method, String eventType);
}
