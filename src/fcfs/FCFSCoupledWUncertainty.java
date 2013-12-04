package fcfs;

import java.util.*;
import java.io.*;

/*TODO:
 * 
 * check departure rates are being loaded in and used
 * 
 */

enum ScheduleMode {
	IAHCFR,
	scheduleInTheAir,
	scheduleArrival,
	scheduleDeparture,
	flightReadyToDepart,
	removeFromArrivalQueue,
	removeFromDepartureQueue,
	undef,
	WheelsOff,
}

class SchedulingEvent implements Comparable{
	int eventTime = 0;
	int coEventTime = 0;
	ScheduleMode mode = ScheduleMode.undef;
	Flight flight;
	public SchedulingEvent(int eventTime, int coEventTime, ScheduleMode mode, Flight flight)
	{ 
		this.eventTime= eventTime; 
		this.coEventTime = coEventTime; 
		this.mode = mode; 
		this.flight = flight;
	}
	void print(){ System.out.printf("r time: %d s time: %d\n", eventTime, coEventTime);}
	public int compareTo(Object o) { //orders priorityqueue by least time
		if(eventTime == ((SchedulingEvent)o).eventTime){
			return flight.id - ((SchedulingEvent)o).flight.id;
		}
		return eventTime-((SchedulingEvent)o).eventTime;
		//return ((rt)o).rescheduleTime - rescheduleTime; //order's priorityqueue by greatest first
	}
}

/*
 * Basic Algorithm:
 * 
 * Order flights by proposed gate departure time
 * schedule departures: 
 * 		if NON-CFR basic FCFS
 * 		if CFR, priority scheduling, push others forward
 * 			also schedule arrivals at this point
 * 		
 * Schedule arrivals 30min before proposed (at this point only non-CFR are left)
 * 	
 * 
 * */

public class FCFSCoupledWUncertainty implements Scheduler {


	public void printResults(ArrayList<Flight> flightList, String dir){

	}

	java.util.PriorityQueue<SchedulingEvent> schedulingQueue;
	Flights flights; 
	Airports airports;
	static double speedUp = 0.025; //0.025; 
	static double slowDown = 0.05; //0.05
	int rand = 0;
	Hashtable<String, Double> dispensedAirportDelayHrs;
	Hashtable<String, Double> absorbedAirportDelayHrs;
	//static java.text.DateFormat dateFormat = new java.text.SimpleDateFormat("DDD:HH:mm:ss:SSSS");
	String ofolder = "output/";
	String infolder = "inputs/";
	//String workingDirectory = "C:\\Users\\Noam Almog\\Desktop\\scheduler\\scheduler\\atl_data\\";
	//String workingDirectory = "/Users/nalmog/Desktop/scheduler/atl_data/";
	//			String workingDirectory = "/Users/kpalopo/Desktop/scheduler/atl_data/";
	String workingDirectory = "/Users/nalmog/Desktop/scheduler/";
	java.util.Random random = new java.util.Random(1);//98);//98);//rand);//9 85); //used 98 for 100000 //6 it goes up //11 goes up
	
	Hashtable<Integer, Hashtable<Integer, Hashtable<String, Hashtable<String,Double>>>> 
		resultsFreezeSchedulinghorizonColumnCountmeanstd = new Hashtable<Integer, Hashtable<Integer, Hashtable<String, Hashtable<String,Double>>>>();
	Hashtable<Integer, Hashtable<Integer, Hashtable<String, ArrayList<Double>>>> 
		dataFreezeSchedulinghorizonColumnList = new Hashtable<Integer, Hashtable<Integer, Hashtable<String, ArrayList<Double>>>>();
	String[] columns = {"gournd delay cfr (min)","air delay cfr (min)", "ground delay all (min)", "air delay all (min)", "arrival airport delay (hr)"};
	
	//initialize result structures
	
	public FCFSCoupledWUncertainty(){
		rand = Math.abs(new java.util.Random().nextInt());

	}

	/*
	algo description
	 */

	void load(String inputs, Flights flights, Airports airports){
		//flights.loadFlightsFromAces(workingDirectory+"clean_job.csv",false);
		airports.loadFromAces(inputs+"AdvancedState_Hourly_Runways_AllCapacities_20110103_20110104.csv");
		airports.loadDelays(inputs+"gate_delay.csv", "gate");	
		airports.loadDelays(inputs+"taxi_delay.csv", "taxi");
		airports.loadDelays(inputs+"taxi_u.csv", "taxi");
		flights.loadTaxiOffset(inputs+"AirportTaxi.csv");
		flights.loadCallSigns(inputs + "job_611_airline_flightid_map.csv");
		//System.out.println("		loaded " + dateFormat.format(new Date()));
		flights.pushFlightsForwardBy1hr(10*60*60*1000); // CHANGE //10
		airports.offsetCapacities(10*60*60*1000); //CHANGE //10 
		//TODO: airports.loadCFRdata();
	} //END LOAD()

	int schedulingHorizon;
	int freezeHorizon;


	public ArrayList<Flight> schedule(int sh, int fh){
		schedulingHorizon = sh;
		freezeHorizon = fh;
		return schedule();
	}

