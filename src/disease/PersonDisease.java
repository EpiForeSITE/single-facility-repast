package disease;


import agents.Person;
import builders.SingleFacilityBuilder;

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduledMethod;
import utils.TimeUtils;


public class PersonDisease {


	private Disease disease;
	private Person person;
	private boolean colonized = false;
	private boolean detected = false;
	private double transmissionRateContribution = 1.0;
	private boolean clinicallyDetectedDuringCurrentStay = false;
	private boolean initialInfection = false;


	private ISchedule schedule;
	private ExponentialDistribution decolonizationDistribution;
	private ExponentialDistribution clinicalDetectionDistribution;
	private static PrintWriter decolWriter;
	private static PrintWriter clinicalWriter;
	public static int clinicalOutputNum;

	static {
        try {
			if(!SingleFacilityBuilder.isBatchRun) {
				
            decolWriter = new PrintWriter("decolonization.txt");
            clinicalWriter = new PrintWriter("clinicalDetection.txt");
			}
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	public PersonDisease(Disease disease, Person person, ISchedule schedule) {
        this.disease = disease;
        this.person = person;
        this.schedule = repast.simphony.engine.environment.RunEnvironment.getInstance().getCurrentSchedule();
       
		initializeEventDistributions();
		double decolonizationRate = 1.0 / disease.getAvgDecolonizationTime();
		if (person.getCurrentFacility() == null) {
			System.err.println("Current facility is null for person: " + person);
			return;
		}
        double meanTimeToClinicalDetection = disease.getMeanTimeToClinicalDetection(person.getCurrentFacility().getType());


        decolonizationDistribution = new ExponentialDistribution(disease.getAvgDecolonizationTime());
        clinicalDetectionDistribution = new ExponentialDistribution(meanTimeToClinicalDetection);
    }

	public void doDecolonization() {
		double currentTime = schedule.getTickCount();
        
        
		if (colonized) {
			colonized = false;
			if(!SingleFacilityBuilder.isBatchRun) {
			decolWriter.printf("Time: %.2f, Decolonized Patient: %d%n", currentTime, person.hashCode());
			}
			resetClinicalDetectionEvent();
			person.updateAllTransmissionRateContributions();
		}
	}
	public boolean isColonized() {
		return colonized;
	}
	public void setPerson(Person person) {
		this.person = person;
	}
	public void setDisease(Disease disease) {
		this.disease = disease;
	}
	public void doClinicalDetection() {
		double currentTime = schedule.getTickCount();
		detected = true;
		clinicallyDetectedDuringCurrentStay = true;
		if(!SingleFacilityBuilder.isBatchRun) {
			clinicalWriter.printf("Time: %.2f, Detected Patient: %d%n", currentTime, person.hashCode());
		}
		clinicalOutputNum++;
		if (!person.isIsolated() && disease.isolatePatientWhenDetected()) {
			person.isolate();
			person.updateAllTransmissionRateContributions();
		}
	}


	public void colonize() {
		colonized = true;
		startDecolonizationTimer();
		person.updateAllTransmissionRateContributions();
		

	}
	public void startClinicalDetectionTimer() {
		double meanTimeToClinicalDetection = disease.getMeanTimeToClinicalDetection(person.getCurrentFacility().getType());
		double timeToDetection = clinicalDetectionDistribution.sample();

		ScheduleParameters params = ScheduleParameters.createOneTime(schedule.getTickCount() + timeToDetection);
		schedule.schedule(params, this, "doClinicalDetection");
	}
	public void updateTransmissionRateContribution(){
		double score = 1.0;
		if(person.isIsolated()) score *= person.getCurrentFacility().getBetaIsolationReduction();
		transmissionRateContribution = score;
	}


	public void startDecolonizationTimer() {
		if (decolonizationDistribution == null) {
			System.err.println("Decolonization distribution is not initialized.");
			return;
		}
			double decolonizationRate = 1.0 / disease.getAvgDecolonizationTime();
			double timeToDecolonization = decolonizationDistribution.sample();
			ScheduleParameters params = ScheduleParameters.createOneTime(schedule.getTickCount() + timeToDecolonization);
			schedule.schedule(params, this, "doDecolonization");
		
	}
	public void addAcquisition() {
		startClinicalDetectionTimer();
		person.updateAllTransmissionRateContributions();
	}


	public void initializeEventDistributions() {
		if (disease != null && person != null && person.getCurrentFacility() != null) {
			double decolonizationRate = 1.0 / disease.getAvgDecolonizationTime();
			double meanTimeToClinicalDetection = disease.getMeanTimeToClinicalDetection(person.getCurrentFacility().getType());

			decolonizationDistribution = new ExponentialDistribution(disease.getAvgDecolonizationTime());
			clinicalDetectionDistribution = new ExponentialDistribution(meanTimeToClinicalDetection);
		} else {
			System.err.println("Cannot initialize distributions: disease, person, or current facility is null.");
		}
	}
	public void resetClinicalDetectionEvent() {
		clinicallyDetectedDuringCurrentStay = false;
		if (clinicalDetectionDistribution != null) {
			double timeToDetection = clinicalDetectionDistribution.sample();
			ScheduleParameters params = ScheduleParameters.createOneTime(schedule.getTickCount() + timeToDetection);
			schedule.schedule(params, this, "doClinicalDetection");
		}
	}
	public double getTransmissionRateContribution() {
		return transmissionRateContribution;
	}
	public void setInitialInfection(boolean initialInfection) {
		this.initialInfection = initialInfection;
	}
	public Disease getDisease() {
		return disease;
	}
	public boolean isDetected() {
		return detected;
	}
	public void setDetected(boolean detected) {
		this.detected = detected;
	}
	public boolean isClinicallyDetectedDuringCurrentStay() {
		return clinicallyDetectedDuringCurrentStay;
	}
	public boolean isInitialInfection() {
		return initialInfection;
	}
}
