package fcfs;

import java.io.PrintStream;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * To initialize a flight add the transit times in sequence starting from departure
 * airport and ending at arrival airport.
 * @author nalmog
 */


public 	class Flight implements Comparable<Flight>{
	//private final static Logger logger = LoggerFactory.getLogger(Flight.class);

	public final static String NONE = "XXXX";

	static double toMinutes = 60*1000.0;
	
	ArrayList<SectorAirport> path = new ArrayList<SectorAirport>();
	ArrayList<CenterTransit> centerPath = new ArrayList<CenterTransit>();
	ArrayList<String> centersTravelled = new ArrayList<String>();
	
	
	static PrintStream io = System.out;
	
	final int id;
	int departureTimeACES = -1;//proposed times loaded by ACES/ASDI //TODO: refactor dpartureTimeProposed name
	int arrivalTimeACES = -1;//proposed times loaded by ACES/ASDI
	int taxi_unimpeded_time = AirportTree.DEFAULT_UNIMPEDED_TAXI_TIME*60000; //from gano
	String arrivalAirport = "undef_arr";
	String departureAirport  = "undef_dep";
	
	int gate_perturbation = 0; // based on ASPM data, always positive
	int taxiUncertainty = 0; // based on ASPM data, always positive
	
	//change each run.
	int atcAirDelay = 0;//air delay
	int atcGroundDelay = 0;//ground delay	
	int wheelsOffTime = -1; //wheels off time
	int arrivalFirstSlot = -1; //original slot, if flight misses this slot, we want to know how much it misses it by
	int departureTimeFinal = -1; //with uncertainty etcetera (departure from the gate?) //private
	int departureTimeScheduled = -1; 
	int arrivalTimeScheduled = -1; //by scheduler
	int arrivalTimeFinal = -1; //wheels on time
	int arrivalAirportDelay = 0;
	int departureAirportDelay =0;
	int centerDelay = 0;
	int centerBoundaryDelay = 0;
	
	//for debugging
	int numberOfJiggles = 0; //number of times a flights departure and or arrival times are modified
	int totalJiggleAmount = 0;// in millisecs
	int gateUncertainty = 0; //uncertaainty in when flight actually leaves as opposed to scheduled
	int additionalGDfromReschedule = 0;
	boolean rescheduled = false;
	int originalJiggle = 0;
	//int uncertaintyMinusGroundDelay = 0; //uncertaintyMinusGroundDelay + atcAirDelay
	
	String airline = "unknown";
	String centersTravelledPath = null;
	
	boolean cfrEffected = false;
	double gateUncertaintyConstant = 0; //gaussian random number used to generate gateUncertainty
	boolean firstTimeBeingArrivalScheduled = true;
	boolean arrivalTimeFrozen = false;
	boolean scheduled = false;
	int numberOfevents = 0;
	boolean nonCFRInternal = false;
	/*
	public void setDepartureTimeFinal(int s){
		departureTimeFinal = s;
		if(id == 33722){
			//U.p("");
		}
	}*/
	public void resetSchedulingDependentVariables(){
		 departureTimeScheduled = -1; 
		 arrivalTimeScheduled = -1; //by scheduler
		 atcAirDelay = 0;
		 atcGroundDelay = 0;
		 wheelsOffTime = -1;
		 arrivalTimeFinal = -1;
		 arrivalFirstSlot = -1;
		 numberOfJiggles = 0; //number of times a flights departure and or arrival times are modified
		 totalJiggleAmount = 0;// in millisecs
		 gateUncertainty = 0;
		 additionalGDfromReschedule = 0;
		 originalJiggle = 0;
		 rescheduled = false;
		 cfrEffected = false;
		 //gateUncertaintyConstant = 0; //gaussian random number used to generate gateUncertainty
		 firstTimeBeingArrivalScheduled = true;
		 arrivalTimeFrozen = false;
		 departureTimeFinal = -1;
		 departureAirportDelay = 0;
		 arrivalAirportDelay = 0;
		 scheduled = false;
		 numberOfevents = 0;
		 nonCFRInternal = false;
		 
	}

