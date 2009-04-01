/**
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */

package xc.mst.dao.service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import xc.mst.bo.log.Log;
import xc.mst.bo.provider.Format;
import xc.mst.bo.service.Service;
import xc.mst.constants.Constants;
import xc.mst.dao.DataException;
import xc.mst.dao.MySqlConnectionManager;
import xc.mst.dao.log.DefaultLogDAO;
import xc.mst.dao.log.LogDAO;
import xc.mst.dao.provider.DefaultFormatDAO;
import xc.mst.dao.provider.FormatDAO;
import xc.mst.utils.LogWriter;

public class DefaultServiceDAO extends ServiceDAO
{
	/**
	 * Data access object for getting formats
	 */
	private FormatDAO formatDao = new DefaultFormatDAO();

	/**
	 * Data access object for getting input formats for a service
	 */
	private ServiceInputFormatUtilDAO serviceInputFormatDAO = new DefaultServiceInputFormatUtilDAO();

	/**
	 * Data access object for getting output formats for a service
	 */
	private ServiceOutputFormatUtilDAO serviceOutputFormatDAO = new DefaultServiceOutputFormatUtilDAO();

	/**
	 * Data access object for managing general logs
	 */
	private LogDAO logDao = new DefaultLogDAO();

	/**
	 * The repository management log file name
	 */
	private static Log logObj = (new DefaultLogDAO()).getById(Constants.LOG_ID_SERVICE_MANAGEMENT);
	
	/**
	 * A PreparedStatement to get all services in the database
	 */
	private static PreparedStatement psGetAll = null;
	
	/**
	 * A PreparedStatement to get a service from the database by its ID
	 */
	private static PreparedStatement psGetById = null;

	/**
	 * A PreparedStatement to get a service from the database by its port
	 */
	private static PreparedStatement psGetByPort = null;

	/**
	 * A PreparedStatement to get a service from the database by its name
	 */
	private static PreparedStatement psGetByName = null;

	/**
	 * A PreparedStatement to insert a service into the database
	 */
	private static PreparedStatement psInsert = null;

	/**
	 * A PreparedStatement to update a service in the database
	 */
	private static PreparedStatement psUpdate = null;

	/**
	 * A PreparedStatement to delete a service from the database
	 */
	private static PreparedStatement psDelete = null;

	/**
	 * Lock to synchronize access to the get all PreparedStatement
	 */
	private static Object psGetAllLock = new Object();
	
	/**
	 * Lock to synchronize access to the get by ID PreparedStatement
	 */
	private static Object psGetByIdLock = new Object();

	/**
	 * Lock to synchronize access to the get by port PreparedStatement
	 */
	private static Object psGetByPortLock = new Object();

	/**
	 * Lock to synchronize access to the get by name PreparedStatement
	 */
	private static Object psGetByNameLock = new Object();

	/**
	 * Lock to synchronize access to the insert PreparedStatement
	 */
	private static Object psInsertLock = new Object();

	/**
	 * Lock to synchronize access to the update PreparedStatement
	 */
	private static Object psUpdateLock = new Object();

	/**
	 * Lock to synchronize access to the delete PreparedStatement
	 */
	private static Object psDeleteLock = new Object();