	public ArrayList<Flight> schedule(){
		//System.out.println("FCFS CFR Coupled Start! " + dateFormat.format(new Date()));
		dispensedAirportDelayHrs = new Hashtable<String, Double>();
		absorbedAirportDelayHrs = new Hashtable<String, Double>();
		flights = new Flights(); airports = new Airports();
		flights.loadFlightsFromAces(workingDirectory+infolder+"clean_job.csv",false);
		load(workingDirectory+infolder, flights, airports);

		//RUN SETTINGS
		final boolean pertrubGate = true; //gate taxi pert on/off (true false)
		final boolean pertrubTaxi = true; //used TRUE FOR 100000
		int uncertaintyToggle = 1; //gate uncertainty on/off (0 1)
																					final int montecarlo = 100;
		int counter = 0; // number of monte carlo runs
		int defaultPertMills = 0;//1*60000;

		//java.util.Random random = new java.util.Random(98);//98);//rand);//9 85); //used 98 for 100000 //6 it goes up //11 goes up


		ScheduleMode modes[] = {
				ScheduleMode.IAHCFR
		};
		
		U.p("monte carlo: " + montecarlo + " estimated time " + montecarlo/100 + " min");

		//MAIN MONTE CARLO LOOP
		while (counter < montecarlo) {
			counter++;
			ArrayList<Flight> flightList = new ArrayList<Flight>(flights.getFlights());
			schedulingQueue = new java.util.PriorityQueue<SchedulingEvent>();
			//remove Non KIAH Flights
			Iterator<Flight> iter = flightList.iterator();
			while(iter.hasNext()){
				Flight f = iter.next();
				if(!f.arrivalAirport.equals("KIAH")){
					iter.remove();
				}
			}
			Collections.sort(flightList, new flightDepTimeIDComparator());

			try{
				//if(counter==1)calculateDelays(flightList, "CFR", false, new BufferedWriter(new FileWriter(workingDirectory+ofolder+"ignore",true)), true, -1);
			} catch(Exception e){

			}

			int c = 0;
			//GENERATE GATE TAXI PERTURBATIONS
			flights.resetPerturbationAndSchedulingDependentVariables();
			//and this
			//Main.p("setting cfr IAH");
			airports.getAirport("KIAH").setCFR(0, Integer.MAX_VALUE);



			for (Flight f: flightList){
				if(counter == 1){
					absorbedAirportDelayHrs .put(f.departureAirport, 0.0);
					dispensedAirportDelayHrs.put(f.arrivalAirport  , 0.0);
				}
				//the more random a flight departure is, the less delay.
				//int rm = (int)(1000*60*60*350*random.nextDouble()); f.arrivalTimeProposed+=rm; f.departureTimeProposed+=rm;
				AirportTree departureAirport = airports.airportList.get(f.departureAirport);
				int gate_noise_seconds = 0, taxi_noise_seconds = 0;		
				//Get gate and taxi perturbations
				f.gateUncertaintyConstant = random.nextGaussian();
				//U.p(f.gateUncertaintyConstant);

				if(departureAirport!=null){
					double gateR = random.nextDouble(), taxiR = random.nextDouble();
					//Main.p(gateR + " gate taxi " + taxiR + " " + departureAirport.taxiUnimpeded + " " + departureAirport.gateStd + " " + departureAirport.taxiMean);
					f.taxi_unimpeded_time = (int)(departureAirport.taxiUnimpeded)*60000;
					if(pertrubGate && departureAirport.gateZeroProbablity < gateR){
						double gate_noise_minutes = Math.exp(random.nextGaussian()*departureAirport.gateStd + departureAirport.gateMean);
						
						gate_noise_minutes = gate_noise_minutes < 120? gate_noise_minutes: 120;
						gate_noise_seconds = (int)(gate_noise_minutes*60000);
						f.gate_perturbation = gate_noise_seconds;
						//ERROR OR NOT??
						if(gate_noise_minutes == 1) f.gate_perturbation = defaultPertMills;
						//f.gate_perturbation = 0;
						//Main.p("random");
					}
					if(pertrubTaxi && departureAirport.taxiZeroProbablity < taxiR){
						double taxi_noise_minutes = Math.exp(random.nextGaussian()*departureAirport.taxiStd + departureAirport.taxiMean);
						taxi_noise_minutes = taxi_noise_minutes < 45? taxi_noise_minutes: 45;
						taxi_noise_seconds = (int)(taxi_noise_minutes*60000);
						f.taxiUncertainty = taxi_noise_seconds;
						//ERROR OR NOT??
						if(taxi_noise_minutes == 1){
							Main.p(departureAirport.taxiZeroProbablity + " " + c++ + " " + departureAirport.airportName);
							f.taxiUncertainty = defaultPertMills;
						}
						//						f.taxi_perturbation = 0;//taxi_noise_seconds; //CHANGE BACK
					}
					//for null airports on first run

					//TODO:add in airport cfr randomness

				} else {
					//Main.p("error in perturbation?");
					/* need??
					double gateR = random.nextDouble(), taxiR = random.nextDouble();
					double gate_noise_minutes = Math.exp(random.nextGaussian()*0);
					gate_noise_minutes = gate_noise_minutes < 120? gate_noise_minutes: 120;
					gate_noise_seconds = (int)(gate_noise_minutes*60000);
					double taxi_noise_minutes = Math.exp(random.nextGaussian()*0);
					taxi_noise_minutes = taxi_noise_minutes < 45? taxi_noise_minutes: 45;
					taxi_noise_seconds = (int)(taxi_noise_minutes*60000);
					f.gate_perturbation = gate_noise_seconds;
					f.taxi_perturbation = taxi_noise_seconds;
					//Main.p(gate_noise_seconds + " else");
					//keep?
					f.gate_perturbation = defaultPertMills;
					f.taxi_perturbation = defaultPertMills;
					 */
				}

			}


			for(ScheduleMode mode: modes){

				//if(counter != 0)Main.p(mode + " c " + counter ); 

				//	Hashtable<Integer, Hashtable<Integer, Hashtable<String, Hashtable<String,Double>>>> 
				//resultsFreezeSchedulinghorizonColumnCountmeanstd = new Hashtable<Integer, Hashtable<Integer, Hashtable<String, Hashtable<String,Double>>>>();
				//Hashtable<Integer, Hashtable<Integer, Hashtable<String, ArrayList<Double>>>> 
				//dataFreezeSchedulinghorizonColumnList = new Hashtable<Integer, Hashtable<Integer, Hashtable<String, ArrayList<Double>>>>();
				
				
				//String[] columns = {"gournd delay cfr","air delay cfr", "ground delay all", "air delay all"};
				
				for(freezeHorizon = 30; freezeHorizon <= 120; freezeHorizon += 5){					
					
					if(counter == 1){
						resultsFreezeSchedulinghorizonColumnCountmeanstd.put(
							new Integer(freezeHorizon), new  Hashtable<Integer, Hashtable<String, Hashtable<String,Double>>>());
					
						dataFreezeSchedulinghorizonColumnList.put(
							new Integer(freezeHorizon), new  Hashtable<Integer, Hashtable<String, ArrayList<Double>>>());
					}
					
					
					for(schedulingHorizon = 0; schedulingHorizon <= 60; schedulingHorizon += 15){
						
						if(counter == 1){
							//String[] columns = {"gournd delay cfr","air delay cfr", "ground delay all", "air delay all"};
							resultsFreezeSchedulinghorizonColumnCountmeanstd.get(freezeHorizon).put(
									schedulingHorizon, new Hashtable<String, Hashtable<String, Double>>());	
							
							dataFreezeSchedulinghorizonColumnList.get(freezeHorizon).put(
									schedulingHorizon, new Hashtable<String, ArrayList<Double>>());
							
							for (String name: columns){
								resultsFreezeSchedulinghorizonColumnCountmeanstd.get(freezeHorizon)
								.get(schedulingHorizon).put(name, new Hashtable<String, Double>());
								
								dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
								.get(schedulingHorizon).put(name, new ArrayList<Double>());
							}
							
						}
						
						
							
							
							
						


						//RESET TO 0
						flights.resetSchedulingDependentVariables();
						airports.resetToStart();
						//SET AIRPORT DATA TO 0
						for (Enumeration<String> e = absorbedAirportDelayHrs.keys(); e.hasMoreElements();){
							String aName = e.nextElement();
							absorbedAirportDelayHrs.put(aName, 0.0);
						}
						for (Enumeration<String> e = dispensedAirportDelayHrs.keys(); e.hasMoreElements();){
							String aName = e.nextElement();
							dispensedAirportDelayHrs.put(aName, 0.0);
						}

						//int lookAheadMilliSec = minsAhd*minToMillisec;

						int cfr_flights = 0; 
						String airline = "CFR Experiment"; //SWA airline of flights that get to be scheduled in advance
						//int iii = 0;iii++;
						//System.out.println("millisec-days in an integer "+(double)Integer.MAX_VALUE/(1000.0*60*60*24));

						for (Flight f: flightList){
							//SETTING CFR for flight

							//calculate gate uncertainty based on look ahead
							//this function does 2min for every 15min, 
							//so 0,2,4,6,8 min uncertainty for 0,15,30,45,60, min scheduling in advance
							//f.gateUncertainty = (int)(Math.abs( ( f.gateUncertaintyConstant*(minsAhd*2/15) ) )*minToMillisec)*uncertaintyToggle;
							//old
							//int uncertaintyMilliSec = (int)(Math.abs( ( random.nextGaussian()*(minsAhd*2/15) ) )*minToMillisec)*uncertaintyToggle; check!!!!!!!!!!!
							//f.gateUncertainty = uncertaintyMilliSec;

							switch (mode) {

							case IAHCFR:
							{		
								if(airports.getArrivalAirport(f).effectedByCFR(f.arrivalTimeACES+f.taxi_unimpeded_time) 
										&& f.departureAirport.equals("KDFW")
										){
									f.cfrEffected = true;
								}
								U.Assert(f.arrivalAirport.equals("KIAH"));
								//schedulingQueue.add(new SchedulingEvent(f.departureTimeProposed + f.gate_perturbation - lookAheadMilliSec, - 9, mode, f));
								//f.departureTimeProposed += f.gate_perturbation;
								int departureSchedulingTime = f.departureTimeACES + f.gate_perturbation;
								if(f.cfrEffected){
									//talk with Gano about this...............
									//bounds flights to -2+1 with an emphasis on 0 delay, 
									departureSchedulingTime -= (int)(schedulingHorizon*U.toMinutes);
									//NOTE: f.gateUncertaintyConstant = random.nextGaussian(); which is gauss mean 0.0 std 1.0 (-1,1)
									
									//double uncertainty =  f.gateUncertaintyConstant*uncertaintyToggle*; //Math.round
									//double uncertainty =  f.gateUncertaintyConstant*3.0 *uncertaintyToggle; //Math.round
									//uncertainty = uncertainty > 1? random.nextDouble(): uncertainty; // such that flights don't leave later than 1 minute;
									//uncertainty = uncertainty < -2? random.nextDouble()+1: uncertainty; //such that flights don't leave earlier than 2 minutes;
									//uncertainty = Math.Round(uncertainty);
									AirportTree departureAirport = airports.getDepartureAirport(f);
									
									//Main.p(gateR + " gate taxi " + taxiR + " " + departureAirport.taxiUnimpeded + " " + departureAirport.gateStd + " " + departureAirport.taxiMean);
									
									//net effect is 1.1 for 0 horizon and +.05 for every 15 min
									double lookAheadUncertaintyConstant = (int)1.1 + schedulingHorizon/(2*15*10);
									
									double gate_uncertainty_minutes = Math.exp(random.nextGaussian()*
											(departureAirport.gateStd*lookAheadUncertaintyConstant) + departureAirport.gateMean);
									f.gateUncertainty = (int)(gate_uncertainty_minutes*U.toMinutes);
									
									double gateR = random.nextDouble();
									if(departureAirport.gateZeroProbablity > gateR){f.gateUncertainty = 0;}
									
									//this is for -2 +1 min at 0 horizon scheduling. Not allowing leaving early, so only 0 or late.
									if(schedulingHorizon==0){
										double plus1minus2 = random.nextDouble()*3;
										plus1minus2 = plus1minus2 < 1? plus1minus2: 0;
										//U.p(plus1minus2);
										f.taxiUncertainty = (int) (plus1minus2*U.toMinutes); 
										f.gateUncertainty = 0;
									}
									//no one leaves on time..??????????????????????????????????????????????????
									U.Assert(f.gateUncertainty >= 0);
									//uncertainty = random.nextInt(4) - 2;
									//U.p(uncertainty + " " + f.gateUncertainty);
									//f.gateUncertainty = (int)(Math.abs( ( f.gateUncertaintyConstant*(minsAhd*2/15) ) )*minToMillisec)*uncertaintyToggle;
								}
								schedulingQueue.add(new SchedulingEvent(departureSchedulingTime, - 1, ScheduleMode.scheduleDeparture, f));

								//}
							}
							break;

							default:
							{
								Main.p("error in switch1 should not be here");
							}

							} //END SWITCH


						} // end FLIGHT loop

						//Main.p(airline + " " + swa);
						String name = mode+"_"+schedulingHorizon+"_min_ahead_"+montecarlo+"_runs"+".csv";//+dateId
						//String ofolder = "output\\";
						//try{
						//Main.p(name + " " + montecarlo);
						//FileWriter fstream = new FileWriter(workingDirectory+ofolder+name,true);
						//Main.p("should be a file at: "+ workingDirectory+ofolder+name);
						//BufferedWriter out = new BufferedWriter(fstream);
						if(counter==1){
							//calculateDelays(flightList, airline, false, out, true, minsAhd); //???????????????????????????????????
						}
						//Main.p("***************************************************************************");
						SchedulingEvent prevEvent = new SchedulingEvent(0, 0, ScheduleMode.scheduleDeparture, Flight.dummyDeparture(0));
						while(!schedulingQueue.isEmpty()){ // while there are events to process (main loop)

							//execute earliest event;
							SchedulingEvent event = schedulingQueue.remove();

							U.Assert(prevEvent.eventTime <= event.eventTime, prevEvent.mode + " " + prevEvent.eventTime 
									+ " ERROR events happening out of order " + event.mode + " " + event.eventTime);
							
							Flight f = event.flight;
							f.scheduled = true;
							f.numberOfevents++;
							if(f.id==4){
								//U.p(""+event.mode);
							}

							switch (event.mode) {

							case scheduleDeparture:
							{
								//scheduleDeparture(event);
								if(event.flight.cfrEffected){
									scheduleDepartureCFR(event);
								} else 
									scheduleDepartureNonCFR(event);
							}
							break;
							case WheelsOff:
							{	
								if(event.flight.cfrEffected){
									WheelsOffCFR(event);
								} else 
									U.e("should not be here readyToDepart");
							}
							break;
							case scheduleArrival:
							{	
								if(event.flight.cfrEffected){
									scheduleArrivalCFR(event);
								} else 
									scheduleArrivalNonCFR(event);
							}
							break;
							case removeFromDepartureQueue: // for case 3 and 4
							{
								removeFromDepartureQueue(event);
							}
							break;
							case removeFromArrivalQueue: // for case 3 and 4
							{
								removeFromArrivalQueue(event);
							}
							break;
							
							case undef:
							{
								Main.p("should not be here");
								System.err.println("EVENT ERROR SHOULD NOT BE HERE");
							}
							break;
							default:
							{
								Main.p("should not be here");
								System.err.println("EVENT ERROR SHOULD NOT BE HERE");

							}

							U.Assert(false, "should not be here in event loop");
							break;

							} //end switch statement
							//U.p(prevTime + " " + event.eventTime);
							prevEvent = event;

						} //END WHILE OF EVENTS

						//validate
						//flights.validate();
						//airports.getAirport("KRDU").print();
						airports.validate();

						//Main.p(airports.airportList.size() + " airports.airportList.size()");
						//Main.p(absorbedAirportDelayHrs.size() + " absorbedAirportDelayHrs.size()");
						//Main.p(dispensedAirportDelayHrs.size();
						//new BufferedWriter(new FileWriter(workingDirectory+ofolder+"ignore",true))
						//calculateDelays(flightList, airline, true,  false, minsAhd);
						//System.out.println("just for " + airline);
						//calculateDelays(flightList, airline, false, false, minsAhd);
										
						/*
						Hashtable<String, Double> data = new Hashtable <String, Double>();
						for (String col: columns){
							data.put(col, 0d);
						}*/
						double groundDelayCFR = 0, airDelayCFR = 0, groundDelayAll = 0, airDelayAll = 0, arrivalAirportDelay = 0;
						int cfrs = 0, all = 0;
						//double minFt = 45;
						//int shorts = 0, 
						for(Flight f: flightList){
							if(f.scheduled){
								if(f.cfrEffected){
									cfrs++;
									groundDelayCFR+= f.atcGroundDelay;
									airDelayCFR += f.atcAirDelay;
									U.Assert(f.atcGroundDelay + f.atcAirDelay == f.arrivalAirportDelay);
									
									dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
									.get(schedulingHorizon).get(columns[0]).add(f.atcGroundDelay/U.toMinutes);
									dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
									.get(schedulingHorizon).get(columns[1]).add(f.atcAirDelay/U.toMinutes);
									
								} else {
									U.Assert(f.atcAirDelay == f.arrivalAirportDelay);
									U.Assert(f.atcGroundDelay == 0);
								}
								
								groundDelayAll += f.atcGroundDelay;
								airDelayAll += f.atcAirDelay;
								arrivalAirportDelay += f.arrivalAirportDelay;
								all++;
								
								
								dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
								.get(schedulingHorizon).get(columns[2]).add(f.atcGroundDelay/U.toMinutes);
								dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
								.get(schedulingHorizon).get(columns[3]).add(f.atcAirDelay/U.toMinutes);
								
							}
						}
						
						dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
						.get(schedulingHorizon).get(columns[4]).add(arrivalAirportDelay/U.toHours);
						
						groundDelayCFR /= (U.toMinutes*cfrs); 
						airDelayCFR /= (U.toMinutes*cfrs);
						groundDelayAll /= (U.toMinutes*all);
						airDelayAll /= (U.toMinutes*all);
						arrivalAirportDelay /= U.toHours;
						//									[0]				[1]							[2]						[3]						[4]
						//String[] columns = {"gournd delay cfr (min)","air delay cfr (min)", "ground delay all (min)", "air delay all (min)", "arrival airport delay (hr)"};
						
						/*
						dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
						.get(schedulingHorizon).get(columns[0]).add(groundDelayCFR);
						dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
						.get(schedulingHorizon).get(columns[1]).add(airDelayCFR);
						dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
						.get(schedulingHorizon).get(columns[2]).add(groundDelayAll);
						dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
						.get(schedulingHorizon).get(columns[3]).add(airDelayAll);
						dataFreezeSchedulinghorizonColumnList.get(freezeHorizon)
						.get(schedulingHorizon).get(columns[4]).add(arrivalAirportDelay);
						
						*/
						
						//U.e(cfrs + " " + minFt + " " + shorts + " " + tot);
						
						//U.epf("freeze horizon(min),%d,scheduling Horizon(min),%d,arrival airport (min), %.1f, departure airport(min), %.1f, ground(min), %.1f, air(min), %.1f\n",
						//U.pf("%d,%d,%.1f,%.1f,%.1f,%.1f,%.1f\n",freezeHorizon,schedulingHorizon,groundDelayCFR,airDelayCFR,groundDelayAll, airDelayAll, arrivalAirportDelay);
						//paper on ranking to predict delay
						//airports.getAirport("KIAH").printArrTrafficOrdering();
						//airports.getAirport("KIAH").printCaps();
						//System.out.printf("%d from DFW, Flights to KIAH: departure delay %.2f arrival delay %.2f gd %.2f\n", fdfwiah, dd, ad, gd);
						//airports.getAirport("KIAH").printArrTrafficByFlight();

						//out.close();

						//write out to airport
						//if(counter == 1)writeToAirports(workingDirectory+ofolder,name,montecarlo,true); //workingDirectory+ofolder+
						//else writeToAirports(workingDirectory+ofolder,name,montecarlo, false);
						//write out details of 1 run
						//if(counter == 1)writeOut1Run(workingDirectory+ofolder,name, flightList);
						//*/

						//					} catch (Exception e){
						//						System.err.println("Error schedule(): " + e.getMessage());
						//					}

					} //END FREEZE HOR
				} //END SCHED HOR
			} // END by Mode

		} // END Monte carlo
		//		for(int s: delayedIntheAir.keySet()){
		//			if(delayedIntheAir.get(s)!=2){
		//				Main.p(s+" "+delayedIntheAir.get(s));
		//			}
		//		}
		Integer[] fhk = (Integer[]) resultsFreezeSchedulinghorizonColumnCountmeanstd.keySet().toArray(new Integer[0]);  
	    Arrays.sort(fhk);
	    Integer[] shk = (Integer[]) resultsFreezeSchedulinghorizonColumnCountmeanstd.get(fhk[0]).keySet().toArray(new Integer[0]);  
	    Arrays.sort(shk);
	        
		U.p("freeze horizon(min),look ahead (min),variable name,mean,std,min,max");
		for (int fh: fhk){
			for (int sh: shk){
				for (String col: columns){
					resultsFreezeSchedulinghorizonColumnCountmeanstd.get(fh)
						.get(sh).put(col, Stats.count_sum_mean_std_min_max(
								dataFreezeSchedulinghorizonColumnList.get(fh)
									.get(sh).get(col)));
					U.pf("%3d,%2d,%-30s%4.1f,%4.1f,%4.1f,%4.1f\n",
							fh,sh,col+',',
							resultsFreezeSchedulinghorizonColumnCountmeanstd.get(fh).get(sh).get(col).get("mean"),
							resultsFreezeSchedulinghorizonColumnCountmeanstd.get(fh).get(sh).get(col).get("std"),
							resultsFreezeSchedulinghorizonColumnCountmeanstd.get(fh).get(sh).get(col).get("min"),
							resultsFreezeSchedulinghorizonColumnCountmeanstd.get(fh).get(sh).get(col).get("max"));
				}
				//U.p("]");
			}
		}
		
		
		

		//System.out.println("FIN! " + dateFormat.format(new Date()));

		return new ArrayList<Flight>();

	} //END SCHEDULE()

