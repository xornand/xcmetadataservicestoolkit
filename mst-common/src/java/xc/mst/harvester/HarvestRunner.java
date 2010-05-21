/**
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */

package xc.mst.harvester;

import java.util.Date;

import org.apache.log4j.Logger;

import xc.mst.bo.harvest.Harvest;
import xc.mst.bo.harvest.HarvestSchedule;
import xc.mst.bo.harvest.HarvestScheduleStep;
import xc.mst.bo.provider.Provider;
import xc.mst.constants.Constants;
import xc.mst.dao.DataException;
import xc.mst.dao.DatabaseConfigException;
import xc.mst.manager.BaseManager;
import xc.mst.utils.LogWriter;
import xc.mst.utils.MSTConfiguration;
import xc.mst.utils.TimingLogger;

/**
 * This class is an interface to the Harvester class which requires only the ID of the harvest schedule
 * step to run.
 *
 * @author ShreyanshV
 */
public class HarvestRunner extends BaseManager
{

	/**
	 * A reference to the logger which writes to the HarvestIn log file
	 */
	static Logger log = Logger.getLogger(Constants.LOGGER_HARVEST_IN);

	/**
	 * The metadataPrefix (format) to harvest
	 */
	private String metadataPrefix = "";

	/**
	 * The setSpec of the set to harvest
	 */
	private String setSpec = null;

	/**
	 * The OAI request's from parameter
	 */
	private Date from = null;

	/**
	 * The OAI request's until parameter
	 */
	private Date until = null;

	/**
	 * True to harvest all records, false to use the from and until parameters
	 */
	private boolean harvestAll = true;

	/**
	 * True to harvest all records if deleted records are not supported by the
	 * repository, false to use the from and until parameters.  If false the
	 * MST will also be unable to synch with the repository unless that repository
	 * supports deleted records
	 */
	private boolean harvestAllIfNoDeletedRecord = true;

	/**
	 * How long to wait for a response from the repository we're harvesting before
	 * declaring that the request timed out
	 */
	private int timeOutMilliseconds = 5 * 60 * 1000; // 5 minutes

	/**
	 * The provider to harvest
	 */
	private Provider provider = null;

	/**
	 * The URL of the provider to harvest
	 */
	private String baseURL = null;

	/**
	 * The harvest schedule to run
	 */
	private HarvestSchedule harvestSchedule = null;

	/**
	 * The harvest that is currently being run
	 */
	private Harvest currentHarvest = null;

	/**
	 * The request run by the harvester
	 */
	private String request = null;

	/**
	 * Constructs an XC_Harvester to run the passed harvest schedule step
	 *
	 * @param harvestScheduleId The ID of the harvest schedule to run
	 * @throws OAIErrorException If the OAI provider being harvested returned an OAI error
	 * @throws Hexception If a serious error occurred which prevented the harvest from being completed
	 * @throws DatabaseConfigException
	 */
	public void setScheduleId(int harvestScheduleId) throws OAIErrorException, Hexception, DatabaseConfigException
	{
		// Set the parameters for the harvest based on the harvest schedule step ID
		harvestSchedule = getHarvestScheduleDAO().getById(harvestScheduleId);
		provider = harvestSchedule.getProvider();
		baseURL = provider.getOaiProviderUrl();
	} // end constructor(int)