	@Override
	public ArrayList<Service> getAll()
	{
		synchronized(psGetAllLock)
		{
			if(log.isDebugEnabled())
				log.debug("Getting all services");

			// The ResultSet from the SQL query
			ResultSet results = null;

			// The list of all services
			ArrayList<Service> services = new ArrayList<Service>();

			try
			{
				// Create the PreparedStatment to get all services if it hasn't already been created
				if(psGetAll == null)
				{
					// SQL to get the rows
					String selectSql = "SELECT " + COL_SERVICE_ID + ", " +
												   COL_SERVICE_NAME + ", " +
												   COL_PACKAGE_NAME + ", " +
												   COL_CLASS_NAME + ", " +
												   COL_IS_USER_DEFINED + ", " +
												   COL_PORT + ", " +
												   COL_WARNINGS + ", " +
												   COL_ERRORS + ", " +
												   COL_INPUT_RECORD_COUNT + ", " +
												   COL_OUTPUT_RECORD_COUNT + ", " +
												   COL_LAST_LOG_RESET + ", " +
												   COL_LOG_FILE_NAME + ", " +
												   COL_HARVEST_OUT_WARNINGS + ", " +
												   COL_HARVEST_OUT_ERRORS + ", " +
												   COL_HARVEST_OUT_RECORDS_AVAILABLE + ", " +
												   COL_HARVEST_OUT_RECORDS_HARVESTED + ", " +
												   COL_HARVEST_OUT_LAST_LOG_RESET + ", " +
												   COL_HARVEST_OUT_LOG_FILE_NAME + " " +
								       "FROM " + SERVICES_TABLE_NAME;

					if(log.isDebugEnabled())
						log.debug("Creating the \"get all services\" PreparedStatement from the SQL " + selectSql);

					// A prepared statement to run the select SQL
					// This should sanitize the SQL and prevent SQL injection
					psGetAll = dbConnection.prepareStatement(selectSql);
				} // end if(get all PreparedStatement not defined)

				// Get the result of the SELECT statement

				// Execute the query
				results = psGetAll.executeQuery();

				// For each result returned, add a Service object to the list with the returned data
				while(results.next())
				{
					// The Object which will contain data on the service
					Service service = new Service();

					// Set the fields on the service
					service.setId(results.getInt(1));
					service.setName(results.getString(2));
					service.setPackageName(results.getString(3));
					service.setClassName(results.getString(4));
					service.setIsUserDefined(results.getBoolean(5));
					service.setPort(results.getInt(6));
					service.setServicesWarnings(results.getInt(7));
					service.setServicesErrors(results.getInt(8));
					service.setInputRecordCount(results.getInt(9));
					service.setOutputRecordCount(results.getInt(10));
					service.setServicesLastLogReset(results.getDate(11));
					service.setServicesLogFileName(results.getString(12));
					service.setHarvestOutWarnings(results.getInt(13));
					service.setHarvestOutErrors(results.getInt(14));
					service.setHarvestOutRecordsAvailable(results.getLong(15));
					service.setHarvestOutRecordsHarvested(results.getLong(16));
					service.setHarvestOutLastLogReset(results.getDate(17));
					service.setHarvestOutLogFileName(results.getString(18));

					for(Integer inputFormatId : serviceInputFormatDAO.getInputFormatsForService(service.getId()))
						service.addInputFormat(formatDao.getById(inputFormatId));

					for(Integer outputFormatId : serviceOutputFormatDAO.getOutputFormatsForService(service.getId()))
						service.addOutputFormat(formatDao.getById(outputFormatId));

					// Add the service to the list
					services.add(service);
				} // end loop over results

				if(log.isDebugEnabled())
					log.debug("Found " + services.size() + " services in the database.");

				return services;
			} // end try(get services)
			catch(SQLException e)
			{
				log.error("A SQLException occurred while getting the services.", e);

				return services;
			} // end catch(SQLException)
			finally
			{
				MySqlConnectionManager.closeResultSet(results);
			} // end finally(close ResultSet)
		} // end synchronized
	} // end method getAll()

    /**
     * returns a sorted list of services
     * @param asc determines whether the rows are sorted in ascending or descending order
     * @param columnSorted the coulmn on which rows are sorted
     * @return list of services
     */
	@Override
	public List<Service> getSorted(boolean asc,String columnSorted)
	{
		if(log.isDebugEnabled())
			log.debug("Getting all services sorted in " + (asc ? "ascending" : "descending") + " order by " + columnSorted);

		// Validate the column we're trying to sort on
		if(!sortableColumns.contains(columnSorted))
		{
			log.error("An attempt was made to sort on the invalid column " + columnSorted);
			return getAll();
		} // end if(sort column invalid)
		
		// The ResultSet from the SQL query
		ResultSet results = null;

		// The Statement for getting the rows
		Statement getSorted = null;
		
		// The list of all services
		ArrayList<Service> services = new ArrayList<Service>();

		try
		{			
			// SQL to get the rows
			String selectSql = "SELECT " + COL_SERVICE_ID + ", " +
										   COL_SERVICE_NAME + ", " +
										   COL_PACKAGE_NAME + ", " +
										   COL_CLASS_NAME + ", " +
										   COL_IS_USER_DEFINED + ", " +
										   COL_PORT + ", " +
										   COL_WARNINGS + ", " +
										   COL_ERRORS + ", " +
										   COL_INPUT_RECORD_COUNT + ", " +
										   COL_OUTPUT_RECORD_COUNT + ", " +
										   COL_LAST_LOG_RESET + ", " +
										   COL_LOG_FILE_NAME + ", " +
										   COL_HARVEST_OUT_WARNINGS + ", " +
										   COL_HARVEST_OUT_ERRORS + ", " +
										   COL_HARVEST_OUT_RECORDS_AVAILABLE + ", " +
										   COL_HARVEST_OUT_RECORDS_HARVESTED + ", " +
										   COL_HARVEST_OUT_LAST_LOG_RESET + ", " +
										   COL_HARVEST_OUT_LOG_FILE_NAME + " " +
						       "FROM " + SERVICES_TABLE_NAME + " " +
						       "ORDER BY " + columnSorted + (asc ? " ASC" : " DESC");
	
			if(log.isDebugEnabled())
				log.debug("Creating the \"get all services\" PreparedStatement from the SQL " + selectSql);

			// A prepared statement to run the select SQL
			// This should sanitize the SQL and prevent SQL injection
			getSorted = dbConnection.createStatement();
			
			// Get the results of the SELECT statement			
			
			// Execute the query
			results = getSorted.executeQuery(selectSql);

			// For each result returned, add a Service object to the list with the returned data
			while(results.next())
			{
				// The Object which will contain data on the service
				Service service = new Service();

				// Set the fields on the service
				service.setId(results.getInt(1));
				service.setName(results.getString(2));
				service.setPackageName(results.getString(3));
				service.setClassName(results.getString(4));
				service.setIsUserDefined(results.getBoolean(5));
				service.setPort(results.getInt(6));
				service.setServicesWarnings(results.getInt(7));
				service.setServicesErrors(results.getInt(8));
				service.setInputRecordCount(results.getInt(9));
				service.setOutputRecordCount(results.getInt(10));
				service.setServicesLastLogReset(results.getDate(11));
				service.setServicesLogFileName(results.getString(12));
				service.setHarvestOutWarnings(results.getInt(13));
				service.setHarvestOutErrors(results.getInt(14));
				service.setHarvestOutRecordsAvailable(results.getLong(15));
				service.setHarvestOutRecordsHarvested(results.getLong(16));
				service.setHarvestOutLastLogReset(results.getDate(17));
				service.setHarvestOutLogFileName(results.getString(18));

				for(Integer inputFormatId : serviceInputFormatDAO.getInputFormatsForService(service.getId()))
					service.addInputFormat(formatDao.getById(inputFormatId));

				for(Integer outputFormatId : serviceOutputFormatDAO.getOutputFormatsForService(service.getId()))
					service.addOutputFormat(formatDao.getById(outputFormatId));

				// Add the service to the list
				services.add(service);
			} // end loop over results

			if(log.isDebugEnabled())
				log.debug("Found " + services.size() + " services in the database.");

			return services;
		} // end try(get services)
		catch(SQLException e)
		{
			log.error("A SQLException occurred while getting the services.", e);

			return services;
		} // end catch(SQLException)
		finally
		{
			MySqlConnectionManager.closeResultSet(results);
			
			try
			{
				getSorted.close();
			} // end try(close the Statement)
			catch(SQLException e)
			{
				log.error("An error occurred while trying to close the \"get processing directives sorted\" Statement");
			} // end catch(DataException)
		} // end finally(close ResultSet)
	} // end method getSortedByUserName(boolean)
	
