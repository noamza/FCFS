# First come first served aircraft scheduling in the national airspace

## About

FCFS is a collection of functions and data structures useful for scheduling aircraft arrivals departures in the national airspace. It also contains several classes that use these utilities to simulate different scheduling scenarios including scheduling in different orders (arrival/departure order), at different times (before/at departure), and with different uncertainties (taxi, gate) including monte-carlo simulation. The package is designed to take in unconstrained flight track data from NASA's Airspace Concept Evaluation System, ACES, (https://www.aviationsystemsdivision.arc.nasa.gov/research/modeling/aces.shtml) as input, as well as airport departure and arrival rate capacity constraints. For monte-carlo simulation delay uncertainty distribution data can be input as well. Using the input data, Flight objects are instantiated and then scheduled into arrival queues such that capacity constraints at the airport are not exceeded. 

A schedule of slots is represented as a list of Flight objects departing/arriving at a given airport. Capacity constraints are represented as minimum time spacing slots in the list of departures/arrivals. The delay generated from scheduling is recorded in the Flight object. For the most part, the scheduling in this package is done on a first-come-first-served (fcfs) basis. Most of the time, a flight receives the closest slot at or after its proposed slot time, after which its schedule is not modified. Hence the name fcfs.

## PAPERS
The following papers are based off this code and the particular source code files in particular. Reading these papers can help in understanding associated schedulers.

*Arrival Delay Absorption using Extended Metering with Speed Control, A Nikoleris, Gano Chatterji, Noam Almog, Kee Palopo, 12th AIAA Aviation Technology, Integration, and Operations (ATIO) Conference, September 2012*
(SchedulerFCFS.java, FCFSFlexibleSpeed.java)

*Delay Sensitivity to Call For Release Scheduling Time, Kee Palopo, Gano Broto Chatterji, and Noam Almog, 12th AIAA Aviation Technology, Integration, and Operations (ATIO) Conference, September 2012*
(FCFSArrival.java)

*Wheels-off Time Uncertainty Impact on Benefits of Early Call For Release Scheduling, NASA/TMÑ2014Ð000000, Kee Palopo, Gano Chatterji, Noam Almog 2015*
(FCFSCoupledWUncertainty.java)

These papers referred to as 'Delay Absorption', 'Delay Sensitivity', and 'Coupled Scheduling TM' respectively in the documentation.

## Inputs

See Readme in input folder.
Note: it is important to ensure that the ASPM capacity times are synced to the ACES track data. There can be an offset between these times which can lead to unrealistic capacity constraints at certain times of day.

## FLIGHT DATA
Flight data is stored in the Flight.java object. This object contains many variables from different scenarios and not all of them are relevant to the types of scheduling desired. It is important to keep track of these variables and make sure that they are set as intended. In essence, departureTimeACES is the input time from ACES referred to as the proposed departure time, and departureTimeFinal is the actual time the flight should depart / departs according to the scheduler. The same is true for arrivals. The other variables can be used as is useful, but code needs to be written to manage them. 

## AIRPORTTREE
AirportTree.java is where departure and arrival queues are created. TreeSets hold a collection of flights representing the arrival/departure queues and methods 'schedule' slots for the flights by inserting them into the queue making sure there is enough time in between slots to meet capacity constraints. There is support for FCFS scheduling, priority scheduling, as well as 'jiggling' where flights are scheduled out of order but attempt to fill in gaps in the schedule and delay later previously scheduled slots if need be. The way delay is managed in these methods is not necessarily consistent and is custom to the scenarios when it was built, this aspect needs to be minded and should probably be changed. So far, slots are only allocated at or after their proposed time not before (no negative delay).

## BUILDING A SCHEDULER
There are utilities for doing departure/arrival/center/ (and to some extent) sector scheduling here. The order the flights are scheduled has a lot of impact. After the flights are loaded using Flights.load(), flights can start to be scheduled by adding them to the departure, sector, center, arrival queues as desired. Queues are aware of their own constraints, but less so of other queues' constraints. For scheduling multiple facilities, outside code needs to manage the delay generated by a given slot queue so that it doesn't interfere with a slot in a different queue. For example, if a flight is being scheduled in a departure queue and then a sector queue, delay from the sector must be handled so that if the delay is passed back to the flight on the ground, it's departure still meets capacity constraints. Most of the previous schedulers only did arrivals.
To create scheduling simulation, it is helpful to build a queue of events to simulate, which is how many of these scheduler/simulation-scenarios are built. For instance at proposed departure time, a flight's arrival is scheduled, this event is added to the event-queue, it then misses its departure due to taxi-uncertainty and another event is added to re-schedule the arrival at wheels off time. The event-queue executes all these events chronologically.

## VALIDATION
It is very important to make sure scheduling is done in a valid way. Checking that slots are spaced out to not exceed capacity constraints is straight forward. Checking that all the variables in the Flight object are sensical, for instance that gate-departure + ground delay + taxi delay = wheels off time can be more subtle. Also making sure that scheduling events happen in the order intended. Validation in the airport queues is pretty good, but validating the Flight objects depends much more on which variables are bing utilized and should be written accordingly.


## SOURCE
Here is a summary of the different classes.

### Data Structures 
Airports.java - This is the collection of AirportTree's also responsible for loading data.
AirportTree.java - Contains list of flights representing arrival slots, ensures these meet capacity constraints. Where the majority of scheduling arrival/departure logic happens.
AirportsInt.java - Older version of Airports/Airportree that is integer based instead of Flight object.
Flights.java - This is the collection of flights also responsible for loading data.
Flight.java - Holds variables representing flight. Make sure values are set in scheduling.
Sectors.java - Less developed
SectorTree.java - Maintains capacity constraints in sectors, used in earlier versions of scheduler.

### Schedulers
Scheduler.java - Scheduler interface, should be developed more.
SchedulerFCFS.java - First scheduler. Schedules departure, sector, and arrival in depth first search, on a first come first served based on departure time.
FCFSFlexibleSpeed.java Monte-carlo simulation of an arrival scheduler that utilizes speed adjustment to absorb delay. It simulates scheduling before and at call for release (gate departure).
FCFSArrival.java - Monte-carlo simulation  in which flights are scheduled in a variety of ways. By departure, arrival, and with jiggling (scheduled by departure but can modify queue to fill in the gaps to be more efficient).
FCFSCoupledWUncertainty.java - Monte-carlo simulation in which there is a mix of flights that are scheduled at departure (cfr flights) with flights that are scheduled in the air at a freeze horizon (non-cfr flights).
SchedulerExample.java - An example of how to use the data structures to construct a scheduler

### Misc
Main.java - Entry point to program
Stats.java - Calculates some simple stats, std, mean, min, max, etc
U.java - Utility class for file, directory names, global values and utility functions.

### Huu
Refer to Huu Huynh for these classes.
DepartureArrivalFCFS_basic.java
DepartureArrivalFCFS.java
CenterBoundaries.java
CenterBoundary.java
Centers.java
CenterTree.java

![](https://upload.wikimedia.org/wikipedia/commons/e/e5/NASA_logo.svg)

