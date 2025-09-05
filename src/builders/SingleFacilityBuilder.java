package builders;

import disease.Disease;
import disease.FacilityOutbreak;
import disease.PersonDisease;
import processes.Admission;
import repast.simphony.context.Context;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.parameter.Parameters;
import utils.TimeUtils;
import agentcontainers.Facility;
import agentcontainers.Region;
import agents.Person;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

// Notes for TODO items:
//find output number of clinicical detections to add to the batch outputs (raw number)
//+ mean Daily Prevalence (patients with infection / total patients) sampled once per day
//+ mean discharge prevalence ( patients infected at discharge / total patients discharged
//+ importation prevalence(patients importing at admission / total patients admitted)
//these all go in 
// Do several batches with days betweeen and DoActiveSurveillanceAfterBurnIn

public class SingleFacilityBuilder implements ContextBuilder<Object> {
	private ISchedule schedule;
	private double isolationEffectiveness;
	private boolean doActiveSurveillance = false;
	private boolean doActiveSurveillanceAfterBurnIn = true;
	private double daysBetweenTests = 14.0;
	private PrintWriter facilityPrevalenceData;
	private PrintWriter R0Data;
	private Region region;
	private double burnInTime = 10 * 365.0;
	private double postBurnInTime = 5 * 365.0;
	private double totalTime = burnInTime + postBurnInTime;
	public Facility facility;
	private boolean stop = false;
	private Parameters params;
	private List<Double> dailyPrevalenceSamples = new ArrayList<>();
	public ArrayList<String> dailyPrev = new ArrayList<String>();
	private PrintWriter simulationOutputFile;
	public static boolean isBatchRun;
	private PrintWriter dailyStatsWriter;
	private int sumDailyInfected = 0;
	private int sumDailyClinicalDetections = 0;