	// /////////////////  //////////////// END OF FCFS


	//depart:
	//schedule a departure meeting dep contraints
	// 
	// 
	//scheduling at gate perturbation.. or gate
	//what if flight is ready to leave before?????
	public void scheduleDepartureCFR(SchedulingEvent event)
	{

		//schedule arrival
		Flight f = event.flight;
		//U.pf("(%.1f,%.1f)\n", f.gateUncertainty/U.toMinutes, f.taxi_perturbation/U.toMinutes);
		int currentTime = event.eventTime;
		U.Assert(f.cfrEffected);
		U.Assert(f.gateUncertainty >= 0);
		U.Assert(currentTime == f.gate_perturbation + f.departureTimeACES - U.toMinutes*schedulingHorizon);
		
		int departureAdditives = + f.taxi_unimpeded_time + f.gate_perturbation; //gate perturbation??
		f.departureTimeFinal = f.departureTimeACES + departureAdditives;
		
		schedulingQueue.add(new SchedulingEvent(currentTime, -8, ScheduleMode.scheduleArrival, f));
		
	}

	public void scheduleArrivalCFR(SchedulingEvent event){
		//U.p("sdfsdfsfd");
		Flight f = event.flight;
		int currentTime = event.eventTime;
		U.Assert(f.cfrEffected);
		U.Assert(f.getDepartureTimeFinal() > 1);
		//int departureAdditives = + f.taxi_unimpeded_time; //gate perturbation??
		//int proposedDepartureTime = f.departureTimeProposed + departureAdditives; //TODO MORE HERE?
		//f.departureTimeFinal = proposedDepartureTime;
		int departureOffset = f.getDepartureTimeFinal()-f.departureTimeACES;
		if(f.firstTimeBeingArrivalScheduled){U.Assert(f.atcGroundDelay == 0);
		U.Assert(departureOffset == f.gate_perturbation+f.taxi_unimpeded_time + f.atcGroundDelay, 
				(f.gate_perturbation+f.taxi_unimpeded_time + f.atcGroundDelay)+" "+departureOffset+" "+f.id+ " " + f.numberOfevents);
		} else U.Assert(departureOffset == f.gate_perturbation+f.taxi_unimpeded_time + f.atcGroundDelay+f.gateUncertainty+f.taxiUncertainty);
		int proposedArrivalTime = f.arrivalTimeACES + departureOffset;
		U.Assert(f.arrivalTimeACES <= proposedArrivalTime);
		if(f.firstTimeBeingArrivalScheduled)
			U.Assert(currentTime <= f.departureTimeACES + f.gate_perturbation, currentTime+ " scheduleArrivalCFR "
					+ (f.departureTimeACES + f.gate_perturbation) + " " + (f.id));
		//U.p((proposedArrivalTime-f.departureTimeFinal) / U.toMinutes);
		if(!f.firstTimeBeingArrivalScheduled)U.Assert(currentTime == f.getDepartureTimeFinal());
		airports.scheduleArrival(f, proposedArrivalTime, currentTime);
		
		if(!f.firstTimeBeingArrivalScheduled){U.Assert(f.atcGroundDelay == f.atcGroundDelay, " " + f.id );}
		if(f.firstTimeBeingArrivalScheduled){
			//wheels off
			U.Assert(f.departureTimeACES + f.gate_perturbation + f.taxi_unimpeded_time + f.atcGroundDelay == f.getDepartureTimeFinal(),
					(f.departureTimeACES + f.gate_perturbation + f.taxi_unimpeded_time + f.atcGroundDelay)
					+ " ?= " + f.getDepartureTimeFinal() + " " + f.id  );

			int wheelsOff = f.departureTimeACES + f.taxi_unimpeded_time + f.gate_perturbation  
					+f.gateUncertainty+f.taxiUncertainty + f.atcGroundDelay;

			U.Assert(wheelsOff>=f.getDepartureTimeFinal());
			//if(f.id ==33722) U.p(wheelsOff + " wheelsOff final " +f.getDepartureTimeFinal()+ " doing dep " + f.id);
			schedulingQueue.add(new SchedulingEvent(wheelsOff, -8, ScheduleMode.WheelsOff, f));
		}
	}
	
	
	//check if flight can make it by speeding up/slowing down,
	public void WheelsOffCFR(SchedulingEvent event){
		//should have ground delay? what to do with it??
		//delete if don't need it
		//use it if there, delete rest if partway through..
		//U.p("sdfsdfsfd");
		Flight f = event.flight;
		int currentTime = event.eventTime;
		U.Assert(f.cfrEffected);
		U.Assert(f.getDepartureTimeFinal() > 1);
		U.Assert(currentTime >= f.getDepartureTimeFinal(), currentTime + " wheelsOff final " +f.getDepartureTimeFinal()+ " no earlies " + f.id + " " + freezeHorizon);//for this case
		int nominalDuration = f.arrivalTimeACES-f.departureTimeACES;
		int shortestDuration =   (int)(nominalDuration/(1+speedUp)); 
		int longestDuration =  (int)(nominalDuration/(1-slowDown));
		U.Assert(currentTime > f.departureTimeACES+f.gate_perturbation+f.taxiUncertainty);

		//TODO tabulation of ground delay
		//flight leaves too early
		if(currentTime < f.getDepartureTimeFinal() && currentTime+longestDuration < f.arrivalTimeFinal){
			U.e( currentTime + " current final" +f.getDepartureTimeFinal()+ " no earlies " + freezeHorizon);
			f.departureTimeFinal = currentTime;
			schedulingQueue.add(new SchedulingEvent(currentTime, -8, ScheduleMode.removeFromArrivalQueue, f));
			f.firstTimeBeingArrivalScheduled = false;
			schedulingQueue.add(new SchedulingEvent(currentTime, -8, ScheduleMode.scheduleArrival, f));
			
			//tabs
		//leaves too late.	
		}else if (currentTime > f.getDepartureTimeFinal() && currentTime + shortestDuration > f.arrivalTimeFinal){
			f.departureTimeFinal = currentTime;
			schedulingQueue.add(new SchedulingEvent(currentTime, -8, ScheduleMode.removeFromArrivalQueue, f));
			schedulingQueue.add(new SchedulingEvent(currentTime, -8, ScheduleMode.scheduleArrival, f));
			f.firstTimeBeingArrivalScheduled = false;
			//U.p("flights leaving late ");
			//tabs
			
		} else {
			f.departureTimeFinal = currentTime;
			//U.p("Smoooooooth");
		}
	}
	
