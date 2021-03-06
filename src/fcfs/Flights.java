/**
 * 
 */
package fcfs;

/**
 * @author Noam Almog
 *
 */
import java.io.*;
import java.util.*;
/*
 * this class holds the collection of flights in scheduling
 * has get, sets for the flights and loading from input data
 */
public class Flights {
	
	PrintStream io = System.out;
	int ACES_FDS_OFFSET = 3*3600000;//3 hours This offset is based on the ACES config file.
	//It's the amount of time ACES holds flights on the ground before starting the simulation.
	Hashtable<Integer, Flight> flightList;
	//Map<Integer,Flight> f = new Map<Integer, Flight>();
	Hashtable<String, Integer> taxiOffset; //in millisecs
	
	public Flights(){
		flightList = new Hashtable<Integer, Flight>();
		taxiOffset = new Hashtable<String, Integer>();
	}

	/**
	 * Overwrites flight if it exists
	 * @param id
	 * @param flight
	 */
	public void put(int id, Flight flight)
	{
		flightList.put(id, flight);
	}

	public Flight get(int id)
	{
		return flightList.get(id);
	}
	
	//read call signs in from ACES to match fligh ID's useful for identifying flight's airline
	public void loadCallSigns(String path){
		try{
			//Read callsigns in
			FileInputStream fstream = new FileInputStream(path);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String line;
			String[] subs = new String[1];
			int flightId;
			String airline;
			br.readLine();
			while ((line = br.readLine()) != null){
				line = line.replaceAll("\\s","");//trim();
				// location(0), sim day(1), hour(2), quarter(3), rates(4)
				subs = line.split(",");
				flightId = Integer.parseInt(subs[0]);
				airline = subs[1].substring(0, 3);
				//U.p(airline + " " + flightId);
				if(flightList.get(flightId) != null){
					flightList.get(flightId).airline = airline;
				}
			}
			in.close();

		}catch (Exception e){
			System.err.println("call sign load Error: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	//Corrects for taxiOffsets that are added in by ACES so that they are not added in again by scheduler.
	public void loadTaxiOffset(String path){
		try{
			//Read ACES Transit Time File Line by Line
			FileInputStream fstream = new FileInputStream(path);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String line;
			String[] subs = new String[1];
			String[] os = new String[1];
			String airportName;
			int taxiOffSet;
			br.readLine();
			while ((line = br.readLine()) != null){
				line = line.replaceAll("\\s","");//trim();
				subs = line.split(",");
				airportName = subs[0];
				os = subs[1].split(":");
				taxiOffSet = Integer.parseInt(os[1])*60000 + Integer.parseInt(os[2])*1000;
				taxiOffset.put(airportName, taxiOffSet);
			}
			in.close();

		}catch (Exception e){
			System.err.println("taxi offset load Error: " + e.getMessage());
			e.printStackTrace();
		}
		
		for (Flight f: flightList.values()){
			//U.p("be " + f.departureTimeProposed + " " + f.arrivalTimeProposed );
			int a = f.departureTimeACES, b = f.arrivalTimeACES;
			if(taxiOffset.get(f.departureAirport)!=null){
					f.correctForTaxiOffset(taxiOffset.get(f.departureAirport));
					//check for correctness
					//540000 is the biggest offset, this ensures we don't have negative starting value
					//(it is specific to this dataset only) and should be changed from being a magic number
					if(a - taxiOffset.get(f.departureAirport)+ 540000 != f.departureTimeACES ){U.p("ERROR in t.o.");}
					if(b - taxiOffset.get(f.departureAirport)+ 540000 != f.arrivalTimeACES ){U.p("ERROR in t.o.");}
			} else {
				//corrects default of 10 minutes taxi out from ACES
				f.correctForTaxiOffset(600000);
				//check for correctness
				if(a - 600000 + 540000 != f.departureTimeACES ){U.p("ERROR in to d");}
				if(b - 600000 + 540000 != f.arrivalTimeACES){U.p("ERROR in to d");}
			}
			//U.p("a " + f.departureTimeProposed + " " + f.arrivalTimeProposed + " " + );
			
		}
	}
	
	public void pushFlightsForwardInTime(int offset){
		for (Flight f: flightList.values()){
			f.pushFlightForwardInTime(offset);
		}
	}
	 
	
	//Loads flights from ACES flight track file. Built to be robust but may run into issues if tracks are incomplete for example if a flight track is missing an arrival airport.
	public void loadFlightsFromAces(String filePath, boolean loadSectors){
		//Hashtable<Integer, Integer> test = new Hashtable<Integer, Integer>();
		String[] subs = new String[1];
		int max = 0, inputCount = 0;
		try{
			//Read ACES Transit Time File Line by Line
			  FileInputStream fstream = new FileInputStream(filePath);
			  DataInputStream in = new DataInputStream(fstream);
			  BufferedReader br = new BufferedReader(new InputStreamReader(in));
			  String line;
			  Flight f = new Flight(-1);
			  boolean correctTransitSequence = false; //Should skip over sequences missing departure or arrival airports
			  
			  //skip past comment line
			  br.readLine();
			  while ((line = br.readLine()) != null){
				  //The values in the csv are as follows:
				  // flightid(0), entryTime(1),exitTime(2),transitTime(3),
				  //upperStreamSector(4),currentSector(5),downStreamSector(6)
				  subs = line.split(",");
				  if( subs.length == 7){ //&& !line.startsWith("*")){
					  //inputCount++;
					  int entryTime = Integer.parseInt(subs[1]) + ACES_FDS_OFFSET;
					  //max = java.lang.Math.max(max, Integer.parseInt(subs[2]));
					  //max = java.lang.Math.max(max, Integer.parseInt(subs[1]));
					  int transitTime = Integer.parseInt(subs[3]);
					  String facilityName = subs[5];
					  
					  //check for large times? can't handle times longer than 24 days.
					  //U.Assert(entryTime < 2000000000);
					  
					  if(subs[4].equals("XXXX")){
						  inputCount++;
						  correctTransitSequence = true;
						  //SETS f.departureTimeProposed
						  f = new Flight(Integer.parseInt(subs[0]));
						  f.departureTimeScheduled = entryTime;
						  f.departureTimeACES = entryTime; 
						  f.departureAirport = facilityName;
						  //add tracons to list;
						  facilityName = subs[6];
					  }

					  if(subs[6].equals("XXXX")){
						  int exitTime = Integer.parseInt(subs[2]) + ACES_FDS_OFFSET;
						  f.arrivalTimeACES = exitTime;
						  f.arrivalTimeScheduled = exitTime;
						  correctTransitSequence = false;
						  f.arrivalAirport = facilityName;
						  //add tracons to list
						  facilityName = subs[4];
						  f.path.add(new SectorAirport(facilityName, entryTime, transitTime, subs[4]+","+subs[5]+","+subs[6]));
						  flightList.put(Integer.parseInt(subs[0]), f);
						  f = null;
					  }
					  
					  if(correctTransitSequence){ 
						  if(loadSectors)f.path.add(new SectorAirport(facilityName, entryTime, transitTime, subs[4]+","+subs[5]+","+subs[6]));
					  } // bad entry else {io.println("bad flightId entry " + subs[0]); }
					  
				  } else {
					  io.println("not 7 " + line);
				  }

			  }
			  in.close();
			  
		}catch (Exception e){
			io.println(subs[0]);
			System.err.println("la Error: " + e.getMessage());
			 e.printStackTrace();
		}
		//io.println(inputCount + " input output " + flightList.size());
		//io.println("max "+max);
		//io.println(test.size() + " total, usable " + flightList.size());
		//io.println(inputCount + " total, usable " + flightList.size());
		//flightList.get(36440).fullPrint();
	}
	
	//Huu
	public void loadCenterTransitFromAces(String filePath){
		String[] subs = new String[1];
		try{
			  FileInputStream fstream = new FileInputStream(filePath);
			  DataInputStream in = new DataInputStream(fstream);
			  BufferedReader br = new BufferedReader(new InputStreamReader(in));
			  String line;
			  int fid = -1;
			  int entryTime = -1;
			  int exitTime = -1;
			  int transitTime = -1;
			  //skip past header line
			  br.readLine();
			  while ((line = br.readLine()) != null){
				  subs = line.split(",");
				  fid = Integer.parseInt(subs[0]);
				  entryTime = Integer.parseInt(subs[1]) + ACES_FDS_OFFSET;
				  exitTime = Integer.parseInt(subs[2]) + ACES_FDS_OFFSET;
				  transitTime = Integer.parseInt(subs[3]);
				  String prevFacilityName = subs[4];
				  String facilityName = subs[5];
				  
				  
				  if (flightList.containsKey(fid)) {
					  Flight flight = flightList.get(fid);
					  flight.centerPath.add(new CenterTransit(facilityName, prevFacilityName, entryTime, exitTime));
				  }
					  
				  else {
					  //flight is not in flight list and has no departure airport/arrival airport to be used
					  System.out.println("Center Transit path for flight " + fid + " not used");
				  }
					
			  }
			  in.close();
			  
		}catch (Exception e){
			io.println(subs[0]);
			System.err.println("Parse Error: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	//For methods below see documantation in Flight.java
	Flight getFlightByID(int id){
		return flightList.get(id);
	}

	void validate(){
		for (Flight f : flightList.values()){
			f.validate();
		}
	}
	
	void validateFCFS(){
		for (Flight f : flightList.values()){
			f.validateFCFS();
		}
	}
	
	void resetPerturbationAndSchedulingDependentVariables(){
		for (Flight f : flightList.values()){
			f.resetPerturbationAndSchedulingDependentVariables();
		}
	}
	
	void resetSchedulingDependentVariables(){
		for (Flight f : flightList.values()){
			f.resetSchedulingDependentVariables();
		}
	}
	
	public Collection<Flight> getFlights(){
		return new ArrayList<Flight>(flightList.values());
	}
	
	public void printFlights(){
		for (Flight f : flightList.values()){
			f.print();
		}
		io.println("total number of flights: " + flightList.size());
	}
	
	public void printFlightsFull(){
		for (Flight f : flightList.values()){
			f.printFull();
		}
	}
	
	
}