	public void resetPerturbationAndSchedulingDependentVariables(){
		 gate_perturbation = 0;
		 taxiUncertainty = 0;
		 // = AirportTree.DEFAULT_UNIMPEDED_TAXI_TIME*60000; //right?
		 gateUncertaintyConstant = 0;
		 resetSchedulingDependentVariables();
	}

	/**
	 * @todo does the tracon name need a TRACON prefix (TKDFW vs TRACONTKDFW)?
	 */
	/**
	 * Sets departure time and transit time through departure airport.
	 * 
	 * @param airport departure airport name (KDFW for example)
	 * @param tracon departure tracon name (TKDFW or TRACONTKDFW for example)
	 * @param departure departure time [msec]
	 * @param transit surface transit time (gate departure to runway takeoff) [msec]
	 */
	public void setDeparture(String airport, String tracon, int departure, int transit)
	{
		departureTimeScheduled = departure;
		departureTimeACES = departure;
		departureAirport = airport;
		path.add(new SectorAirport(airport, departure, transit, makePath(NONE, airport, tracon)));
		//logger.debug("{}, {}, {}, {}, {}", id, airport, departure, transit, makePath(NONE, airport, tracon));
	}

	/**
	 * Sets arrival time and transit through arrival airport
	 * 
	 * @param airport arrival airport name (KDFW for example)
	 * @param tracon arrival tracon name (TKDFW or TRACONTKDFW for example)
	 * @param entry arrival time - transit time [msec]
	 * @param transit surface transit time (runway landing to gate arrival) [msec]
	 */
	public void setArrival(String airport, String tracon, int entry, int transit)
	{
		arrivalTimeScheduled = entry + transit;
		arrivalTimeACES = entry + transit;
		arrivalAirport = airport;
		path.add(new SectorAirport(airport, entry, transit, makePath(tracon, airport, NONE)));
		//logger.debug("{}, {}, {}, {}, {}", id, airport, entry, transit, makePath(tracon, airport, NONE));
	}

	/**
	 * @todo does it matter if we have multiple crossings?
	 */
	/**
	 * Should be done in order of crossing
	 * 
	 * @param current current facility (sector or tracon) name
	 * @param from facility entering from
	 * @param to facility exiting towards
	 * @param entry entry time [msec]
	 * @param transit transit time [msec]
	 */
	public void addTransit(String current, String from, String to, int entry, int transit)
	{
		path.add(new SectorAirport(current, entry, transit, makePath(from, current, to)));
		//logger.debug("{}, {}, {}, {}, {}", id, current, entry, transit, makePath(from, current, to));
	}
	
	/**
	 * @param from
	 * @param current
	 * @param to
	 * @return
	 */
	public String makePath(String from, String current, String to)
	{
		return from + "," + current + "," + to;
	}
	
	static int dummyID = 0;
	public static Flight dummyArrival(int arrivalTime){
		Flight f = new Flight(++dummyID);
		f.arrivalTimeFinal = arrivalTime;
		return f;
	}	
	
	public static Flight dummyDeparture(int departureTime){
		Flight f = new Flight(++dummyID);
		f.departureTimeFinal = departureTime;
		return f;
	}
	