	//schedules departures at perturbed gate time.
	public void scheduleDepartureNonCFR(SchedulingEvent event)
	{
		Flight f = event.flight;
		U.Assert(!f.cfrEffected);
		int departureAdditives = + f.taxi_unimpeded_time + f.gate_perturbation; //gate perturbation??
		f.departureTimeFinal = f.departureTimeACES + departureAdditives;
		int proposedArrivalTime = f.arrivalTimeACES + departureAdditives;
		//flight will schedule arrival the amount of the freeze horizon before arrival
		int freezeHorizonMil = freezeHorizon*(int) Flight.toMinutes;
		//so that don't schedule arrival before departure, otherwise schedule x minutes before arrival
		int timeToScheduleArrival = Math.max(proposedArrivalTime - freezeHorizonMil, f.getDepartureTimeFinal());
		//CFR flights will have been scheduled already
		schedulingQueue.add(new SchedulingEvent(timeToScheduleArrival, proposedArrivalTime, ScheduleMode.scheduleArrival, f));
	}


	//schedules arrivals at perturbed arrivaltime - freeze horizon, or wheels off, whichever is later
	public void scheduleArrivalNonCFR(SchedulingEvent event)
	{
		//Main.p(++ty);
		//TODO logic different if scheduled more than once.
		Main.Assert(!event.flight.cfrEffected,"!event.flight.CFRaffected");
		Flight f = event.flight;
		int currentTime = event.eventTime;
		int proposedArrivalTime = event.coEventTime;
		Main.Assert(f.atcAirDelay == 0, "f.atcAirDelay == 0");
		//there could be ground delay added from adjusting the arrival Queue, which would mean still more than 30min from arrival.
		if(f.atcGroundDelay>0 && f.firstTimeBeingArrivalScheduled){
			f.firstTimeBeingArrivalScheduled = false;
			schedulingQueue.add(new SchedulingEvent(currentTime+f.atcGroundDelay, proposedArrivalTime+f.atcGroundDelay, ScheduleMode.scheduleArrival, f));
		}
		else {
			airports.scheduleArrival(f, proposedArrivalTime, currentTime);
			f.arrivalTimeFrozen = true;
		}

	}