	@Override
	public Service getById(int serviceId)
	{
		synchronized(psGetByIdLock)
		{
			if(log.isDebugEnabled())
				log.debug("Getting the service with ID " + serviceId);

			// The ResultSet from the SQL query
			ResultSet results = null;

			try
			{
				// Create the PreparedStatment to get a service by ID if it hasn't already been created
				if(psGetById == null)
				{
					// SQL to get the row
					String selectSql = "SELECT " + COL_SERVICE_ID + ", " +
				                                   COL_SERVICE_NAME + ", " +
				                                   COL_PACKAGE_NAME + ", " +
				                                   COL_CLASS_NAME + ", " +
				                                   COL_IS_USER_DEFINED + ", " +
				                                   COL_PORT + ", " +
												   COL_WARNINGS + ", " +
												   COL_ERRORS+ ", " +
												   COL_INPUT_RECORD_COUNT + ", " +
												   COL_OUTPUT_RECORD_COUNT + ", " +
												   COL_LAST_LOG_RESET + ", " +
												   COL_LOG_FILE_NAME + ", " +
												   COL_HARVEST_OUT_WARNINGS + ", " +
												   COL_HARVEST_OUT_ERRORS + ", " +
												   COL_HARVEST_OUT_RECORDS_AVAILABLE + ", " +
												   COL_HARVEST_OUT_RECORDS_HARVESTED + ", " +
												   COL_HARVEST_OUT_LAST_LOG_RESET + ", " +
												   COL_HARVEST_OUT_LOG_FILE_NAME + " " +
	                                   "FROM " + SERVICES_TABLE_NAME + " " +
	                                   "WHERE " + COL_SERVICE_ID + "=?";

					if(log.isDebugEnabled())
						log.debug("Creating the \"get service by ID\" PreparedStatement from the SQL " + selectSql);

					// A prepared statement to run the select SQL
					// This should sanitize the SQL and prevent SQL injection
					psGetById = dbConnection.prepareStatement(selectSql);
				} // end if(get by ID PreparedStatement not defined)

				// Set the parameters on the select statement
				psGetById.setInt(1, serviceId);

				// Get the result of the SELECT statement

				// Execute the query
				results = psGetById.executeQuery();

				// If any results were returned
				if(results.next())
				{
					// The Object which will contain data on the service
					Service service = new Service();

					// Set the fields on the service
					service.setId(results.getInt(1));
					service.setName(results.getString(2));
					service.setPackageName(results.getString(3));
					service.setClassName(results.getString(4));
					service.setIsUserDefined(results.getBoolean(5));
					service.setPort(results.getInt(6));
					service.setServicesWarnings(results.getInt(7));
					service.setServicesErrors(results.getInt(8));
					service.setInputRecordCount(results.getInt(9));
					service.setOutputRecordCount(results.getInt(10));
					service.setServicesLastLogReset(results.getDate(11));
					service.setServicesLogFileName(results.getString(12));
					service.setHarvestOutWarnings(results.getInt(13));
					service.setHarvestOutErrors(results.getInt(14));
					service.setHarvestOutRecordsAvailable(results.getLong(15));
					service.setHarvestOutRecordsHarvested(results.getLong(16));
					service.setHarvestOutLastLogReset(results.getDate(17));
					service.setHarvestOutLogFileName(results.getString(18));

					for(Integer inputFormatId : serviceInputFormatDAO.getInputFormatsForService(service.getId()))
						service.addInputFormat(formatDao.getById(inputFormatId));

					for(Integer outputFormatId : serviceOutputFormatDAO.getOutputFormatsForService(service.getId()))
						service.addOutputFormat(formatDao.getById(outputFormatId));

					if(log.isDebugEnabled())
						log.debug("Found the service with ID " + serviceId + " in the database.");

					// Return the service
					return service;
				} // end if(result found)

				if(log.isDebugEnabled())
					log.debug("The service with ID " + serviceId + " was not found in the database.");

				return null;
			} // end try(get the service)
			catch(SQLException e)
			{
				log.error("A SQLException occurred while getting the service with ID " + serviceId, e);

				return null;
			} // end catch(SQLException)
			finally
			{
				MySqlConnectionManager.closeResultSet(results);
			} // end finally(close ResultSet)
		} // end synchronized
	} // end method getById(int)

