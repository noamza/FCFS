package fcfs;

import java.util.*;

//@author Huu Huynh

public class CenterBoundaries {
	

	Hashtable<String, CenterBoundary> centerBoundaryList = new Hashtable<String, CenterBoundary>();

	public int getSoonestSlot(String centerBoundaryName, String exitCenterName, String enterCenterName, int enterTime){
		CenterBoundary cb = centerBoundaryList.get(centerBoundaryName);
		if(cb == null){
			cb = new CenterBoundary(exitCenterName, enterCenterName);
			centerBoundaryList.put(centerBoundaryName, cb);
		}
		return cb.getSoonestEntrySlot(enterTime);
	}
	
	public int schedule(String centerBoundaryName, String exitCenterName, String enterCenterName, int enterTime, int scheduledEntryTime){
		CenterBoundary cb = centerBoundaryList.get(centerBoundaryName);
		if(cb == null){
			cb = new CenterBoundary(exitCenterName, enterCenterName);
			centerBoundaryList.put(centerBoundaryName, cb);
		}
		return cb.insertAtSoonestCenterBoundary(enterTime, scheduledEntryTime);
	}
	
	public int getSoonestSlot(String centerBoundaryName, CenterTransit ct){
		CenterBoundary cb = centerBoundaryList.get(centerBoundaryName);
		if(cb == null){
			cb = new CenterBoundary(ct.prevFacilityName, ct.facilityName);
			centerBoundaryList.put(centerBoundaryName, cb);
		}
		return cb.getSoonestEntrySlot(ct, ct.proposedEntryTime);
	}
	
	public int schedule(String centerBoundaryName, CenterTransit ct){
		CenterBoundary cb = centerBoundaryList.get(centerBoundaryName);
		if(cb == null){
			cb = new CenterBoundary(ct.prevFacilityName,ct.facilityName);
			centerBoundaryList.put(centerBoundaryName, cb);
		}
		return cb.insertAtSoonestCenterBoundary(ct);
	}
	
	public void removeFromSchedule(CenterTransit ct, String centerBoundaryName) {
		CenterBoundary cb = centerBoundaryList.get(centerBoundaryName);
		if(cb == null){
			cb = new CenterBoundary(ct.prevFacilityName,ct.facilityName);
			centerBoundaryList.put(centerBoundaryName, cb);
		}
		cb.removeFromSchedule(ct);
	}
}