	public void removeFromDepartureQueue(SchedulingEvent event)
	{
		Flight f = event.flight;
		airports.removeFlightFromDepartureQueue(f);
	}

	public void removeFromArrivalQueue(SchedulingEvent event)
	{
		//TODO make this better by rebalancing queue after? queue repair?
		Flight f = event.flight;
		airports.removeFlightFromArrivalQueue(f);
	}
	
	public void summarize(Collection<Flight> flightList){
		ArrayList<Double> timeDataE = new ArrayList<Double>();
		//compares to baseline
		for(Flight f: flightList){ 
			if(f.scheduled){
				//String first = "none", cur = "then"; int tot = 0; double mint = Double.MAX_VALUE
			} //end IF scheduled
		}//END for flights
		Hashtable<String, Double> timeE = Stats.count_sum_mean_std_min_max(timeDataE);

		/*	
		Hashtable<String,Double> results = new Hashtable<String,Double>();
		results.put("avgTimeE", timeE.get("mean"));
		results.put("stdTimeE", timeE.get("std"));
		results.put("avgPassE", totalPassesE.get("mean"));
		results.put("stdPassE", totalPassesE.get("std"));
		results.put("avgTimeU", timeU.get("mean"));
		results.put("stdTimeU", timeU.get("std"));
		results.put("avgPassU", totalPassedByU.get("mean"));
		results.put("stdPassU", totalPassedByU.get("std"));

		return results;
		//*/
	}
	
	/*
	public void scheduleDeparture(SchedulingEvent event)
	{
		//Main.p(++sici);
		//TODO logic different if being scheduled more than once
		//schedule priority or regular departure based on CFR
		Flight f = event.flight;
		//duration speeds
		int nominalDuration = f.arrivalTimeProposed-f.departureTimeProposed;
		int fastestDuration =  (int)(nominalDuration/(1+speedUp)); 
		Main.Assert(f.gateUncertainty<=0, "err uncertainty is '-' " + f.gateUncertainty); //????
		int delayDelta = Math.max(f.gateUncertainty,f.atcGroundDelay); 
		Main.Assert(f.atcAirDelay == 0, "f.atcAirDelay==0");
		Main.Assert(f.atcGroundDelay == 0, "f.atcGroundDelay==0");
		int departureAdditives = + f.taxi_unimpeded_time; //gate perturbation??
		int proposedDepartureTime = f.departureTimeProposed + departureAdditives; //TODO MORE HERE?

		if(airports.getArrivalAirport(f).effectedByCFR(f.arrivalTimeProposed+departureAdditives) 
				&& f.departureAirport.equals("KDFW")
				){
			f.cfrEffected = true;
			//f.airline = "cfr";
		}

		airports.scheduleDeparture(f, proposedDepartureTime, event.eventTime);	
		int proposedArrivalTime = f.arrivalTimeProposed + departureAdditives + f.atcGroundDelay;
		//flight will schedule arrival 30min before
		int minBeforeArrival = freezeHorizon*(int) Flight.toMinutes;
		//so that don't schedule arrival before departure, otherwise schedule x minutes before arrival
		int timeToScheduleArrival = Math.max(proposedArrivalTime - minBeforeArrival, f.departureTimeFinal); //use current or departure time?
		//schedule arrivals at departure time for cfrs;
		if(f.cfrEffected){
			//if(f.cfrEffected){
			timeToScheduleArrival = f.departureTimeFinal;
		}
		schedulingQueue.add(new SchedulingEvent(timeToScheduleArrival, proposedArrivalTime, ScheduleMode.scheduleArrival, f));
	}
	 */
	/*
	public void scheduleDeparture(SchedulingEvent event)
	{
		//Main.p(++sici);
		//TODO logic different if being scheduled more than once
		//schedule priority or regular departure based on CFR
		//schedule arrival if CFR
		Flight f = event.flight;
		//duration speeds
		int nominalDuration = f.arrivalTimeProposed-f.departureTimeProposed;
		int fastestDuration =  (int)(nominalDuration/(1+speedUp)); 
		Main.Assert(f.gateUncertainty<=0, "err uncertainty is '-' " + f.gateUncertainty); //????
		int delayDelta = Math.max(f.gateUncertainty,f.atcGroundDelay); 
		Main.Assert(f.atcAirDelay == 0, "f.atcAirDelay==0");
		Main.Assert(f.atcGroundDelay == 0, "f.atcGroundDelay==0");
		if(airports.getArrivalAirport(f).effectedByCFR(f.departureTimeProposed+f.taxi_unimpeded_time)){
			f.cfrEffected = true;
			//cfr_flights++;
		}
		int departureAdditives = + f.taxi_unimpeded_time; //gate perturbation??
		int proposedDepartureTime = f.departureTimeProposed + departureAdditives; //TODO MORE HERE?
		int proposedArrivalTime = f.arrivalTimeProposed + departureAdditives;
		f.cfrEffected = airports.effectedByCFR(f);
		//f.CFRaffected
		if(f.cfrEffected){
		  /*int diff = -1;
			int departureSchedulingDelay = 0;
			int i = 0;

			while (diff != 0){
//				departureSchedulingDelay += 
//						airports.getSoonestPriorityDeparture(f, proposedDepartureTime + departureSchedulingDelay, event.eventTime);
//				diff = airports.getSoonestArrival(f, proposedArrivalTime + departureSchedulingDelay, event.eventTime);
//				departureSchedulingDelay += diff;

				//DEPARTURE
				int depDelay = 
						airports.getSoonestPriorityDeparture(f, proposedDepartureTime + departureSchedulingDelay, event.eventTime);

				if(depDelay > 0){Main.p(f.id + " wackness ");}
				departureSchedulingDelay += depDelay;
				f.departureAirportDelay += depDelay;
				//ARRIVAL
				int arrDelay = airports.getSoonestArrival(f, proposedArrivalTime + departureSchedulingDelay, event.eventTime);
				departureSchedulingDelay += arrDelay;
				f.arrivalAirportDelay += arrDelay;
				diff = arrDelay;
			}
	 */
	//if(f.id==-851)Main.p(f.departureTimeProposed + " main proposedDepartureTime + departureSchedulingDelay " + proposedDepartureTime + " "+ departureSchedulingDelay+"\n");
	//int shouldBeZero = airports.schedulePriorityDeparture(f, proposedDepartureTime + departureSchedulingDelay, event.eventTime);
	//Main.Assert(shouldBeZero==0, "shouldBeZero not 0");
	//schedule arrival at time of departure scheduling;
	//shouldBeZero = airports.scheduleArrival(f, proposedArrivalTime + departureSchedulingDelay, event.eventTime);
	//Main.Assert(shouldBeZero==0, "shouldBeZero not 0");
	//f.atcGroundDelay = departureSchedulingDelay; //OVER WRITES, OK????
	//f.departureAirportDelay = f.departureTimeFinal - proposedDepartureTime;
	//f.arrivalAirportDelay = f.arrivalTimeFinal - proposedArrivalTime;
	/*
			airports.schedulePriorityDeparture(f, proposedDepartureTime, event.eventTime);	
		} else {
			airports.scheduleNonPriorityDeparture(f, proposedDepartureTime, event.eventTime);
			//schedule 30min in before arrival.
			//
		}
		proposedArrivalTime += f.atcGroundDelay;
		schedulingQueue.add(new SchedulingEvent((proposedArrivalTime - 30*(int) Flight.toMinutes), proposedArrivalTime, ScheduleMode.scheduleArrival, f));
		//if(f.departureAirport.equals("KCLT"));airports.getDepartureAirport(f).printDepTrafficByFlight();
		////////////////////nevermind this junk, add in code for event to schedule arrival 30min from landing

		int pushback = f.departureTimeProposed + f.gate_perturbation + delayDelta;
		int wheelsOffTime = pushback + f.taxi_unimpeded_time + f.taxi_perturbation; //add in taxi??
		f.wheelsOffTime = wheelsOffTime;
		int lastOnTimeDeparturePoint = f.arrivalFirstSlot - fastestDuration; 
		//totalGroundDelay+=f.departureDelayFromArrivalAirport/60000.0;
		if (wheelsOffTime > lastOnTimeDeparturePoint){
			//flight leaves too late.
			/////////schedulingQueue.add(new SchedulingEvent(lastOnTimeDeparturePoint, -8, ScheduleMode.removeByFlight, f));// -8 dummy value
			///////////schedulingQueue.add(new SchedulingEvent(wheelsOffTime, -4, ScheduleMode.scheduleInTheAirByFlight, f));
		}

	}
	 */