	public void runHarvest()
	{
		StringBuilder requests = new StringBuilder();
		try
		{

			log.info("Found "+ getHarvestScheduleStepDAO().getStepsForSchedule(harvestSchedule.getId()).size() + " steps.");
			
			harvestSchedule.setStatus(Constants.STATUS_SERVICE_RUNNING);
			getHarvestScheduleDAO().update(harvestSchedule, false);

			
			for(HarvestScheduleStep step : getHarvestScheduleStepDAO().getStepsForSchedule(harvestSchedule.getId()))
			{
				StringBuilder sb = new StringBuilder();
				sb.append("format:");
				if (step.getFormat() != null) {
					sb.append(step.getFormat());
				} else {
					sb.append("null");
				}
				sb.append(" set:");
				if (step.getSet() != null) {
					sb.append(step.getSet());
				} else {
					sb.append("null");
				}
				TimingLogger.log(sb.toString());
				Harvester.resetProcessedRecordCount();
				Harvester.resetTotalRecordCount();
				runHarvestStep(step);

				if(requests.length() == 0)
					requests.append(request);
				else
					requests.append("\n").append(request);
			}

			harvestSchedule = getHarvestScheduleDAO().getById(harvestSchedule.getId());
			
			harvestSchedule.setRequest(requests.toString());
			harvestSchedule.setStatus(Constants.STATUS_SERVICE_NOT_RUNNING);

			getHarvestScheduleDAO().update(harvestSchedule, false);

			// Set the current harvest's end time
			currentHarvest.setEndTime(new Date());
			getHarvestDAO().update(currentHarvest);

			// Set the provider's last harvest time
			provider = getProviderDAO().getById(provider.getId());
			provider.setLastHarvestEndTime(new Date());
			getProviderDAO().update(provider);

			LogWriter.addInfo(provider.getLogFileName(), "Finished harvest of " + baseURL);
		}
		catch (Hexception e) 
		{
			// If harvest is aborted it throws Hexception. So in catch block set harvest end time
			try
			{
				
				harvestSchedule = getHarvestScheduleDAO().getById(harvestSchedule.getId());
				
				harvestSchedule.setRequest(requests.toString());

				getHarvestScheduleDAO().update(harvestSchedule, false);
				
				// Set the current harvest's end time
				currentHarvest.setEndTime(new Date());
				getHarvestDAO().update(currentHarvest);

				// Set the provider's last harvest time
				provider = getProviderDAO().getById(provider.getId());
				provider.setLastHarvestEndTime(new Date());
				getProviderDAO().update(provider);

			}
			catch(DatabaseConfigException e2)
			{
				log.error("Unable to connect to the database with the parameters defined in the configuration file.", e2);
			}
			catch(DataException e2)
			{
				log.error("An error occurred while deleting the aborted harvest.", e2);
			}
			
			if(e.getMessage().contains("Harvest received kill signal"))
				log.info("Harvest Aborted!");
			else
				log.warn("Harvest failed.");
		}
		catch(DatabaseConfigException e)
		{
			log.error("Unable to connect to the database with the parameters defined in the configuration file.", e);
		}
		catch(DataException e)
		{
			log.error("An error occurred while updating the harvest schedule's request field.", e);
		}
		catch (Exception e) {

			log.error("An error occurred while harvesting", e);
		}
	}

	/**
	 * Runs the harvest
	 * @throws Hexception 
	 */
	private void runHarvestStep(HarvestScheduleStep harvestScheduleStep) throws Hexception, Exception
	{
		try
		{
			metadataPrefix = harvestScheduleStep.getFormat().getName();

			// If there was a set, set up the setSpec
			if(harvestScheduleStep.getSet() != null)
				setSpec = harvestScheduleStep.getSet().getSetSpec();

			// Set the from field to the time when we last harvested the provider
			if (harvestScheduleStep.getLastRan() != null) {
				from = new Date(harvestScheduleStep.getLastRan().getTime());
			}

			// Harvest all records if the from parameter was not provided
			if(from != null)
				harvestAll = false;

			// The time when we started the harvest
			Date startTime = new Date();

			// Setup the harvest we're currently running
			currentHarvest = new Harvest();
			currentHarvest.setStartTime(startTime);
			currentHarvest.setProvider(provider);
			currentHarvest.setHarvestSchedule(harvestScheduleStep.getSchedule());
			getHarvestDAO().insert(currentHarvest);

			String timeout = MSTConfiguration.getProperty(Constants.CONFIG_HARVESTER_TIMEOUT_URL);
			if(timeout != null)
			{
				try
				{
					timeOutMilliseconds = Integer.parseInt(timeout);
				}
				catch(NumberFormatException e)
				{
					log.warn("The HarvesterTimeout in the configuration file was not an integer.");
				}
			}
			
			
			// Run the harvest
			Harvester.harvest(
					 baseURL,
					 metadataPrefix,
					 setSpec,
					 from,
					 until,
					 harvestAll,
					 harvestAllIfNoDeletedRecord,
					 timeOutMilliseconds,
					 harvestScheduleStep,
					 currentHarvest);

			// Set the request used to run the harvest
			currentHarvest = getHarvestDAO().getById(currentHarvest.getId());
			request = currentHarvest.getRequest();

			// Set the harvest schedule step's last run date to the time when we started the harvest.
			harvestScheduleStep.setLastRan(startTime);
			getHarvestScheduleStepDAO().update(harvestScheduleStep, harvestScheduleStep.getSchedule().getId());
		} // end try(run the harvest)
		catch (Exception e) {

			log.error("An error occurred while harvesting " + baseURL, e);
			throw e;

		}
		// end catch(Exception)
	} // end method runHarvest()

	/**
	 * Gets the request used to start the harvest
	 *
	 * @return The OAI request used to start the harvest
	 */
	public String getRequest()
	{
		return request;
	}
	
	/**
	 * Logs the status of the harvest to the database
	 * @throws DataException
	 */
	protected void persistStatus(String status)
	{
		try {
			log.debug("status: "+status);
			currentHarvest.getHarvestSchedule().setStatus(status);
			getHarvestScheduleDAO().update(currentHarvest.getHarvestSchedule(), false);

		} catch (DataException e) {
			log.error("Error during updating status of harvest_schedule to database.");
		}
	}
} // end class HarvestRunner