	@Override
	public Service loadBasicService(int serviceId)
	{
		synchronized(psGetByIdLock)
		{
			if(log.isDebugEnabled())
				log.debug("Getting the service with ID " + serviceId);

			// The ResultSet from the SQL query
			ResultSet results = null;

			try
			{
				// Create the PreparedStatment to get a service by ID if it hasn't already been created
				if(psGetById == null)
				{
					// SQL to get the row
					String selectSql = "SELECT " + COL_SERVICE_ID + ", " +
				                                   COL_SERVICE_NAME + ", " +
				                                   COL_PACKAGE_NAME + ", " +
				                                   COL_CLASS_NAME + ", " +
				                                   COL_IS_USER_DEFINED + ", " +
				                                   COL_PORT + ", " +
												   COL_WARNINGS + ", " +
												   COL_ERRORS+ ", " +
												   COL_INPUT_RECORD_COUNT + ", " +
												   COL_OUTPUT_RECORD_COUNT + ", " +
												   COL_LAST_LOG_RESET + ", " +
												   COL_LOG_FILE_NAME + ", " +
												   COL_HARVEST_OUT_WARNINGS + ", " +
												   COL_HARVEST_OUT_ERRORS + ", " +
												   COL_HARVEST_OUT_RECORDS_AVAILABLE + ", " +
												   COL_HARVEST_OUT_RECORDS_HARVESTED + ", " +
												   COL_HARVEST_OUT_LAST_LOG_RESET + ", " +
												   COL_HARVEST_OUT_LOG_FILE_NAME + " " +
	                                   "FROM " + SERVICES_TABLE_NAME + " " +
	                                   "WHERE " + COL_SERVICE_ID + "=?";

					if(log.isDebugEnabled())
						log.debug("Creating the \"get service by ID\" PreparedStatement from the SQL " + selectSql);

					// A prepared statement to run the select SQL
					// This should sanitize the SQL and prevent SQL injection
					psGetById = dbConnection.prepareStatement(selectSql);
				} // end if(get by ID PreparedStatement not defined)

				// Set the parameters on the update statement
				psGetById.setInt(1, serviceId);

				// Get the result of the SELECT statement

				// Execute the query
				results = psGetById.executeQuery();

				// If any results were returned
				if(results.next())
				{
					// The Object which will contain data on the service
					Service service = new Service();

					// Set the fields on the service
					service.setId(results.getInt(1));
					service.setName(results.getString(2));
					service.setPackageName(results.getString(3));
					service.setClassName(results.getString(4));
					service.setIsUserDefined(results.getBoolean(5));
					service.setPort(results.getInt(6));
					service.setServicesWarnings(results.getInt(7));
					service.setServicesErrors(results.getInt(8));
					service.setInputRecordCount(results.getInt(9));
					service.setOutputRecordCount(results.getInt(10));
					service.setServicesLastLogReset(results.getDate(11));
					service.setServicesLogFileName(results.getString(12));
					service.setHarvestOutWarnings(results.getInt(13));
					service.setHarvestOutErrors(results.getInt(14));
					service.setHarvestOutRecordsAvailable(results.getLong(15));
					service.setHarvestOutRecordsHarvested(results.getLong(16));
					service.setHarvestOutLastLogReset(results.getDate(17));
					service.setHarvestOutLogFileName(results.getString(18));

					if(log.isDebugEnabled())
						log.debug("Found the service with ID " + serviceId + " in the database.");

					// Return the service
					return service;
				} // end if(result found)

				if(log.isDebugEnabled())
					log.debug("The service with ID " + serviceId + " was not found in the database.");

				return null;
			} // end try(get the service)
			catch(SQLException e)
			{
				log.error("A SQLException occurred while getting the service with ID " + serviceId, e);

				return null;
			} // end catch(SQLException)
			finally
			{
				MySqlConnectionManager.closeResultSet(results);
			} // end finally(close ResultSet)
		} // end synchronized
	} // end method loadBasicService(int)