	public void calculateDelays(ArrayList<Flight> flightList, String airline, boolean calculateAll,
			boolean printHeader, int minutesAhead){
		if(printHeader){

		}

		int delayedOnGround = 0, delayedInAir = 0, missedSlots = 0; 
		double totalAirDelay = 0, totalGroundDelay = 0, maxGroundDelay = 0, maxAirDelay = 0, totalMissedSlotMetric = 0, 
				meanJiggles = 0, totalJiggles =0;

		ArrayList<Double> groundDelay = new ArrayList<Double>();
		ArrayList<Double> airDelay = new ArrayList<Double>();
		ArrayList<Double> missedSlotMetric = new ArrayList<Double>();
		ArrayList<Double> totalJiggleAmount = new ArrayList<Double>();

		int totalSWAs = 0;

		for (Flight f: flightList){

			if(f.airline.equals(airline) || calculateAll || (airline.equals("CFR") && f.cfrEffected) ){

				//STD
				f.print();
				totalSWAs++;

				groundDelay.add(f.atcGroundDelay/U.toMinutes);
				airDelay.add(f.atcAirDelay/U.toMinutes);
				missedSlotMetric.add((f.arrivalTimeFinal - f.arrivalFirstSlot)/U.toMinutes);
				totalJiggleAmount.add(f.totalJiggleAmount/1000.0); // in secs


				//missed slots
				if(f.arrivalFirstSlot != f.arrivalTimeFinal){ 
					missedSlots++;
					totalMissedSlotMetric += f.arrivalTimeFinal - f.arrivalFirstSlot;
				}
				totalJiggles += f.totalJiggleAmount/1000.0; // in seconds
				meanJiggles += f.numberOfJiggles;

				//ground delays
				if(f.atcGroundDelay != 0) { 
					delayedOnGround++; 
					totalGroundDelay += f.atcGroundDelay;
					maxGroundDelay = Math.max(f.atcGroundDelay/U.toMinutes,maxGroundDelay);
					//puts delay in departure and arrival airports, must add entry if does not exist for each case
					if(dispensedAirportDelayHrs.get(f.arrivalAirport) != null){
						dispensedAirportDelayHrs.put(f.arrivalAirport, 
								dispensedAirportDelayHrs.get(f.arrivalAirport)+f.atcGroundDelay/U.toHours);
					} else {
						System.err.println(" why does airport not exist?");
						dispensedAirportDelayHrs.put(f.arrivalAirport, f.atcGroundDelay/U.toHours);
					}
					if(absorbedAirportDelayHrs.get(f.departureAirport) != null){
						absorbedAirportDelayHrs.put(f.departureAirport, 
								absorbedAirportDelayHrs.get(f.departureAirport)+f.atcGroundDelay/U.toHours);
					} else {
						System.err.println(" why does airport not exist?");
						absorbedAirportDelayHrs.put(f.departureAirport, f.atcGroundDelay/U.toHours);
					}			
				}

				// air delays
				if(f.atcAirDelay != 0){

					delayedInAir++; 
					totalAirDelay+=f.atcAirDelay ;
					maxAirDelay = Math.max(f.atcAirDelay/U.toMinutes,maxAirDelay);
					//puts delay in departure and arrival airports, must add entry if does not exist for each case
					if(dispensedAirportDelayHrs.get(f.arrivalAirport) != null){
						dispensedAirportDelayHrs.put(f.arrivalAirport, 
								dispensedAirportDelayHrs.get(f.arrivalAirport)+f.atcAirDelay/U.toHours);
					} else {
						System.err.println(" why does airport not exist?");
						dispensedAirportDelayHrs.put(f.arrivalAirport, f.atcAirDelay/U.toHours);
					}	
				}


			} // END airline if

		} // END FLights loop

		totalAirDelay /= U.toMinutes; totalGroundDelay /= U.toMinutes; totalMissedSlotMetric /= U.toMinutes;

		/*
		if(counter == 1){
		Main.p("std ground " + standardDeviation(groundDelay.toArray(new Double[groundDelay.size()])) + " mean " + totalGroundDelay / flightList.size());
		Main.p("std air " + standardDeviation(airDelay.toArray(new Double[airDelay.size()])) + " mean " + totalAirDelay / flightList.size());
		Main.p("std slot " + standardDeviation(missedSlotMetric.toArray(new Double[missedSlotMetric.size()])) + " mean " + totalMissedSlotMetric / flightList.size());
		Main.p("std jiggle " + standardDeviation(totalJiggleAmount.toArray(new Double[totalJiggleAmount.size()])) + " mean " + totalJiggles / flightList.size());
		}
		 */


		//System.out.printf("%-35s", mode+",");
		//System.out.printf("here"); //9
		if(printHeader){
			System.out.println("minAhead,"); //9
			System.out.println("avg delay per flight,"); //9
			System.out.println("total weighted delay hrs"+","); //9
			System.out.println("totalSWAs-missedSlots"+","); //1 flights that made slots
			System.out.println("missedSlots"+","); //1 flights that made slots
			System.out.println("totalMissedSlotMetric/totalSWAs"+","); //2
			System.out.println("delayedOnGround"+","); //3
			System.out.println("delayedInAir"+","); //4 !!
			//System.out.println("maxGroundDelay"+","); //5
			//System.out.println("maxAirDelay"+","); //6 !!
			System.out.println("totalGroundDelay hrs"+","); //7
			System.out.println("totalAirDelay hrs"+","); //8 !!
			//System.out.println("totalGroundDelay+totalAirDelay) hrs"+","); //9
			//System.out.println("meanJiggles/(double)totalSWAs"+","); //8 !!
			System.out.println("(totalGroundDelay+totalAirDelay)/((double)totalSWAs)");
		} else {
			System.out.print(minutesAhead+",");
			System.out.printf("%5.2f,",(totalGroundDelay+totalAirDelay*2)/totalSWAs); //9
			System.out.printf("%7.1f,",(totalGroundDelay+totalAirDelay*2)/60); //9
			System.out.printf("%7d, ",totalSWAs-missedSlots); //1 flights that made slots
			System.out.printf("%5d,", missedSlots);
			System.out.printf("%5.1f, ",totalMissedSlotMetric/totalSWAs); //2
			System.out.printf("%5d, ",delayedOnGround); //3
			System.out.printf("%5d, ",delayedInAir); //4 !!
			//System.out.printf("%5.0f, ",maxGroundDelay); //5
			//System.out.printf("%5.0f, ",maxAirDelay); //6 !!
			System.out.printf("%6.1f, ",totalGroundDelay/60); //7
			System.out.printf("%6.1f, ",totalAirDelay/60); //8 !!
			//System.out.printf("%6.0f, ",(totalGroundDelay+totalAirDelay)/60); //9
			//System.out.printf("%5.1f,",meanJiggles/(double)totalSWAs); //8 !!
			System.out.printf("%5.1f",(totalGroundDelay+totalAirDelay)/((double)totalSWAs)*60); //9
			System.out.print("\n"); //9
		}

		//System.out.printf("%5.1f\n",totalJiggles/(double)totalSWAs); //9


	} //END CALCULATE DELAYS()