	public boolean validateFCFS(){
		boolean valid = true;
		if(id<0){U.p("ERROR " + id + " is invalid! id<0"); printVariables(); valid =  false;}
		if(departureTimeACES<0){U.p("ERROR " + id + " is invalid! departureTimeProposed<0"); printVariables(); valid =  false;}
		//if(atcAirDelay<0){U.p("ERROR " + id + " is invalid! "); printVariables(); valid =  false;}
		if(atcGroundDelay<0){U.p("ERROR " + id + " is invalid! airDelayFromArrivalAirport<0"); printVariables(); valid =  false;}		
		if(departureTimeFinal<0){U.p("ERROR " + id + " is invalid! taxi_unimpeded_time<0"); printVariables(); valid =  false;}
		if(departureTimeACES>departureTimeFinal){U.p("ERROR " + id + " is invalid! departureTimeProposed>departureTimeFinal"); printVariables(); valid =  false;}
		if(arrivalTimeACES>arrivalTimeFinal){U.p("ERROR " + id + " is invalid! arrivalTimeProposed>arrivalTimeFinal"); printVariables(); valid =  false;}
		if(arrivalTimeFinal<=departureTimeFinal){U.p("ERROR " + id + " is invalid! arrivalTimeFinal<=departureTimeFinal"); printVariables(); valid =  false;}
		if(arrivalTimeACES<=departureTimeACES){U.p("ERROR " + id + " is invalid! arrivalTimeProposed<=departureTimeProposed"); printVariables(); valid =  false;}
		if(departureTimeACES+atcGroundDelay!=departureTimeFinal){
			U.p("ERROR " + id + " is invalid! departureTimeProposed+atcGroundDelay!=departureTimeFinal");printVariables(); valid =  false;}
		//if(arrivalTimeProposed+atcGroundDelay!=arrivalTimeFinal){
			//U.p("ERROR " + id + " is invalid! arrivalTimeProposed+atcGroundDelay!=arrivalTimeFinal");printVariables(); valid =  false;}
		//U.p("here");
		//if(arrivalTimeFinal - arrivalTimeProposed != atcGroundDelay){U.p("good news");}
		//if(id==36788){U.p(arrivalTimeFinal - arrivalTimeProposed +" "+ atcGroundDelay);}
		
		return valid;
	}
	
	public boolean validate(){
		boolean valid = true;
		if(id<0){U.p("ERROR " + id + " is invalid! id<0"); printVariables(); valid =  false;}
		if(departureTimeACES<0){U.p("ERROR " + id + " is invalid! departureTimeProposed<0"); printVariables(); valid =  false;}
		if(atcAirDelay<0){U.p("ERROR " + id + " is invalid! "); printVariables(); valid =  false;}
		if(atcGroundDelay<0){U.p("ERROR " + id + " is invalid! airDelayFromArrivalAirport<0"); printVariables(); valid =  false;}
		if(gate_perturbation<0){U.p("ERROR " + id + " is invalid! gate_perturbation<0"); printVariables(); valid =  false;}
		if(taxiUncertainty<0){U.p("ERROR " + id + " is invalid! taxi_perturbation<0"); printVariables(); valid =  false;}
		if(taxi_unimpeded_time<0){U.p("ERROR " + id + " is invalid! "); printVariables(); valid =  false;}
		if(wheelsOffTime<0){U.p("ERROR " + id + " is invalid! taxi_unimpeded_time<0"); printVariables(); valid =  false;}
		if(departureTimeACES>wheelsOffTime){U.p("ERROR " + id + " is invalid! departureTimeProposed>departureTimeFinal"); printVariables(); valid =  false;}
		if(arrivalTimeACES>arrivalTimeFinal){U.p("ERROR " + id + " is invalid! arrivalTimeProposed>arrivalTimeFinal"); printVariables(); valid =  false;}
		if(arrivalTimeFinal<=wheelsOffTime){U.p("ERROR " + id + " is invalid! arrivalTimeFinal<=departureTimeFinal"); printVariables(); valid =  false;}
		if(arrivalTimeACES<=departureTimeACES){U.p("ERROR " + id + " is invalid! arrivalTimeProposed<=departureTimeProposed"); printVariables(); valid =  false;}
		if(wheelsOffTime<departureTimeACES+taxi_unimpeded_time+gate_perturbation){ //taxi_perturbation
			U.p("ERROR " + id + " is invalid! wheelsOffTime<departureTimeProposed+taxi_perturbation+taxi_unimpeded_time+gate_perturbation"); printVariables(); valid =  false;
		}
		if(arrivalFirstSlot<=0){
			U.p("ERROR " + id + " arrivalFirstSlot is invalid! arrivalFirstSlot<=0"); printVariables(); valid =  false;}
		if(arrivalFirstSlot>arrivalTimeFinal){
			U.p("ERROR " + id + " arrivalFirstSlot is invalid! arrivalFirstSlot>arrivalTimeFinal"); printVariables(); valid =  false;}
		int uncertaintyMinusGroundDelay = gateUncertainty - atcGroundDelay;
		//3 different possible outcomes for wheels on - first slot 
		if(arrivalTimeFinal-arrivalFirstSlot !=  totalJiggleAmount // makes it on first time schedule
	    && arrivalTimeFinal-arrivalFirstSlot !=  totalJiggleAmount + taxiUncertainty + originalJiggle + additionalGDfromReschedule  // no contract, jiggle uncertainty rescheduling
	    && arrivalTimeFinal-arrivalFirstSlot !=  totalJiggleAmount + taxiUncertainty + originalJiggle + additionalGDfromReschedule + uncertaintyMinusGroundDelay  
	    && arrivalTimeFinal-arrivalFirstSlot !=  totalJiggleAmount + taxiUncertainty + originalJiggle + additionalGDfromReschedule + uncertaintyMinusGroundDelay + atcAirDelay  
		&& arrivalTimeFinal-arrivalFirstSlot !=  totalJiggleAmount + taxiUncertainty + originalJiggle + additionalGDfromReschedule +                               atcAirDelay
		&& arrivalTimeFinal-arrivalFirstSlot !=   					 taxiUncertainty +											   uncertaintyMinusGroundDelay + atcAirDelay //contract jiggle uncertainty
		&& arrivalTimeFinal-arrivalFirstSlot !=                      taxiUncertainty +         																	 atcAirDelay //contract

//		&& arrivalTimeFinal-arrivalFirstSlot !=  					 taxi_perturbation + gateUncertainty - atcGroundDelay + 2*additionalGDfromReschedule + totalJiggleAmount + originalJiggle 
//		&& arrivalTimeFinal-arrivalFirstSlot !=  					taxi_perturbation + gateUncertainty - atcGroundDelay + 2*originalJiggle + totalJiggleAmount + additionalGDfromReschedule
//		&& arrivalTimeFinal-arrivalFirstSlot != atcAirDelay
				){
			U.p("ERROR " + id + " wheels on - arrivalFirstSlot = " + (arrivalTimeFinal-arrivalFirstSlot)/toMinutes); printVariables(); valid =  false;}
		
		return valid;
	}
	
	
	public Flight(int i){
		this.id = i;
	}
	
