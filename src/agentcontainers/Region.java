package agentcontainers;


import agents.Person;
import disease.Disease;
import disease.PersonDisease;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;


import java.util.ArrayList;


public class Region extends AgentContainer{
	private boolean stop = false;

	private boolean inBurnInPeriod = true;
	private int numImportations = 0;
	private boolean useSingleImportation = false;
	public int numTransmissionsFromInitialCase = 0;
	private double intra_event_time;
	private ISchedule schedule;

	private ArrayList<Facility> facilities = new ArrayList<Facility>();
	private ArrayList<Disease> diseases = new ArrayList<Disease>();
	private ArrayList<Person> people = new ArrayList<Person>();




	public Region(Facility f) {
	   super();
	   schedule = repast.simphony.engine.environment.RunEnvironment.getInstance().getCurrentSchedule();
	   if(!facilities.contains(f)&&facilities.size()<1) {
			facilities.add(f);
		}
	   
   }
   
   // make int
   public int dailyPopulationTally() {
       System.out.println("daily population: " + facilities.get(0).getCurrentPopulationSize());
		   	/*stop = false
			double currTime = schedule.getTickCount()
			double elapse = distro.sample()
			ScheduleParameters params = ScheduleParameters.createOneTime(currTime + elapse)
			nextAction = schedule.schedule(params, this, "doPopulationTally")
			*/
      return people.size();
   }


	public void doPopulationTally(){
		for(Facility f : facilities) {
			f.updatePopulationTally() ;
			System.out.println("currpop" + f.getCurrentPopulationSize());
		}
		//action.call()
		if(!stop) {
			dailyPopulationTally();
		}
		//stop=true;
	}
	public void remove_people(Person person) {
	    	System.out.println("removing person: " + person.hashCode());
	    	System.out.println("people size:" + people.size());
	    	
	    	
	    	
		if (people.contains(person)) {
		    people.remove(person);
		    System.out.println("discharging person: " + person.hashCode());
			if (person.getCurrentFacility() != null) {
				person.getCurrentFacility().dischargePatient(person); 
			}
			
		}
	}


    public void importToFacility(Facility f){

		Person p = add_people(f);
		p.setRegion(this);
		for(Disease d : diseases){
			PersonDisease pd = p.add_diseases();
			pd.setDisease(d);
			pd.setPerson(p);
			if(useSingleImportation){
				if(!inBurnInPeriod){
					if(++numImportations == 1){
						pd.colonize();
						pd.setInitialInfection(true);
					}
				}
			}
			else{
				if(uniform() < d.getImportationProb()) pd.colonize();
			}
		}
		f.admitPatient(p);
		if(!facilities.contains(f)&&facilities.size()<1) {
			facilities.add(f);
		}
		System.out.println("Adding facility");
		
	}


    public void addInitialFacilityPatient(Facility f){
		// Oct 4, 2024 WRR: This needs to be refactored to do non-Anylogic instantiation.
		Person p = add_people(f);
		p.setRegion(this);
		for(Disease d : diseases){
			PersonDisease pd = p.add_diseases();
			pd.setDisease(d);
			pd.setPerson(p);
			if(!useSingleImportation && uniform() < 0.456) pd.colonize();
		}
		f.admitInitialPatient(p);
		if(!facilities.contains(f)&&facilities.size()<1) {
			facilities.add(f);
		}
		// Oct 25, 2024 WRR: This should say "adding patient", right?
		System.out.println("Adding initial facility agent");
		System.out.println(facilities.get(0).getCurrentPatients().size());
	}

	public void startActiveSurveillance(){
		for(Facility f : facilities) f.startActiveSurveillance();
	}
	public double uniform() {
		return Math.random();
	}

	public Person add_people(Facility f) {
		Person newPerson = new Person(f);
		
		newPerson.setRegion(this);
		System.out.println("Add person " + newPerson.hashCode());
		people.add(newPerson);  
		return newPerson;
	}
	public Disease add_diseases() {
		
		Person person = people.get(people.size() - 1);
		Disease disease = diseases.get(0);
		
		for (int i=0;i<diseases.size()-1;i++) {
			//person.diseases.add(disease);
		}
		return disease;
	}


	public void startDailyPopulationTallyTimer() {
		stop = false;
		double currTime = schedule.getTickCount();
		ScheduleParameters params = ScheduleParameters.createOneTime(currTime, intra_event_time);
		schedule.schedule(params, this, "doPopulationTally");
}


	public void finishSimulation() {
			stop = true;
			System.out.println("Simulation has been finished and cleaned up.");
			schedule.setFinishing(true);
		
	}


	public void addDisease(Disease disease) {
		diseases.add(disease);
		// TODO Auto-generated method stub
		
	}


	public ArrayList<Disease> getDiseases() {
		// TODO Auto-generated method stub
		return diseases;
	}


	public void setInBurnInPeriod(boolean b) {
		inBurnInPeriod = b;
		
	}

	public boolean isStop() {
	    return stop;
	}

	public void setStop(boolean stop) {
	    this.stop = stop;
	}

	public int getNumImportations() {
	    return numImportations;
	}

	public void setNumImportations(int numImportations) {
	    this.numImportations = numImportations;
	}

	public boolean isUseSingleImportation() {
	    return useSingleImportation;
	}

	public void setUseSingleImportation(boolean useSingleImportation) {
	    this.useSingleImportation = useSingleImportation;
	}

	public int getNumTransmissionsFromInitialCase() {
	    return numTransmissionsFromInitialCase;
	}

	public void setNumTransmissionsFromInitialCase(int numTransmissionsFromInitialCase) {
	    this.numTransmissionsFromInitialCase = numTransmissionsFromInitialCase;
	}

	public double getIntra_event_time() {
	    return intra_event_time;
	}

	public void setIntra_event_time(double intra_event_time) {
	    this.intra_event_time = intra_event_time;
	}

	public ISchedule getSchedule() {
	    return schedule;
	}

	public void setSchedule(ISchedule schedule) {
	    this.schedule = schedule;
	}

	public ArrayList<Facility> getFacilities() {
	    return facilities;
	}

	public void setFacilities(ArrayList<Facility> facilities) {
	    this.facilities = facilities;
	}

	public ArrayList<Person> getPeople() {
	    return people;
	}

	public void setPeople(ArrayList<Person> people) {
	    this.people = people;
	}

	public boolean isInBurnInPeriod() {
	    return inBurnInPeriod;
	}

	public void setDiseases(ArrayList<Disease> diseases) {
	    this.diseases = diseases;
	}
   
}