	public void calculateDelays(ArrayList<Flight> flightList, String airline, boolean calculateAll,
			BufferedWriter out, boolean printHeader, int minutesAhead){

		if(printHeader){
			try{


				out.write("# of flights that made their slots,");
				out.write("# of flights that missed their slots,");
				out.write("avg amount flights missed their slots by (min),");
				out.write("std amount flights missed their slots by (min),");


				out.write("# of flights  delayed on the ground,");
				out.write("max ground delay (min),");
				out.write("avg ground delay (min),");
				out.write("std ground delay (min),");
				out.write("total ground delay (hrs),");

				out.write("# of flights  delayed in the air,");
				out.write("max air delay (min),");
				out.write("avg air delay (min),");
				out.write("std air delay (min),");
				out.write("total air delay (hrs),");

				out.write("total delay (hrs),");
				out.write("total weighted delay (hrs),");
				out.write("avg jiggles per flight,"); //8 !!
				out.write("avg jiggle amount after last scheduling per flight (SECS),"); //9
				out.write("std jiggle amount after last scheduling per flight (SECS)\n");

			} catch (Exception e){
				System.out.println(e.getMessage());
			}

		}


		int delayedOnGround = 0, delayedInAir = 0, missedSlots = 0; 
		double totalAirDelay = 0, totalGroundDelay = 0, maxGroundDelay = 0, maxAirDelay = 0, totalMissedSlotMetric = 0, 
				meanJiggles = 0, totalJiggles =0;

		ArrayList<Double> groundDelay = new ArrayList<Double>();
		ArrayList<Double> airDelay = new ArrayList<Double>();
		ArrayList<Double> missedSlotMetric = new ArrayList<Double>();
		ArrayList<Double> totalJiggleAmount = new ArrayList<Double>();

		int totalSWAs = 0;

		for (Flight f: flightList){

			if(f.airline.equals(airline) || calculateAll || (airline.equals("CFR") && f.cfrEffected) ){

				//STD
				totalSWAs++;

				groundDelay.add(f.atcGroundDelay/U.toMinutes);
				airDelay.add(f.atcAirDelay/U.toMinutes);
				missedSlotMetric.add((f.arrivalTimeFinal - f.arrivalFirstSlot)/U.toMinutes);
				totalJiggleAmount.add(f.totalJiggleAmount/1000.0); // in secs


				//missed slots
				if(f.arrivalFirstSlot != f.arrivalTimeFinal){ 
					missedSlots++;
					totalMissedSlotMetric += f.arrivalTimeFinal - f.arrivalFirstSlot;
				}
				totalJiggles += f.totalJiggleAmount/1000.0; // in seconds
				meanJiggles += f.numberOfJiggles;

				//ground delays
				if(f.atcGroundDelay != 0) { 
					delayedOnGround++; 
					totalGroundDelay += f.atcGroundDelay;
					maxGroundDelay = Math.max(f.atcGroundDelay/U.toMinutes,maxGroundDelay);
					//puts delay in departure and arrival airports, must add entry if does not exist for each case
					if(dispensedAirportDelayHrs.get(f.arrivalAirport) != null){
						dispensedAirportDelayHrs.put(f.arrivalAirport, 
								dispensedAirportDelayHrs.get(f.arrivalAirport)+f.atcGroundDelay/U.toHours);
					} else {
						System.err.println(" why does airport not exist?");
						dispensedAirportDelayHrs.put(f.arrivalAirport, f.atcGroundDelay/U.toHours);
					}
					if(absorbedAirportDelayHrs.get(f.departureAirport) != null){
						absorbedAirportDelayHrs.put(f.departureAirport, 
								absorbedAirportDelayHrs.get(f.departureAirport)+f.atcGroundDelay/U.toHours);
					} else {
						System.err.println(" why does airport not exist?");
						absorbedAirportDelayHrs.put(f.departureAirport, f.atcGroundDelay/U.toHours);
					}			
				}

				// air delays
				if(f.atcAirDelay != 0){
					delayedInAir++; 
					totalAirDelay+=f.atcAirDelay ;
					maxAirDelay = Math.max(f.atcAirDelay/U.toMinutes,maxAirDelay);
					//puts delay in departure and arrival airports, must add entry if does not exist for each case
					if(dispensedAirportDelayHrs.get(f.arrivalAirport) != null){
						dispensedAirportDelayHrs.put(f.arrivalAirport, 
								dispensedAirportDelayHrs.get(f.arrivalAirport)+f.atcAirDelay/U.toHours);
					} else {
						System.err.println(" why does airport not exist?");
						dispensedAirportDelayHrs.put(f.arrivalAirport, f.atcAirDelay/U.toHours);
					}	
				}


			} // END airline if

		} // END FLights loop

		totalAirDelay /= U.toMinutes; totalGroundDelay /= U.toMinutes; totalMissedSlotMetric /= U.toMinutes;

		/*
		if(counter == 1){
		Main.p("std ground " + standardDeviation(groundDelay.toArray(new Double[groundDelay.size()])) + " mean " + totalGroundDelay / flightList.size());
		Main.p("std air " + standardDeviation(airDelay.toArray(new Double[airDelay.size()])) + " mean " + totalAirDelay / flightList.size());
		Main.p("std slot " + standardDeviation(missedSlotMetric.toArray(new Double[missedSlotMetric.size()])) + " mean " + totalMissedSlotMetric / flightList.size());
		Main.p("std jiggle " + standardDeviation(totalJiggleAmount.toArray(new Double[totalJiggleAmount.size()])) + " mean " + totalJiggles / flightList.size());
		}
		 */


		//System.out.printf("%-35s", mode+",");
		//System.out.printf("here"); //9
		if(printHeader){
			System.out.println("minAhead,"); //9
			System.out.println("avg delay per flight,"); //9
			System.out.println("total weighted delay hrs"+","); //9
			System.out.println("totalSWAs-missedSlots"+","); //1 flights that made slots
			System.out.println("missedSlots"+","); //1 flights that made slots
			System.out.println("totalMissedSlotMetric/totalSWAs"+","); //2
			System.out.println("delayedOnGround"+","); //3
			System.out.println("delayedInAir"+","); //4 !!
			//System.out.println("maxGroundDelay"+","); //5
			//System.out.println("maxAirDelay"+","); //6 !!
			System.out.println("totalGroundDelay hrs"+","); //7
			System.out.println("totalAirDelay hrs"+","); //8 !!
			//System.out.println("totalGroundDelay+totalAirDelay) hrs"+","); //9
			//System.out.println("meanJiggles/(double)totalSWAs"+","); //8 !!
			System.out.println("(totalGroundDelay+totalAirDelay)/((double)totalSWAs)");
		} else {
			System.out.print(minutesAhead+",");
			System.out.printf("%5.2f,",(totalGroundDelay+totalAirDelay*2)/totalSWAs); //9
			System.out.printf("%7.1f,",(totalGroundDelay+totalAirDelay*2)/60); //9
			System.out.printf("%7d, ",totalSWAs-missedSlots); //1 flights that made slots
			System.out.printf("%5d,", missedSlots);
			System.out.printf("%5.1f, ",totalMissedSlotMetric/totalSWAs); //2
			System.out.printf("%5d, ",delayedOnGround); //3
			System.out.printf("%5d, ",delayedInAir); //4 !!
			//System.out.printf("%5.0f, ",maxGroundDelay); //5
			//System.out.printf("%5.0f, ",maxAirDelay); //6 !!
			System.out.printf("%6.1f, ",totalGroundDelay/60); //7
			System.out.printf("%6.1f, ",totalAirDelay/60); //8 !!
			//System.out.printf("%6.0f, ",(totalGroundDelay+totalAirDelay)/60); //9
			//System.out.printf("%5.1f,",meanJiggles/(double)totalSWAs); //8 !!
			System.out.printf("%5.1f",(totalGroundDelay+totalAirDelay)/((double)totalSWAs)*60); //9
			System.out.print("\n"); //9
		}

		//System.out.printf("%5.1f\n",totalJiggles/(double)totalSWAs); //9

		//WRITING OUT TO MC FILE
		try {
			out.write(flightList.size()-missedSlots+",");
			out.write(missedSlots+",");
			out.write(totalMissedSlotMetric/flightList.size() + ",");
			out.write(standardDeviation(missedSlotMetric.toArray(new Double[missedSlotMetric.size()])) +",");

			out.write(delayedOnGround + ",");
			out.write(maxGroundDelay + ",");
			out.write(totalGroundDelay/flightList.size() + ",");
			out.write(standardDeviation(groundDelay.toArray(new Double[groundDelay.size()])) +",");
			out.write(totalGroundDelay/60 + ",");

			out.write(delayedInAir + ",");
			out.write(maxAirDelay + ",");
			out.write(totalAirDelay/flightList.size() + ",");
			out.write(standardDeviation(airDelay.toArray(new Double[airDelay.size()])) +",");
			out.write(totalAirDelay/60 + ",");


			out.write(totalGroundDelay+totalAirDelay +",");
			out.write(((totalGroundDelay+totalAirDelay*2)/60) +",");

			out.write(meanJiggles/(double)flightList.size()+","); //8 !!
			out.write(totalJiggles/(double)flightList.size()+","); //9
			out.write(standardDeviation(totalJiggleAmount.toArray(new Double[totalJiggleAmount.size()])) +"\n");

		} catch (IOException e) {
			System.out.println(e.getMessage());
		}

	} //END CALCULATE DELAYS()




