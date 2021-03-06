package fcfs;

import java.util.*;

/*
 * This is the utilities class (U)
 * It also contains global values and settings, as well as utility methods.
 * 
 */

//Utility Class
public class U{	
	static java.io.PrintStream io = System.out;
	static boolean debug = false;
	static int watchingFlight = -1;
	static boolean verbose = false;
	//Wed, 18 Apr 2012 00:00:00 UTC  // Month is 0 based!!??
	static final double simulationStart = (double)(new GregorianCalendar(2011,1-1,3).getTimeInMillis());
	
	static final java.text.DateFormat dateFormat = new java.text.SimpleDateFormat("yyyy:M:dd:HH:mm:ss");
	static final double toMinutes = 60*1000.0; //convert minutes to milliseconds
	static final double toHours   = 60*toMinutes; //convert hours to milliseconds

	//FOLDER FILE NAMES
	static String fs = System.getProperties().getProperty("file.separator");
	static String workingDirectory = System.getProperties().getProperty("user.dir") + fs; //OR Set this to static value.
	static final String inputFolder = "input" + fs;
	static final String outFolder = "output" + fs;
	static final String ACESflightTrackData = "job_268_sector_transitTime_takeoffLanding_48h_1_rtma_takeoff_eta_SAMPLE_ACES_TRACK_DATA.csv"; //clean_job.csv
	static final String airportCapacity = "AdvancedState_Hourly_Runways_AllCapacities_20110103_20110104.csv";
	
	public static String timeToDate(int time){
		return new java.text.SimpleDateFormat("yyyy:M:dd:HH:mm:ss:SSSS").format(new java.util.Date((long)time));
	}
	public static String timeToDateAdjusted(int time){
		double t = time;
		t += simulationStart;
		return new java.text.SimpleDateFormat("yyyy:M:dd:HH:mm:ss").format(new java.util.Date((long)t));
	}
	
	public static String timeToDateAdjustedShort(int time){
		double t = time;
		t += simulationStart;
		return new java.text.SimpleDateFormat("D:HH:mm:ss").format(new java.util.Date((long)t));
	}
	
	public static String timeToString(int time){
		return new java.text.SimpleDateFormat("yyyy:M:DDD:HH:mm:ss:SSSS").format(new java.util.Date((long)time));
	}
	
	public static boolean Assert(boolean a, String expression){ 
		if(!a){throw new java.lang.Error("FAILED: " + expression);}
		return a;}
	
	public static boolean Assert(boolean a){ 
		if(!a){throw new java.lang.Error("FAILED: Assert()");}
		return a;}
	
	public static void pp(Object s) {System.out.print(s.toString());} 
	public static void p(String s){ System.out.println(s);}
	public static void p(int s){ System.out.println(s);}
	public static void p(double s){ System.out.println(s);}
	public static void e(Object s) {System.err.print(s.toString()+"\n");}
	public static void pf(String format, Object... args){ System.out.printf(format, args);}
	public static void epf(String format, Object... args){ System.err.printf(format, args);}
	
	public static void start() { System.out.println("START! " + dateFormat.format(new Date()));}
	public static void end() { System.out.println("FIN! " + dateFormat.format(new Date()));}
	public static String now() { return dateFormat.format(new Date()).toString();}

	
}