	public void addDelay(int d){
		atcGroundDelay = d;
		departureTimeACES += d;
		arrivalTimeACES += d;
		
		for(SectorAirport s: path){
			s.entryTime += d;
		}
		
	}
	
	//changes all variables back to their values
	//before simulation
	
	
	
	public void correctForTaxiOffset(int d){
		//540000 is the biggest offset, this ensures we don't have negative starting value
		//(it is specific to this dataset only)
		d -= 540000; // CHANGE AND REMOVE THIS MAGIC NUMBER
		departureTimeACES -= d;
		arrivalTimeACES -= d;
		for(SectorAirport s: path){
			s.entryTime -= d;
		}
	}
	
	//millisec
	public void pushFlightForwardInTime(int d){
		departureTimeACES += d;
		arrivalTimeACES += d;
		for(SectorAirport s: path){
			s.entryTime += d;
		}
	}
	
	void print(){
		io.printf("ID %5d DEPF %s DEPP %s ARRF %s ARRP %s DELAY %d CFR %b : %s->%s", 
				id, 
				U.timeToDateAdjustedShort(departureTimeFinal),
				U.timeToDateAdjustedShort(departureTimeACES + taxi_unimpeded_time),
				U.timeToDateAdjustedShort(arrivalTimeFinal),
				U.timeToDateAdjustedShort(arrivalTimeACES + taxi_unimpeded_time),
				atcGroundDelay,
				cfrEffected,
				departureAirport,
				arrivalAirport
				
				);
		for(SectorAirport s: path){
			//io.print(" " + s.name);
		}
		io.println(" " + arrivalAirport);
	}
	
