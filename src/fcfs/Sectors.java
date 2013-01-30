package fcfs;

/**
 * @author Noam Almog
 *
 */
import java.io.*;
import java.util.*;

public class Sectors {
	
	PrintStream io = System.out;
	//make smaller??
	Hashtable<String, SectorTree> sectorList = new Hashtable<String, SectorTree>();
	
	//Hashtable<String, SectorTree>() = ;
	//could make smaller <<
	
	 
	public void loadFromAces(String filePath){
		String[] subs = new String[1];
		try{
			//Read ACES Transit Time File Line by Line
			  FileInputStream fstream = new FileInputStream(filePath);
			  DataInputStream in = new DataInputStream(fstream);
			  BufferedReader br = new BufferedReader(new InputStreamReader(in));
			  String line;
			  //skip past comment line
			  br.readLine();
			  while ((line = br.readLine()) != null){
				  // sectorid(0), capcity(1),
				  subs = line.split(",");
				  if( subs.length == 2){ //&& !line.startsWith("*")){
					  //f = new SectorTree(subs[0], Integer.parseInt(subs[1]));  
					  sectorList.put(subs[0], new SectorTree(subs[0], Integer.parseInt(subs[1])));
				  } else {
					  io.println("not 7 " + line);
				  } 
			  }
			  in.close();

		}catch (Exception e){
			io.println(subs[0]);
			System.err.println("sector load Error: " + e.getMessage());
			e.printStackTrace();
		}

	}

	public void printSectors(){ 
		for (SectorTree s : sectorList.values()){
			//io.print(++i + " "); 
			s.print();
		}
	}
	
	public void printSectorMaxCaps(){ 
		for (SectorTree s : sectorList.values()){
			s.printMaxCapacity();
		}
	}
	
	public int getSoonestSlot(String sectorName, int enterTime, int exitTime){
		SectorTree s = sectorList.get(sectorName);
		if(s == null){
			s = new SectorTree(sectorName);
			sectorList.put(sectorName, s);
		}
		return s.getSoonestSlot(enterTime, exitTime);
	}
	
	public int schedule(String sectorName, int enterTime, int exitTime){
		SectorTree s = sectorList.get(sectorName);
		if(s == null){
			s = new SectorTree(sectorName);
			sectorList.put(sectorName, s);
		}
		return s.insertAtSoonestSlot(enterTime, exitTime);
	}
	

	
	/*
	class Sector{
		String name;
		int maxCapacity = -1;
		Sector(){
		}
		Sector(String n, int max){
			name = n; maxCapacity = max;
		}
		void print(){ 
			io.printf("ID: %s : %d", name, maxCapacity);
			io.println();
		}
	}//*/
	
}