	@Override
	public Service getByPort(int port)
	{
		synchronized(psGetByPortLock)
		{
			if(log.isDebugEnabled())
				log.debug("Getting the service with port " + port);

			// The ResultSet from the SQL query
			ResultSet results = null;

			try
			{
				// Create the PreparedStatment to get a service by port if it hasn't already been created
				if(psGetByPort == null)
				{
					// SQL to get the row
					String selectSql = "SELECT " + COL_SERVICE_ID + ", " +
				                                   COL_SERVICE_NAME + ", " +
				                                   COL_PACKAGE_NAME + ", " +
				                                   COL_CLASS_NAME + ", " +
				                                   COL_IS_USER_DEFINED + ", " +
				                                   COL_PORT + ", " +
												   COL_WARNINGS + ", " +
												   COL_ERRORS+ ", " +
												   COL_INPUT_RECORD_COUNT + ", " +
												   COL_OUTPUT_RECORD_COUNT + ", " +
												   COL_LAST_LOG_RESET + ", " +
												   COL_LOG_FILE_NAME + ", " +
												   COL_HARVEST_OUT_WARNINGS + ", " +
												   COL_HARVEST_OUT_ERRORS + ", " +
												   COL_HARVEST_OUT_RECORDS_AVAILABLE + ", " +
												   COL_HARVEST_OUT_RECORDS_HARVESTED + ", " +
												   COL_HARVEST_OUT_LAST_LOG_RESET + ", " +
												   COL_HARVEST_OUT_LOG_FILE_NAME + " " +
	                                   "FROM " + SERVICES_TABLE_NAME + " " +
	                                   "WHERE " + COL_PORT + "=?";

					if(log.isDebugEnabled())
						log.debug("Creating the \"get service by port\" PreparedStatement from the SQL " + selectSql);

					// A prepared statement to run the select SQL
					// This should sanitize the SQL and prevent SQL injection
					psGetByPort = dbConnection.prepareStatement(selectSql);
				} // end if(get by port PreparedStatement not defined)

				// Set the parameters on the select statement
				psGetByPort.setInt(1, port);

				// Get the result of the SELECT statement

				// Execute the query
				results = psGetByPort.executeQuery();

				// If any results were returned
				if(results.next())
				{
					// The Object which will contain data on the service
					Service service = new Service();

					// Set the fields on the service
					service.setId(results.getInt(1));
					service.setName(results.getString(2));
					service.setPackageName(results.getString(3));
					service.setClassName(results.getString(4));
					service.setIsUserDefined(results.getBoolean(5));
					service.setPort(results.getInt(6));
					service.setServicesWarnings(results.getInt(7));
					service.setServicesErrors(results.getInt(8));
					service.setInputRecordCount(results.getInt(9));
					service.setOutputRecordCount(results.getInt(10));
					service.setServicesLastLogReset(results.getDate(11));
					service.setServicesLogFileName(results.getString(12));
					service.setHarvestOutWarnings(results.getInt(13));
					service.setHarvestOutErrors(results.getInt(14));
					service.setHarvestOutRecordsAvailable(results.getLong(15));
					service.setHarvestOutRecordsHarvested(results.getLong(16));
					service.setHarvestOutLastLogReset(results.getDate(17));
					service.setHarvestOutLogFileName(results.getString(18));

					for(Integer inputFormatId : serviceInputFormatDAO.getInputFormatsForService(service.getId()))
						service.addInputFormat(formatDao.getById(inputFormatId));

					for(Integer outputFormatId : serviceOutputFormatDAO.getOutputFormatsForService(service.getId()))
						service.addOutputFormat(formatDao.getById(outputFormatId));

					if(log.isDebugEnabled())
						log.debug("Found the service with port " + port + " in the database.");

					// Return the service
					return service;
				} // end if(result found)

				if(log.isDebugEnabled())
					log.debug("The service with port " + port + " was not found in the database.");

				return null;
			} // end try(get the service)
			catch(SQLException e)
			{
				log.error("A SQLException occurred while getting the service with port " + port, e);

				return null;
			} // end catch(SQLException)
			finally
			{
				MySqlConnectionManager.closeResultSet(results);
			} // end finally(close ResultSet)
		} // end synchronized
	} // end method getByPort(int)