	public void writeToAirports(String workingDirectory, String name, int montecarlo, boolean writeNames){ 
		//write by airport
		double totalAirport = 0;
		try{								//WRITE DISPENSED
			// Create file 
			FileWriter fstream = new FileWriter(workingDirectory+"delay_dispensed_by_airport_"+name,true);
			BufferedWriter out = new BufferedWriter(fstream);
			//			out.write("name,delay(hrs)\n");
			//
			//			for (Enumeration<String> e = dispensedAirportDelayHrs.keys(); e.hasMoreElements();){
			//				String a = e.nextElement();
			//				//Main.p(a+" " + dispensedAirportDelayHrs.get(a)/montecarlo);
			//				out.write(a+"," + dispensedAirportDelayHrs.get(a)/montecarlo+"\n");
			//				totalAirport+=dispensedAirportDelayHrs.get(a)/montecarlo;
			//			}
			//			out.close();
			//Main.p("total arrivalAirport dispensed" + totalAirport);

			if(writeNames){
				for (Enumeration<String> e = dispensedAirportDelayHrs.keys(); e.hasMoreElements();){
					String aName = e.nextElement();
					//Main.p(a+" " + absorbedAirportDelayHrs.get(a)/montecarlo);
					out.write(aName+",");
				} out.write("\n");

			}

			for (Enumeration<String> e = dispensedAirportDelayHrs.keys(); e.hasMoreElements();){
				String aName = e.nextElement();
				//Main.p(a+" " + absorbedAirportDelayHrs.get(a)/montecarlo);
				out.write(dispensedAirportDelayHrs.get(aName)+",");
			} out.write("\n");
			out.close();

		}catch (Exception e){//Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}

		//Main.p(totalAirport + " " + (avgDoubles[2]+avgDoubles[3]));
		totalAirport = 0;
		//WRITE ABSORBED
		try{
			// Create file 
			FileWriter fstream = new FileWriter(workingDirectory+"delay_absorbed_by_airport_"+name,true);
			BufferedWriter out = new BufferedWriter(fstream);

			if(writeNames){
				for (Enumeration<String> e = absorbedAirportDelayHrs.keys(); e.hasMoreElements();){
					String aName = e.nextElement();
					//Main.p(a+" " + absorbedAirportDelayHrs.get(a)/montecarlo);
					out.write(aName+",");
				} out.write("\n");

			}

			for (Enumeration<String> e = absorbedAirportDelayHrs.keys(); e.hasMoreElements();){
				String aName = e.nextElement();
				//Main.p(a+" " + absorbedAirportDelayHrs.get(a)/montecarlo);
				out.write(absorbedAirportDelayHrs.get(aName)+",");
			} out.write("\n");			
			out.close();

		}catch (Exception e){//Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
	}



	public void writeOut1Run(String workingDirectory, String name, ArrayList<Flight> flightList){
		Collections.sort(flightList, new flightIDComparator()); 
		//*
		try{
			// Create file 
			FileWriter fstream = new FileWriter(workingDirectory+"distribution_"+name);
			BufferedWriter out = new BufferedWriter(fstream);
			out.write("flight id," +
					//events
					"scheduled gate? departure time," +
					"wheels off time," +
					"wheelsoff-pushback offset min," + // for debugging

					"scheduled arrival time," +
					"first arrival slot," +
					"final arrival slot," +
					"final-first arrival offset min," + // debug

					//uncertainties
					"unimpeded taxitime," +
					"taxi perturbation," +
					"gate perturbation," +
					"gate uncertainty," + 

					//delays
					"ground delay," +
					"airborne delay," +
					"number of jiggles," +
					"total jiggle amount after last scheduling" + 
					"\n");

			for (Flight f: flightList){
				out.write(f.id +"," +
						//events
						f.departureTimeACES +","+
						f.wheelsOffTime + "," +
						(f.wheelsOffTime - f.departureTimeACES) + "," + // for debugging

						f.arrivalTimeACES +","+ 
						f.arrivalFirstSlot +","+
						f.arrivalTimeFinal +","+
						(f.arrivalTimeFinal - f.arrivalFirstSlot) + "," + // debug

						////uncertainties
						f.taxi_unimpeded_time + "," + 
						f.taxiUncertainty + "," + 
						f.gate_perturbation + "," + 
						f.gateUncertainty+ "," +

						//delays
						f.atcGroundDelay +","+ 
						f.atcAirDelay+ "," + 
						f.numberOfJiggles +","+ 
						f.totalJiggleAmount + 
						"\n"
						);	
			}
			//Close the output stream
			out.close();
		}catch (Exception e){//Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}

	}

	public void writeOut(ArrayList<Flight> flightList, String workingDirectory, boolean prior){
		Collections.sort(flightList, new flightIDComparator());
		double totalGroundDelay = 0, totalAirDelaylAirDelay = 0; double totalAirDelaylDelay = 0;
		String name = "at_call_for_release_schedule";
		if(prior) name = "prior_to_call_for_release_schedule"; 
		//*
		try{
			// Create file 
			FileWriter fstream = new FileWriter(workingDirectory+name+".csv");
			BufferedWriter out = new BufferedWriter(fstream);
			out.write("flight id," +
					"unimpeded taxitime," +
					"taxi perturbation," +
					"gate perturbation," +
					"ground delay," +
					"airborne delay," +
					"scheduled departure time," +
					"wheeld off time," +
					"scheduled arrival time," +
					"wheels on time\n");

			for (Flight f: flightList){
				out.write(f.id +"," + 
						f.taxi_unimpeded_time + "," + 
						f.taxiUncertainty + "," + 
						f.gate_perturbation + "," + 
						f.atcGroundDelay +","+ 
						f.atcAirDelay+ "," + 
						f.departureTimeACES +","+ 
						f.wheelsOffTime + "," + 
						f.arrivalTimeACES +","+ 
						f.arrivalTimeFinal +"\n"
						);	
				totalGroundDelay += f.atcGroundDelay/3600000.0;
				totalAirDelaylAirDelay += f.atcAirDelay/3600000.0;
			}
			//Close the output stream
			out.close();
		}catch (Exception e){//Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
		//Main.p(a + " a b " + b); 		" totalAirDelayl flightList: " + flightList.size() +
		//Main.p(name +  "\ntotalAirDelayL: " + Math.round((totalAirDelaylAirDelay+totalGroundDelay))+" hrs\nground delay: " + Math.round(totalGroundDelay)
		//		+ " hrs \nairborne delay: " + Math.round(totalAirDelaylAirDelay) + " hrs");

	}

	// std is the sqrt of sum of variance (values-mean) squared divided by n (n-1 for sample std)
	// Change ( n - 1 ) to n if you have complete data instead of a sample.
	public static double standardDeviation(Double data[])
	{
		final int n = data.length;
		// return false if n is too small
		if(n<2) return Double.NaN;
		// Calculate the mean
		double mean = 0;
		for (int i=0; i<n; i++){mean += data[i];}
		mean /= n;
		// calculate the sum of squares
		double sum = 0;
		for ( int i=0; i<n; i++ ){
			final double v = data[i] - mean;
			sum += v*v;
		}
		return Math.sqrt(sum /n);
	}

	public void testrt(){
		java.util.PriorityQueue<SchedulingEvent> sr = new java.util.PriorityQueue<SchedulingEvent>();
		/*
		sr.add(new SchedulingEvent(9, -34));
		sr.add(new SchedulingEvent(3, -64));
		sr.add(new SchedulingEvent(1, 34));
		sr.add(new SchedulingEvent(6, 5));
		sr.add(new SchedulingEvent(5, 2));
		sr.add(new SchedulingEvent(99, 1));
		sr.add(new SchedulingEvent(2, -3));
		//*/
		while(!sr.isEmpty()){
			sr.poll().print();
		}

	}





}