	void printVariables(){
		PrintStream o = System.out;
		int uncertaintyMinusGroundDelay = gateUncertainty - atcGroundDelay;
		o.println("[] ");
		o.println("id " + id + " " + arrivalAirport + " to " + departureAirport);
		o.println("departureTimeProposed " + departureTimeACES/toMinutes);
		o.println("departureTimeFinal " + departureTimeFinal/toMinutes);
		o.println("arrivalTimeProposed " + arrivalTimeACES/toMinutes);
		o.println("wheelsOffTime " + wheelsOffTime/toMinutes);
		o.println("arrivalTimeFinal " + arrivalTimeFinal/toMinutes);
		o.println("arrivalFirstSlot " + arrivalFirstSlot/toMinutes);
		o.println("gate_perturbation " + gate_perturbation/toMinutes);
		o.println("taxi_unimpeded_time " + taxi_unimpeded_time/toMinutes);
		o.println("taxi_perturbation " + taxiUncertainty/toMinutes);
		o.println("gateUncertainty " + gateUncertainty/toMinutes);
		o.println("uncertaintyMinusGroundDelay " + uncertaintyMinusGroundDelay/toMinutes);
		o.println("atcAirDelay " + atcAirDelay/toMinutes);
		o.println("atcGroundDelay " + atcGroundDelay/toMinutes);
		o.println("numberOfJiggles " + numberOfJiggles);
		o.println("totalJiggleAmount " + totalJiggleAmount/toMinutes);
		o.println("additionalGDfromReschedule " + additionalGDfromReschedule/toMinutes);
		o.println("originalJiggle " + originalJiggle/toMinutes);
		o.println("rescheduled " + rescheduled);
		o.println("[][][][]");
	}
/*	
 * 
 * 
	void print(){
		io.printf("ID %d DEPF %d DEPP %d ARRF %d ARRP %d DELAY %d CFR %b : %s->%s", 
				id, 
				departureTimeFinal,
				departureTimeProposed + taxi_unimpeded_time,
				arrivalTimeFinal,
				arrivalTimeProposed,
				atcGroundDelay,
				cfrEffected,
				departureAirport,
				arrivalAirport
				);
		for(SectorAirport s: path){
			//io.print(" " + s.name);
		}
		io.println(" " + arrivalAirport);
	}
	*/
	void print(String ss){
		io.printf("%s: ID %d DEPF %d DEPPwTAXI %d ARR %d DELAY %d CFR %b : %s->%s", ss,
				id, 
				departureTimeFinal,
				departureTimeACES,
				arrivalTimeFinal, 
				atcGroundDelay,
				cfrEffected,
				departureAirport,
				arrivalAirport
				);
		for(SectorAirport s: path){
			io.print(" " + s.name);
		}
		io.println(" " + arrivalAirport);
	}
	
	void printFull(){
		io.printf("ID: %d Delay: %d\ndepAairport %s depTime %d\n", id, atcGroundDelay, departureAirport, departureTimeACES/1000);
		io.println("path:");
		for(SectorAirport s: path){
			io.printf("name: %s entryTime: %d transitTime: %d raw: %s\n", s.name, s.entryTime/1000, s.transitTime/1000, s.raw );
		}
		io.println("arrAirport:" + arrivalAirport + " " + " arrTime: " + arrivalTimeACES/1000 + "\n");
	}

	@Override
	//sorts by departure time.
	public int compareTo(Flight o) {
		return (departureTimeACES-o.departureTimeACES);
	}

	/**
	 * @return none
	 */
	public static String getNone()
	{
		return NONE;
	}

	/**
	 * @return toMinutes
	 */
	public static double getToMinutes()
	{
		return toMinutes;
	}

	/**
	 * @return path
	 */
	public ArrayList<SectorAirport> getPath()
	{
		return path;
	}

	/**
	 * @return centerPath
	 */
	public ArrayList<CenterTransit> getCenterPath()
	{
		return centerPath;
	}

	/**
	 * @return centersTravelled
	 */
	public ArrayList<String> getCentersTravelled()
	{
		return centersTravelled;
	}

	/**
	 * @return io
	 */
	public static PrintStream getIo()
	{
		return io;
	}

	/**
	 * @return id
	 */
	public int getId()
	{
		return id;
	}

	/**
	 * @return departureTimeScheduled
	 */
	public int getDepartureTimeScheduled()
	{
		return departureTimeScheduled;
	}