	@Override
	public Service getByServiceName(String name)
	{
		synchronized(psGetByNameLock)
		{
			if(log.isDebugEnabled())
				log.debug("Getting the service with the name " + name);

			// The ResultSet from the SQL query
			ResultSet results = null;

			try
			{
				// Create the PreparedStatment to get a service by name if it hasn't already been created
				if(psGetByName == null)
				{
					// SQL to get the row
					String selectSql = "SELECT " + COL_SERVICE_ID + ", " +
				                                   COL_SERVICE_NAME + ", " +
				                                   COL_PACKAGE_NAME + ", " +
				                                   COL_CLASS_NAME + ", " +
				                                   COL_IS_USER_DEFINED + ", " +
				                                   COL_PORT + ", " +
												   COL_WARNINGS + ", " +
												   COL_ERRORS+ ", " +
												   COL_INPUT_RECORD_COUNT + ", " +
												   COL_OUTPUT_RECORD_COUNT + ", " +
												   COL_LAST_LOG_RESET + ", " +
												   COL_LOG_FILE_NAME + ", " +
												   COL_HARVEST_OUT_WARNINGS + ", " +
												   COL_HARVEST_OUT_ERRORS + ", " +
												   COL_HARVEST_OUT_RECORDS_AVAILABLE + ", " +
												   COL_HARVEST_OUT_RECORDS_HARVESTED + ", " +
												   COL_HARVEST_OUT_LAST_LOG_RESET + ", " +
												   COL_HARVEST_OUT_LOG_FILE_NAME + " " +
	                                   "FROM " + SERVICES_TABLE_NAME + " " +
	                                   "WHERE " + COL_SERVICE_NAME + "=?";

					if(log.isDebugEnabled())
						log.debug("Creating the \"get service by name\" PreparedStatement from the SQL " + selectSql);

					// A prepared statement to run the select SQL
					// This should sanitize the SQL and prevent SQL injection
					psGetByName = dbConnection.prepareStatement(selectSql);
				} // end if(get by name PreparedStatement not defined)

				// Set the parameters on the select statement
				psGetByName.setString(1, name);

				// Get the result of the SELECT statement

				// Execute the query
				results = psGetByName.executeQuery();

				// If any results were returned
				if(results.next())
				{
					// The Object which will contain data on the service
					Service service = new Service();

					// Set the fields on the service
					service.setId(results.getInt(1));
					service.setName(results.getString(2));
					service.setPackageName(results.getString(3));
					service.setClassName(results.getString(4));
					service.setIsUserDefined(results.getBoolean(5));
					service.setPort(results.getInt(6));
					service.setServicesWarnings(results.getInt(7));
					service.setServicesErrors(results.getInt(8));
					service.setInputRecordCount(results.getInt(9));
					service.setOutputRecordCount(results.getInt(10));
					service.setServicesLastLogReset(results.getDate(11));
					service.setServicesLogFileName(results.getString(12));
					service.setHarvestOutWarnings(results.getInt(13));
					service.setHarvestOutErrors(results.getInt(14));
					service.setHarvestOutRecordsAvailable(results.getLong(15));
					service.setHarvestOutRecordsHarvested(results.getLong(16));
					service.setHarvestOutLastLogReset(results.getDate(17));
					service.setHarvestOutLogFileName(results.getString(18));

					for(Integer inputFormatId : serviceInputFormatDAO.getInputFormatsForService(service.getId()))
						service.addInputFormat(formatDao.getById(inputFormatId));

					for(Integer outputFormatId : serviceOutputFormatDAO.getOutputFormatsForService(service.getId()))
						service.addOutputFormat(formatDao.getById(outputFormatId));

					if(log.isDebugEnabled())
						log.debug("Found the service with the name " + name + " in the database.");

					// Return the service
					return service;
				} // end if(result found)

				if(log.isDebugEnabled())
					log.debug("The service with the name " + name + " was not found in the database.");

				return null;
			} // end try(get the service)
			catch(SQLException e)
			{
				log.error("A SQLException occurred while getting the service with the name " + name, e);

				return null;
			} // end catch(SQLException)
			finally
			{
				MySqlConnectionManager.closeResultSet(results);
			} // end finally(close ResultSet)
		} // end synchronized
	} // end method getByServiceName(String)

