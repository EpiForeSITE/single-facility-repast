package agentcontainers;
import agents.Person;
import disease.FacilityOutbreak;
import disease.PersonDisease;
import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.random.RandomHelper;
import java.util.ArrayList;
import java.util.LinkedList;

public class Facility extends AgentContainer{


    private static final long serialVersionUID = -758171564017677907L;
	private int currentPopulationSize = 0;
	private double betaIsolationReduction;
	private double timeBetweenMidstaySurveillanceTests = -1.0;
	private boolean onActiveSurveillance = false;
	private int type;
	private Region region;
	private double newPatientAdmissionRate;
	private double avgPopTarget;
	private double meanLOS;
	private double avgPopulation;
	private int numDaysTallied = 0;
	private double patientDays;
	private int numAdmissions = 0;
	private double admissionSurveillanceAdherence = 0.911;
	private double midstaySurveillanceAdherence = 0.954;
	private ExponentialDistribution distro;
	private ISchedule schedule;
	private ISchedulableAction nextAction;
	private ArrayList<FacilityOutbreak> outbreaks = new ArrayList<>();
	private LinkedList<Person> currentPatients = new LinkedList<>();
	private boolean stop = false;
	private double meanIntraEventTime;
	private int capacity;
	private double isolationEffectiveness;
	
	// Constructor
	public Facility() {
		super();
		schedule = repast.simphony.engine.environment.RunEnvironment.getInstance().getCurrentSchedule();
	}

	public void admitNewPatient(ISchedule sched) {
		schedule = sched;
		Person newPatient = new Person(this);
		admitPatient(newPatient);
		System.out.println("New patient admitted. Current population: " + setCurrentPopulationSize(getCurrentPopulationSize() + 1));
	}
	
	public void admitPatient(Person p){
		p.admitToFacility(this);

		p.startDischargeTimer(getRandomLOS());

		

		for(PersonDisease pd : p.getDiseases()){
			if(pd.isColonized()){
				if(pd.getDisease().isActiveSurveillanceAgent() && onActiveSurveillance){
					if(uniform() < pd.getDisease().getProbSurveillanceDetection() * admissionSurveillanceAdherence){
						pd.setDetected(true);
						if(pd.getDisease().isolatePatientWhenDetected()) p.isolate();
					}
				}
				pd.startClinicalDetectionTimer();
			}
		}
		getCurrentPatients().add(p);
		getRegion().getPeople().add(p);

		if(onActiveSurveillance && !p.isIsolated() && getTimeBetweenMidstaySurveillanceTests() > 0)
			p.startNextPeriodicSurveillanceTimer();

		p.updateAllTransmissionRateContributions();

		if(!getRegion().isInBurnInPeriod()) updateAdmissionTally(p);
	}
	public void dischargePatient(Person p){
		setCurrentPopulationSize(getCurrentPopulationSize() - 1);
		getCurrentPatients().remove(p);
		updateTransmissionRate();
		// Oct 4, 2024 WRR: This isn't deleting the patient from anywhere but this currentPatients collection.
		if(!getRegion().isInBurnInPeriod()) updateStayTally(p);

		p.destroyMyself(getRegion());
	}

	public void updateTransmissionRate(){
		for(FacilityOutbreak fo : getOutbreaks()) fo.updateTransmissionRate();
	}

	public double getRandomLOS(){
		if(getType()==0){

			double shape1 = 7.6019666;
			double scale1 = 3.4195217;
			double shape2 = 1.2327910;
			double scale2 = 23.5214724;
			double prob1 = 0.6253084;

			if(uniform() < prob1) return gamma(shape1,scale1);
			else return gamma(shape2,scale2);
		}
		else{
			return -1.0;
		}
	}

	public void admitInitialPatient(Person p){
		p.admitToFacility(this);
		p.startDischargeTimer(exponential(1.0/getMeanLOS()));

		setCurrentPopulationSize(getCurrentPopulationSize() + 1);

		boolean doSurveillanceTest = false;
		if(onActiveSurveillance) doSurveillanceTest = true;

		for(PersonDisease pd : p.getDiseases()){
			if(pd.isColonized()){
				pd.startClinicalDetectionTimer();
			}
		}
		getCurrentPatients().add(p);

		p.updateAllTransmissionRateContributions();
		setCurrentPopulationSize(getCurrentPopulationSize() + 1);
	}

	public void updatePopulationTally(){
		avgPopulation = (avgPopulation * numDaysTallied + getCurrentPopulationSize()) / (numDaysTallied + 1);
		numDaysTallied++;

		for(FacilityOutbreak fo : getOutbreaks()) fo.updatePrevalenceTally();
	}