	/**
	 * @return departureTimeProposed
	 */
	public int getDepartureTimeProposed()
	{
		return departureTimeACES;
	}

	/**
	 * @return arrivalTimeProposed
	 */
	public int getArrivalTimeProposed()
	{
		return arrivalTimeACES;
	}

	/**
	 * @return taxi_unimpeded_time
	 */
	public int getTaxi_unimpeded_time()
	{
		return taxi_unimpeded_time;
	}

	/**
	 * @return arrivalAirport
	 */
	public String getArrivalAirport()
	{
		return arrivalAirport;
	}

	/**
	 * @return departureAirport
	 */
	public String getDepartureAirport()
	{
		return departureAirport;
	}

	/**
	 * @return gate_perturbation
	 */
	public int getGate_perturbation()
	{
		return gate_perturbation;
	}

	/**
	 * @return taxi_perturbation
	 */
	public int getTaxi_perturbation()
	{
		return taxiUncertainty;
	}

	/**
	 * @return atcAirDelay
	 */
	public int getAtcAirDelay()
	{
		return atcAirDelay;
	}

	/**
	 * @return atcGroundDelay
	 */
	public int getAtcGroundDelay()
	{
		return atcGroundDelay;
	}

	/**
	 * @return wheelsOffTime
	 */
	public int getWheelsOffTime()
	{
		return wheelsOffTime;
	}

	/**
	 * @return arrivalFirstSlot
	 */
	public int getArrivalFirstSlot()
	{
		return arrivalFirstSlot;
	}

	/**
	 * @return departureTimeFinal
	 */
	public int getDepartureTimeFinal()
	{
		return departureTimeFinal;
	}

	/**
	 * @return arrivalTimeScheduled
	 */
	public int getArrivalTimeScheduled()
	{
		return arrivalTimeScheduled;
	}

	/**
	 * @return arrivalTimeFinal
	 */
	public int getArrivalTimeFinal()
	{
		return arrivalTimeFinal;
	}

	/**
	 * @return arrivalAirportDelay
	 */
	public int getArrivalAirportDelay()
	{
		return arrivalAirportDelay;
	}

	/**
	 * @return departureAirportDelay
	 */
	public int getDepartureAirportDelay()
	{
		return departureAirportDelay;
	}

	/**
	 * @return centerDelay
	 */
	public int getCenterDelay()
	{
		return centerDelay;
	}

	/**
	 * @return centerBoundaryDelay
	 */
	public int getCenterBoundaryDelay()
	{
		return centerBoundaryDelay;
	}

	/**
	 * @return numberOfJiggles
	 */
	public int getNumberOfJiggles()
	{
		return numberOfJiggles;
	}

	/**
	 * @return totalJiggleAmount
	 */
	public int getTotalJiggleAmount()
	{
		return totalJiggleAmount;
	}

	/**
	 * @return gateUncertainty
	 */
	public int getGateUncertainty()
	{
		return gateUncertainty;
	}

	/**
	 * @return additionalGDfromReschedule
	 */
	public int getAdditionalGDfromReschedule()
	{
		return additionalGDfromReschedule;
	}

	/**
	 * @return rescheduled
	 */
	public boolean isRescheduled()
	{
		return rescheduled;
	}

	/**
	 * @return originalJiggle
	 */
	public int getOriginalJiggle()
	{
		return originalJiggle;
	}

	/**
	 * @return uncertaintyMinusGroundDelay
	 */
	public int getUncertaintyMinusGroundDelay()
	{
		return gateUncertainty - atcGroundDelay;
	}

	/**
	 * @return airline
	 */
	public String getAirline()
	{
		return airline;
	}

	/**
	 * @return centersTravelledPath
	 */
	public String getCentersTravelledPath()
	{
		return centersTravelledPath;
	}