	@Override
	public boolean insert(Service service) throws DataException
	{
		// Check that the non-ID fields on the service are valid
		validateFields(service, false, true);

		synchronized(psInsertLock)
		{
			if(log.isDebugEnabled())
				log.debug("Inserting a new service with the name " + service.getName());

			// The result set returned by the query
			ResultSet rs = null;

			try
			{
				// Build the PreparedStatement to insert a service if it wasn't already created
				if(psInsert == null)
				{
					// SQL to insert the new row
					String insertSql = "INSERT INTO " + SERVICES_TABLE_NAME + " (" + COL_SERVICE_NAME + ", " +
	            	      													COL_PACKAGE_NAME + ", " +
	            	      													COL_CLASS_NAME + ", " +
	            	      													COL_IS_USER_DEFINED + ", " +
	            	      													COL_PORT + ", " +
	            	      													COL_WARNINGS + ", " +
	            	      													COL_ERRORS + ", " +
	            	      													COL_INPUT_RECORD_COUNT + ", " +
	            	      													COL_OUTPUT_RECORD_COUNT + ", " +
	            	      													COL_LAST_LOG_RESET + ", " +
	            	      													COL_LOG_FILE_NAME + ", " +
	            	      													COL_HARVEST_OUT_WARNINGS + ", " +
	            	      													COL_HARVEST_OUT_ERRORS + ", " +
	            	      													COL_HARVEST_OUT_RECORDS_AVAILABLE + ", " +
	            	      													COL_HARVEST_OUT_RECORDS_HARVESTED + ", " +
	            	      													COL_HARVEST_OUT_LAST_LOG_RESET + ", " +
	            	      													COL_HARVEST_OUT_LOG_FILE_NAME + ") " +
	            				       "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

					if(log.isDebugEnabled())
						log.debug("Creating the \"insert service\" PreparedStatemnt from the SQL " + insertSql);

					// A prepared statement to run the insert SQL
					// This should sanitize the SQL and prevent SQL injection
					psInsert = dbConnection.prepareStatement(insertSql);
				} // end if(insert PreparedStatement not defined)

				// Set the parameters on the insert statement
				psInsert.setString(1, service.getName());
				psInsert.setString(2, service.getPackageName());
				psInsert.setString(3, service.getClassName());
				psInsert.setBoolean(4, service.getIsUserDefined());
				psInsert.setInt(5, service.getPort());
				psInsert.setInt(6, service.getServicesWarnings());
				psInsert.setInt(7, service.getServicesErrors());
				psInsert.setInt(8, service.getInputRecordCount());
				psInsert.setInt(9, service.getOutputRecordCount());
				psInsert.setDate(10, service.getServicesLastLogReset());
				psInsert.setString(11, service.getServicesLogFileName());
				psInsert.setInt(12, service.getHarvestOutWarnings());
				psInsert.setInt(13, service.getHarvestOutErrors());
				psInsert.setLong(14, service.getHarvestOutRecordsAvailable());
				psInsert.setLong(15, service.getHarvestOutRecordsHarvested());
				psInsert.setDate(16, service.getHarvestOutLastLogReset());
				psInsert.setString(17, service.getHarvestOutLogFileName());

				// Execute the insert statement and return the result
				if(psInsert.executeUpdate() > 0)
				{
					// Get the auto-generated resource identifier ID and set it correctly on this Service Object
					rs = dbConnection.createStatement().executeQuery("SELECT LAST_INSERT_ID()");

				    if (rs.next())
				        service.setId(rs.getInt(1));

				    boolean success = true;

				    // Insert the input format assignments
				    for(Format inputFormat : service.getInputFormats())
				    	success = serviceInputFormatDAO.insert(service.getId(), inputFormat.getId()) && success;

				    // Insert the output format assignments
				    for(Format outputFormat : service.getOutputFormats())
				    	success = serviceOutputFormatDAO.insert(service.getId(), outputFormat.getId()) && success;

				    if(success)
				    	LogWriter.addInfo(logObj.getLogFileLocation(), "Added a new service with the name " + service.getName());
				    else
				    {
				    	LogWriter.addWarning(logObj.getLogFileLocation(), "Added a new service with the name " + service.getName() + ", but failed to mark which formats it inputs and outputs");
				    	
				    	logObj.setWarnings(logObj.getWarnings() + 1);
				    	logDao.update(logObj);
				    }
				    
					return success;
				} // end if(insert succeeded)
				else
				{
					LogWriter.addError(logObj.getLogFileLocation(), "Failed to add a new service with the name " + service.getName());
					
					logObj.setErrors(logObj.getErrors() + 1);
			    	logDao.update(logObj);
			    	
					return false;
				}
			}
			catch(SQLException e)
			{
				log.error("A SQLException occurred while inserting a new service with the name " + service.getName(), e);

				LogWriter.addError(logObj.getLogFileLocation(), "Failed to add a new service with the name " + service.getName());
				
				logObj.setErrors(logObj.getErrors() + 1);
		    	logDao.update(logObj);
		    	
				return false;
			} // end catch(SQLException)
			finally
			{
				MySqlConnectionManager.closeResultSet(rs);
			} // end finally(close ResultSet)
		} // end synchronized
	} // end method insert(Service)