	@Override
	public Context<Object> build(Context<Object> context) {
		// System.out.println("Starting simulation build.");
		schedule = repast.simphony.engine.environment.RunEnvironment.getInstance().getCurrentSchedule();

		params = repast.simphony.engine.environment.RunEnvironment.getInstance().getParameters();
		isolationEffectiveness = params.getDouble("isolationEffectiveness");
		doActiveSurveillanceAfterBurnIn = params.getBoolean("doActiveSurveillanceAfterBurnIn");
		daysBetweenTests = params.getDouble("daysBetweenTests");
		isBatchRun = params.getBoolean("isBatchRun");

		facility = new Facility();
		this.region = new Region(facility);
		facility.setRegion(region);
		setupAgents();

		scheduleEvents();

		// Oct 4, 2024 WRR:Start admissions process
		Admission admit = new Admission(21.1199 / 75.0, facility);
		admit.start();

		// Oct 4, 2024 WRR: schedule annotated methods on this builder class.
		schedule.schedule(this);

		context.add(region);
		context.add(this);
		// Oct 4, 2024 WRR: return facility?
		if (!isBatchRun) {
			try {
				dailyStatsWriter = new PrintWriter("daily_stats.txt");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return context;
	}

	// Oct 4, 2024 WRR: Here's one possible implementation of regular repeating
	// events,
	// example, like the Region.dailyPopulationTally even that Damon has described
	// in the text file.

	@ScheduledMethod(start = 1.0, interval = 1)
	public void dailyEvents() {
		if (facility.getPopulationSize() != 0) {
			region.doPopulationTally();
			region.logDailyPopulationStats();
		}
		double dailyPrevalence = 0.0;
        int count = 0;
        for (Facility f : region.getFacilities()) {
            for (FacilityOutbreak outbreak : f.getOutbreaks()) {
                dailyPrevalence += outbreak.getPrevalence();
                count++;
            }
        }
        if (count > 0) {
            dailyPrevalenceSamples.add(dailyPrevalence / count);
        }
        
        int colonized = 0;
        for (Person p : facility.getCurrentPatients()) {
		if (p.personDiseases.get(0).isColonized()) {
			colonized++;
		}
	}
        // do the same for p.personDiseases.get(0).isDetected()
       int detected = 0;
       for (Person p : facility.getCurrentPatients()) {
	   
	   if (p.personDiseases.get(0).isDetected()) {
	   detected++;
       }
       }
       
       int isolated = 0;
       for (Person p : facility.getCurrentPatients()) {
	   if (p.isIsolated()) {
	       isolated++;
       }
       }
	 
        
        dailyPrev.add(facility.getPopulationSize() + "," + colonized  + "," + detected +"," + isolated + ",\n");
	}

	public void setupAgents() {
		// System.out.println("Setting up AGENTS");

		int numDiseases = 1;
		int[] diseaseList = { 1 }; // Generic disease type ID
		for (int i = 0; i < numDiseases; i++) {
			Disease disease = new Disease();
			disease.setSimIndex(i);
			disease.setType(diseaseList[i]);
			region.getDiseases().add(disease);
		}

		int[] facilitySize = { 75 };
		int[] facilityType = { 0 };
		double[] meanLOS = { 27.1199026 };

		for (int i = 0; i < region.getFacilities().size(); i++) {
			Facility f = region.getFacilities().get(i);
			f.setType(facilityType[i]);
			f.setAvgPopTarget(facilitySize[i]);
			f.setMeanLOS(meanLOS[i]);

			f.setBetaIsolationReduction(1 - isolationEffectiveness);
			f.setNewPatientAdmissionRate(facilitySize[i] / meanLOS[i]);

			if (doActiveSurveillance) {
				f.setTimeBetweenMidstaySurveillanceTests(daysBetweenTests);
			}

			for (Disease d : region.getDiseases()) {
				FacilityOutbreak fo = f.addOutbreaks(d);
				fo.setDisease(d);
				fo.setDiseaseName(d.getDiseaseName());
				fo.facility = f;
			}

			for (int j = 0; j < facilitySize[i]; j++) {
				region.addInitialFacilityPatient(f);
			}
			f.admitNewPatient(schedule);

		}
	}

	public void scheduleEvents() {
		// System.out.println("Scheduling events.");
		schedule.schedule(ScheduleParameters.createOneTime(burnInTime), this, "doEndBurnInPeriod");

		System.out.println("Scheduled burn-in end at tick: " + burnInTime);
		System.out.println("Scheduled simulation end at tick: " + totalTime);
	}

	public void printCurrentTick() {
		double currentTick = schedule.getTickCount();
		System.out.println("Current tick: " + currentTick);
	}

	public void scheduleSimulationEnd() {
		// Oct 4, 2024 WRR:this should be rolled into scheduleEvents(). The schedule is
		// an
		// event queuing system. It holds and sorts as many events as you give it.
		schedule.schedule(ScheduleParameters.createOneTime(totalTime), this, "doSimulationEnd");

	}

	public void doEndBurnInPeriod() {

		region.setInBurnInPeriod(false);
		region.startDailyPopulationTallyTimer();
		doActiveSurveillance = doActiveSurveillanceAfterBurnIn;
		if (doActiveSurveillance) {
			for (Facility f : region.getFacilities()) {
				f.setTimeBetweenMidstaySurveillanceTests(daysBetweenTests);
			}
		}

		scheduleSimulationEnd();
	}

	public int getClinicalDetections() {
		return PersonDisease.clinicalOutputNum;
	}
	
	public void writeDailyPrevToFile() {
	    try (PrintWriter writer = new PrintWriter(new FileWriter("daily_prevalence.txt"))) {
	        writer.println("TotalPatients,Colonized,Detected,Isolated");
	        for (String line : dailyPrev) {
	            writer.print(line);
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	public void doSimulationEnd() throws IOException {
	    System.out.println("Simulation ending at tick: " + schedule.getTickCount());
	    
	    if (!params.getBoolean("isBatchRun")) {
	        writeDailyPrevToFile();
	    }



		simulationOutputFile = new PrintWriter("simulation_results.txt");
		simulationOutputFile.println(
				"surveillance_after_burn_in, isolation_effectiveness, days_between_tests, clinical_detections, mean_daily_prevalence, mean_discharge_prevalence, importation_prevalence, number_of_transmissions, sum_daily_infected, sum_daily_clinical_detections"
		);
		simulationOutputFile.println(doActiveSurveillanceAfterBurnIn + "," + isolationEffectiveness + ","
				+ daysBetweenTests + "," + getClinicalDetections() + "," + getMeanDailyPrevalence() + "," + getMeanDischargePrevalence() + "," + getImportationPrevalence() + "," + getNumberOfTransmissions()
				+ "," + sumDailyInfected + "," + sumDailyClinicalDetections + "\n");
		simulationOutputFile.flush();
		simulationOutputFile.close();
		if (dailyStatsWriter != null) {
			dailyStatsWriter.close();
		}
		stop = true;
		System.out.println("Ending simulation at tick: " + schedule.getTickCount());


	    // writeSimulationResults();
	    region.finishSimulation();
	    // repast.simphony.engine.environment.RunEnvironment.getInstance().endAt(totalTime);
	    repast.simphony.engine.environment.RunEnvironment.getInstance().endRun();
	    System.out.println("Simulation ended.");

	}
	/*
	 * 
	 * private void writeSimulationResults() {
	 * System.out.println("Writing simulation results."); for (Facility f :
	 * region.getFacilities()) { facilityPrevalenceData.printf("%d %d %d",
	 * f.getOutbreaks().get(0).getNumColonizedNow(), f.getCurrentPopulationSize(),
	 * f.getOutbreaks().get(0).getTransmissionsTally());
	 * facilityPrevalenceData.println(); } R0Data.printf("%d",
	 * region.numTransmissionsFromInitialCase); R0Data.println(); }
	 */

	public int getNumberOfTransmissions() {
		int numberOfTransmissions = 0;
		for (Facility f : region.getFacilities()) {
			for (FacilityOutbreak outbreak : f.getOutbreaks()) {
				numberOfTransmissions += outbreak.getTransmissionsTally();
			}
		}
		return numberOfTransmissions;
	}

	public double getMeanDailyPrevalence() {
	    if (dailyPrevalenceSamples.isEmpty()) {
	    	return 0.0;
	    }
	    double sum = 0.0;
	    for (double val : dailyPrevalenceSamples) { sum += val;}
	    return sum / dailyPrevalenceSamples.size();
	}

    public double getMeanDischargePrevalence() {
        double totalDischargePrevalence = 0.0;
        int count = 0;
        for (Facility f : region.getFacilities()) {
            for (FacilityOutbreak outbreak : f.getOutbreaks()) {
                totalDischargePrevalence += outbreak.getAvgDischargePrevalence();
                count++;
            }
        }
        return count > 0 ? totalDischargePrevalence / count : 0.0;
    }

    public double getImportationPrevalence() {
        double totalImportationPrevalence = 0.0;
        int count = 0;
        for (Facility f : region.getFacilities()) {
            for (FacilityOutbreak outbreak : f.getOutbreaks()) {
                totalImportationPrevalence += outbreak.getImportationRate();
                count++;
            }
        }
        return count > 0 ? totalImportationPrevalence / count : 0.0;
    }

	public ISchedule getSchedule() {
		return schedule;
	}

	public void setSchedule(ISchedule schedule) {
		this.schedule = schedule;
	}

	public double getIsolationEffectiveness() {
		return isolationEffectiveness;
	}

	public void setIsolationEffectiveness(double isolationEffectiveness) {
		this.isolationEffectiveness = isolationEffectiveness;
	}

	public boolean isDoActiveSurveillance() {
		return doActiveSurveillance;
	}

	public void setDoActiveSurveillance(boolean doActiveSurveillance) {
		this.doActiveSurveillance = doActiveSurveillance;
	}

	public double getDaysBetweenTests() {
		return daysBetweenTests;
	}

	public void setDaysBetweenTests(double daysBetweenTests) {
		this.daysBetweenTests = daysBetweenTests;
	}

	public PrintWriter getFacilityPrevalenceData() {
		return facilityPrevalenceData;
	}

	public void setFacilityPrevalenceData(PrintWriter facilityPrevalenceData) {
		this.facilityPrevalenceData = facilityPrevalenceData;
	}

	public PrintWriter getR0Data() {
		return R0Data;
	}

	public void setR0Data(PrintWriter r0Data) {
		R0Data = r0Data;
	}

	public Region getRegion() {
		return region;
	}

	public void setRegion(Region region) {
		this.region = region;
	}

	public double getBurnInTime() {
		return burnInTime;
	}

	public void setBurnInTime(double burnInTime) {
		this.burnInTime = burnInTime;
	}

	public double getPostBurnInTime() {
		return postBurnInTime;
	}

	public void setPostBurnInTime(double postBurnInTime) {
		this.postBurnInTime = postBurnInTime;
	}

	public double getTotalTime() {
		return totalTime;
	}

	public void setTotalTime(double totalTime) {
		this.totalTime = totalTime;
	}

	public Facility getFacility() {
		return facility;
	}

	public void setFacility(Facility facility) {
		this.facility = facility;
	}

	public boolean isStop() {
		return stop;
	}

	public void setStop(boolean stop) {
		this.stop = stop;
	}

}