	public void pathCsvHeader(StringBuilder buffer)
	{
		buffer.append("**flightid,entryTime(milliseconds),exitTime(milliseconds),transitTime(milliseconds)," +
				"upperStreamSector,currentSector,downStreamSector");
	}
	public void pathAsCsvString(StringBuilder buffer)
	{
		for (SectorAirport segment : path)
		{
			buffer.append(id).append(",");
			buffer.append(segment.name).append(",");
			buffer.append(segment.entryTime).append(",");
			buffer.append(segment.entryTime + segment.transitTime).append(",");
			buffer.append(segment.transitTime).append(",");
			buffer.append(segment.raw);
			buffer.append("\n");
		}
	}
}

class flightFinalDepTimeComparator implements Comparator<Flight>{
	public int compare(Flight f1, Flight f2){
		//if(f1.departureTimeFinal == f2.departureTimeFinal){return f1.id - f2.id;}
		//System.out.println("comp: " + f1.id + " " + f1.departureTimeFinal + " " + f2.id + " " + f2.departureTimeFinal + " : " + (f1.departureTimeFinal - f2.departureTimeFinal));
		return f1.getDepartureTimeFinal() - f2.getDepartureTimeFinal();
	}
}


class flightFinalArrTimeComparator implements Comparator<Flight>{
	public int compare(Flight f1, Flight f2){
		//if(f1.arrivalTimeFinal == f2.arrivalTimeFinal){return f1.id - f2.id;}
		return f1.arrivalTimeFinal - f2.arrivalTimeFinal;
	}
}


class flightIDComparator implements Comparator<Flight>{
	public int compare(Flight f1, Flight f2){
		U.Assert(f1.id != f2.id, "flightIDComparator: comparing flights with same ID!");
		return f1.id - f2.id;
	}
}

class flightArrTimeIDComparator implements Comparator<Flight>{
	public int compare(Flight f1, Flight f2) {
		if(f1.arrivalTimeACES == f2.arrivalTimeACES){
			return f1.id - f2.id;
		}
		return f1.arrivalTimeACES - f2.arrivalTimeACES;
	}
}

class flightDepTimeIDComparator implements Comparator<Flight>{
	public int compare(Flight f1, Flight f2){
		if(f1.departureTimeACES == f2.departureTimeACES){
			return f1.id - f2.id;
		}
		return f1.departureTimeACES - f2.departureTimeACES;
	}
}

class flightGateTaxiUnimDepComparator implements Comparator<Flight>{
	public int compare(Flight f1, Flight f2){
		return (f1.departureTimeACES+f1.gate_perturbation+f1.taxi_unimpeded_time) - 
			   (f2.departureTimeACES+f2.gate_perturbation+f2.taxi_unimpeded_time);
	}
}

class centerBoundaryEntryTimeComparator implements Comparator<CenterTransit>{
	public int compare(CenterTransit ct1, CenterTransit ct2) {
		//if(ct1.entryTime == ct2.entryTime){return ct1.facilityName.compareTo(ct1.facilityName);}
		return (ct1.entryTime - ct2.entryTime);
	}
}

class centerBoundaryFinalEntryTimeComparator implements Comparator<CenterTransit>{
	public int compare(CenterTransit ct1, CenterTransit ct2) {
		//if(ct1.finalEntryTime == ct2.finalEntryTime){return ct1.facilityName.compareTo(ct1.facilityName);}
		return (ct1.finalEntryTime - ct2.finalEntryTime);
	}
}

class CenterTransit{
	String facilityName;
	String prevFacilityName;
	int entryTime;
	int exitTime;
	int transitTime = exitTime - entryTime;
	int finalEntryTime = -1;
	int finalExitTime = -1;
	int proposedEntryTime = -1;
	int flightid = -1;
	int delay = 0;
	
	CenterTransit(String facility, String prevFacility, int entry, int exit) {
		facilityName = facility;
		prevFacilityName = prevFacility;
		entryTime = entry;
		exitTime = exit;
	}
}

class SectorAirport{
	String raw;
	String name;
	int entryTime;
	int transitTime;
	//int scheduledEntryTime;
	SectorAirport(String n, int e, int s){
		name = n; entryTime = e; transitTime = s;
	}
	SectorAirport(String n, int e, int s, String r){
		name = n; entryTime = e; transitTime = s; raw = r;
	}
}