	@Override
	public boolean update(Service service) throws DataException
	{
		// Check that the fields on the service are valid
		validateFields(service, true, true);

		synchronized(psUpdateLock)
		{
			if(log.isDebugEnabled())
				log.debug("Updating the service with ID " + service.getId());

			try
			{
				// Create a PreparedStatement to update a service if it wasn't already created
				if(psUpdate == null)
				{
					// SQL to update new row
					String updateSql = "UPDATE " + SERVICES_TABLE_NAME + " SET " + COL_SERVICE_NAME + "=?, " +
				                                                          COL_PACKAGE_NAME + "=?, " +
				                                                          COL_CLASS_NAME + "=?, " +
				                                                          COL_IS_USER_DEFINED + "=?, " +
				                                                          COL_PORT + "=?, " +
				                                                          COL_WARNINGS + "=?, " +
				                                                          COL_ERRORS + "=?, " +
				                                                          COL_INPUT_RECORD_COUNT + "=?, " +
				                                                          COL_OUTPUT_RECORD_COUNT + "=?, " +
				                                                          COL_LAST_LOG_RESET + "=?, " +
				                                                          COL_LOG_FILE_NAME + "=?, " +
				                                                          COL_HARVEST_OUT_WARNINGS + "=?, " +
				                                                          COL_HARVEST_OUT_ERRORS + "=?, " +
				                                                          COL_HARVEST_OUT_RECORDS_AVAILABLE + "=?, " +
				                                                          COL_HARVEST_OUT_RECORDS_HARVESTED + "=?, " +
				                                                          COL_HARVEST_OUT_LAST_LOG_RESET + "=?, " +
				                                                          COL_HARVEST_OUT_LOG_FILE_NAME + "=? " +
	                                   "WHERE " + COL_SERVICE_ID + "=?";

					if(log.isDebugEnabled())
						log.debug("Creating the \"update service\" PreparedStatement from the SQL " + updateSql);

					// A prepared statement to run the update SQL
					// This should sanitize the SQL and prevent SQL injection
					psUpdate = dbConnection.prepareStatement(updateSql);
				} // end if(update PreparedStatement not defined)

				// Set the parameters on the update statement
				psUpdate.setString(1, service.getName());
				psUpdate.setString(2, service.getPackageName());
				psUpdate.setString(3, service.getClassName());
				psUpdate.setBoolean(4, service.getIsUserDefined());
				psUpdate.setInt(5, service.getPort());
				psUpdate.setInt(6, service.getServicesWarnings());
				psUpdate.setInt(7, service.getServicesErrors());
				psUpdate.setInt(8, service.getInputRecordCount());
				psUpdate.setInt(9, service.getOutputRecordCount());
				psUpdate.setDate(10, service.getServicesLastLogReset());
				psUpdate.setString(11, service.getServicesLogFileName());
				psUpdate.setInt(12, service.getHarvestOutWarnings());
				psUpdate.setInt(13, service.getHarvestOutErrors());
				psUpdate.setLong(14, service.getHarvestOutRecordsAvailable());
				psUpdate.setLong(15, service.getHarvestOutRecordsHarvested());
				psUpdate.setDate(16, service.getHarvestOutLastLogReset());
				psUpdate.setString(17, service.getHarvestOutLogFileName());
				psUpdate.setInt(18, service.getId());

				// Execute the update statement and return the result
				if(psUpdate.executeUpdate() > 0)
				{
					boolean success = true;

					// Delete the old input and output format assignments for the service
					serviceInputFormatDAO.deleteInputFormatsForService(service.getId());
					serviceOutputFormatDAO.deleteOutputFormatsForService(service.getId());

					// Insert the input format assignments
				    for(Format inputFormat : service.getInputFormats())
				    	success = serviceInputFormatDAO.insert(service.getId(), inputFormat.getId()) && success;

				    // Insert the output format assignments
				    for(Format outputFormat : service.getOutputFormats())
				    	success = serviceOutputFormatDAO.insert(service.getId(), outputFormat.getId()) && success;

				    if(success)
				    	LogWriter.addInfo(logObj.getLogFileLocation(), "Updated the service with the name " + service.getName());
				    else
				    {
				    	LogWriter.addWarning(logObj.getLogFileLocation(), "Updated the service with the name " + service.getName() + ", but failed to update the formats it inputs and outputs");
				    
				    	logObj.setWarnings(logObj.getWarnings() + 1);
				    	logDao.update(logObj);
				    }
				    
					return success;
				} // end if(the update succeeded)

				LogWriter.addError(logObj.getLogFileLocation(), "Failed to update the service with the name " + service.getName());
				
				logObj.setErrors(logObj.getErrors() + 1);
		    	logDao.update(logObj);
		    	
				// If we got here, the update failed
				return false;
			} // end try(update service)
			catch(SQLException e)
			{
				log.error("A SQLException occurred while updating the service with ID " + service.getId(), e);

				LogWriter.addError(logObj.getLogFileLocation(), "Failed to update the service with the name " + service.getName());
				
				logObj.setErrors(logObj.getErrors() + 1);
		    	logDao.update(logObj);
		    	
				return false;
			} // end catch(SQLException)
		} // end synchronized
	} // end method update(Service)

	@Override
	public boolean delete(Service service) throws DataException
	{
		// Check that the ID field on the service are valid
		validateFields(service, true, false);

		synchronized(psDeleteLock)
		{
			if(log.isDebugEnabled())
				log.debug("Deleting the service with ID " + service.getId());

			try
			{
				// Create the PreparedStatement to delete a service if it wasn't already defined
				if(psDelete == null)
				{
					// SQL to delete the row from the table
					String deleteSql = "DELETE FROM " + SERVICES_TABLE_NAME + " " +
		                               "WHERE " + COL_SERVICE_ID + " = ? ";

					if(log.isDebugEnabled())
						log.debug("Creating the \"delete service\" PreparedStatement the SQL " + deleteSql);

					// A prepared statement to run the delete SQL
					// This should sanitize the SQL and prevent SQL injection
					psDelete = dbConnection.prepareStatement(deleteSql);
				} // end if(delete PreparedStatement not defined)

				// Set the parameters on the delete statement
				psDelete.setInt(1, service.getId());

				// Execute the delete statement and return the result
				boolean success = psDelete.execute();
				
				if(success)
					LogWriter.addInfo(logObj.getLogFileLocation(), "Deleted the service with the name " + service.getName());
				else
		    	{
		    		LogWriter.addError(logObj.getLogFileLocation(), "Failed to delete the service with the name " + service.getName());
				
		    		logObj.setErrors(logObj.getErrors() + 1);
		    		logDao.update(logObj);
		    	}
		    	
				return success;
			} // end try(delete the service)
			catch(SQLException e)
			{
				log.error("A SQLException occurred while deleting the service with ID " + service.getId(), e);

				LogWriter.addError(logObj.getLogFileLocation(), "Failed to delete the service with the name " + service.getName());
				
				logObj.setErrors(logObj.getErrors() + 1);
		    	logDao.update(logObj);
		    	
				return false;
			} // end catch(SQLException)
		} // end synchronized
	} // end method delete(Service)
} // end class DefaultServiceDAO