	public void updateStayTally(Person p){
		setPatientDays(getPatientDays() + p.getCurrentLOS());

		for(int i=0; i<getOutbreaks().size(); i++)
			getOutbreaks().get(i).updateStayTally(p.getDiseases().get(i));
	}

	public void updateAdmissionTally(Person p){
		setNumAdmissions(getNumAdmissions() + 1);

		for(int i=0; i<getOutbreaks().size(); i++)
			getOutbreaks().get(i).updateAdmissionTally(p.getDiseases().get(i));
	}

	public void startActiveSurveillance(){
		onActiveSurveillance = true;
	}
	public double uniform() {
		return Math.random();
	}
	public double gamma(double shape, double scale) {
		GammaDistribution gammaDistribution = new GammaDistribution(shape, scale);
		return gammaDistribution.sample();
	}
	public double exponential(double rate) {
		ExponentialDistribution exponentialDistribution = new ExponentialDistribution(rate);
		return exponentialDistribution.sample();
	}
	public FacilityOutbreak addOutbreaks() {
		FacilityOutbreak newOutbreak = new FacilityOutbreak(meanIntraEventTime);
		newOutbreak.setFacility(this);

		getOutbreaks().add(newOutbreak);

		return newOutbreak;
	}
	public int getType() {
		return type;
	}

	public void addOutbreak(FacilityOutbreak outbreak) {
		getOutbreaks().add(outbreak);
	}

	public int getCapacity() {
		return this.capacity;
	}

	public void setIsolationEffectiveness(double isolationEffectiveness) {
		this.isolationEffectiveness = isolationEffectiveness;
	}
	public int getPopulationSize() {
		return getCurrentPatients().size();
	}

	public double getTimeBetweenMidstaySurveillanceTests() {
	    return timeBetweenMidstaySurveillanceTests;
	}

	public void setTimeBetweenMidstaySurveillanceTests(double timeBetweenMidstaySurveillanceTests) {
	    this.timeBetweenMidstaySurveillanceTests = timeBetweenMidstaySurveillanceTests;
	}

	public double getMidstaySurveillanceAdherence() {
	    return midstaySurveillanceAdherence;
	}

	public void setMidstaySurveillanceAdherence(double midstaySurveillanceAdherence) {
	    this.midstaySurveillanceAdherence = midstaySurveillanceAdherence;
	}

	public void setType(int type) {
	    this.type = type;
	}

	public double getAvgPopTarget() {
	    return avgPopTarget;
	}

	public void setAvgPopTarget(double avgPopTarget) {
	    this.avgPopTarget = avgPopTarget;
	}

	public double getMeanLOS() {
	    return meanLOS;
	}

	public void setMeanLOS(double meanLOS) {
	    this.meanLOS = meanLOS;
	}

	public double getBetaIsolationReduction() {
	    return betaIsolationReduction;
	}

	public void setBetaIsolationReduction(double betaIsolationReduction) {
	    this.betaIsolationReduction = betaIsolationReduction;
	}

	public double getNewPatientAdmissionRate() {
	    return newPatientAdmissionRate;
	}

	public void setNewPatientAdmissionRate(double newPatientAdmissionRate) {
	    this.newPatientAdmissionRate = newPatientAdmissionRate;
	}

	public Region getRegion() {
	    return region;
	}

	public void setRegion(Region region) {
	    this.region = region;
	}

	public LinkedList<Person> getCurrentPatients() {
	    return currentPatients;
	}

	public void setCurrentPatients(LinkedList<Person> currentPatients) {
	    this.currentPatients = currentPatients;
	}

	public double getPatientDays() {
	    return patientDays;
	}

	public void setPatientDays(double patientDays) {
	    this.patientDays = patientDays;
	}

	public int getNumAdmissions() {
	    return numAdmissions;
	}

	public void setNumAdmissions(int numAdmissions) {
	    this.numAdmissions = numAdmissions;
	}

	public int getCurrentPopulationSize() {
	    return currentPopulationSize;
	}

	public int setCurrentPopulationSize(int currentPopulationSize) {
	    this.currentPopulationSize = currentPopulationSize;
	    return currentPopulationSize;
	}

	public ArrayList<FacilityOutbreak> getOutbreaks() {
	    return outbreaks;
	}

	public void setOutbreaks(ArrayList<FacilityOutbreak> outbreaks) {
	    this.outbreaks = outbreaks;
	}
}