/**
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */

package xc.mst.manager.processingDirective;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import xc.mst.bo.processing.Job;
import xc.mst.bo.provider.Format;
import xc.mst.bo.record.Record;
import xc.mst.bo.service.ErrorCode;
import xc.mst.bo.service.Service;
import xc.mst.constants.Constants;
import xc.mst.dao.DataException;
import xc.mst.dao.DatabaseConfigException;
import xc.mst.dao.log.DefaultLogDAO;
import xc.mst.dao.log.LogDAO;
import xc.mst.dao.provider.DefaultFormatDAO;
import xc.mst.dao.provider.FormatDAO;
import xc.mst.dao.service.DefaultErrorCodeDAO;
import xc.mst.dao.service.DefaultServiceDAO;
import xc.mst.dao.service.ErrorCodeDAO;
import xc.mst.dao.service.ServiceDAO;
import xc.mst.manager.IndexException;
import xc.mst.manager.record.DefaultRecordService;
import xc.mst.manager.record.RecordService;
import xc.mst.services.MetadataService;
import xc.mst.utils.LogWriter;
import xc.mst.utils.MSTConfiguration;
import xc.mst.utils.index.RecordList;
import xc.mst.utils.index.SolrIndexManager;

/**
 * Provides implementation for service methods to interact with the services in the MST
 *
 * @author Tejaswi Haramurali
 */
public class DefaultServicesService implements ServicesService
{
	/**
	 * The logger object
	 */
	protected static Logger log = Logger.getLogger(Constants.LOGGER_GENERAL);

    /** DAO object for services in the MST */
    private ServiceDAO servicesDao;

    /**
     * DAO for log data in the MST
     */
    private static LogDAO logDao = new DefaultLogDAO();

    /**
     * DAO for format data in the MST
     */
    private static FormatDAO formatDao = new DefaultFormatDAO();

    /**
     * DAO for error codes in the MST
     */
    private static ErrorCodeDAO errorCodeDao = new DefaultErrorCodeDAO();

    /**
     * Service for records in the MST
     */
    private static RecordService recordService = new DefaultRecordService();
    
    /**
     * Manager to insert/delete jobs in the MST
     */
    private static JobService jobService = new DefaultJobService();

    /**
     * Constant signifying the parser is parsing the service's input formats
     */
    private static final String FILE_OUTPUT_FORMATS = "OUTPUT FORMATS";

    /**
     * Constant signifying the parser is parsing the service's output formats
     */
    private static final String FILE_INPUT_FORMATS = "INPUT FORMATS";

    /**
     * Constant signifying the parser is parsing the service's error messages
     */
    private static final String FILE_ERROR_MESSAGES = "ERROR MESSAGES";

    /**
     * Constant signifying the parser is parsing the service specific configuration
     */
    private static final String FILE_SERVICE_SPECIFIC = "SERVICE CONFIG";

    public DefaultServicesService()
    {
        servicesDao = new DefaultServiceDAO();
    }

    /**
     * Returns a list of all services
     *
     * @return List of Services
     * @throws DatabaseConfigException
     */
    public List<Service> getAllServices() throws DatabaseConfigException
    {
        return servicesDao.getAll();
    }

    /**
     * Returns a sorted list of services
     *
     * @param sort determines whether the list of services is sorted in ascending or descending order
     * @param columnSorted column on which the rows are sorted
     * @return list of services
     * @throws DatabaseConfigException
     */
    public List<Service> getAllServicesSorted(boolean sort,String columnSorted) throws DatabaseConfigException
    {
        return servicesDao.getSorted(sort, columnSorted);
    }

    /**
     * Adds a new Service whose configuration details are present in the file
     *
     * @param configFile
     * @throws xc.mst.dao.DataException
     * @throws java.io.IOException
     * @throws xc.mst.manager.processingDirective.ConfigFileException
     */
    public void addNewService(File configFile) throws DataException, IOException, ConfigFileException
    {
    	BufferedReader in = null; // Reads the file
    	
    	String configFolderPath = configFile.getParentFile().getParent();
    	
    	try
    	{
    		MetadataService mService = null; // An instance of the service we're adding

    		String logFileName = logDao.getById(Constants.LOG_ID_SERVICE_MANAGEMENT).getLogFileLocation();

    		in = new BufferedReader(new FileReader(configFile));

    		// The name of the service, which must appear in the first line of the configuration file
    		String name = in.readLine();
    		name = (name.indexOf('#') >= 0 ? name.substring(0, name.indexOf('#')).trim() : name.trim());
    		if(name == null || name.length() == 0)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The first line of the service configuration file must be the service's name.");
    			throw new ConfigFileException("The first line of the service configuration file must be the service's name.");
    		}

    		// Verify that the name is unique
    		Service oldService = servicesDao.getByServiceName(name);
    		if(oldService != null)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The service's name was not unique.");
    			throw new ConfigFileException("Cannot add a service named " + name + " because a service with that name already exists.");
    		}

    		// The version of the service, which must appear in the second line of the configuration file
    		String version = in.readLine();
    		version = (version.indexOf('#') >= 0 ? version.substring(0, version.indexOf('#')).trim() : version.trim());
    		
    		if(version == null || version.length() == 0)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The second line of the service configuration file must be the service's version.");
    			throw new ConfigFileException("The second line of the service configuration file must be the service's version.");
    		}


    		// The .jar file containing the service, which must appear in the third line of the configuration file
    		String jar = in.readLine();
    		jar = (jar.indexOf('#') >= 0 ? jar.substring(0, jar.indexOf('#')).trim() : jar.trim());
    		if(jar == null || jar.length() == 0 || !jar.endsWith(".jar"))
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The third line of the service configuration file must be the .jar file containing the service.");
    			throw new ConfigFileException("The third line of the service configuration file must be the .jar file containing the service.");
    		}
    		jar = configFolderPath + MSTConfiguration.FILE_SEPARATOR + "serviceJar" + MSTConfiguration.FILE_SEPARATOR + jar;

    		// The name of the service's class, which must appear in the fourth line of the configuration file
    		String className = in.readLine();
    		className = (className.indexOf('#') >= 0 ? className.substring(0, className.indexOf('#')).trim() : className.trim());
    		if(className == null || className.length() == 0)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The fourth line of the service configuration file must be the service's class name.");
    			throw new ConfigFileException("The fourth line of the service configuration file must be the service's class name.");
    		}

    		// The port on which the service's OAI repository operates, which must appear in the fifth line of the configuration file
    		String portString = in.readLine();
    		portString = (portString.indexOf('#') >= 0 ? portString.substring(0, portString.indexOf('#')).trim() : portString.trim());
    		if(portString == null || portString.length() == 0)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The fifth line of the service configuration file must be the service's OAI repository's port.");
    			throw new ConfigFileException("The fifth line of the service configuration file must be the service's OAI repository's port.");
    		}
    		int port = 0;
    		try
    		{
    			port = Integer.parseInt(portString);
    		}
    		catch(NumberFormatException e)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The fifth line of the service configuration file must be the service's OAI repository's port.");
    			throw new ConfigFileException("The fifth line of the service configuration file must be the service's OAI repository's port.");
    		}

    		// The .jar file we need to load the service from
    		File jarFile = new File(jar);

    		// The class loader for the MetadataService class
    		ClassLoader serviceLoader = MetadataService.class.getClassLoader();

    		// Load the class from the .jar file
    		URLClassLoader loader = new URLClassLoader(new URL[] { jarFile.toURI().toURL() }, serviceLoader);
    		try
    		{
				Class<?> clazz = loader.loadClass(className);
				mService = (MetadataService)clazz.newInstance();
			}
    		catch (ClassNotFoundException e)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The class " + className + " could not be found in the .jar file " + jar);
				throw new ConfigFileException("The class " + className + " could not be found in the .jar file " + jar);
			}
    		catch (InstantiationException e)
			{
    			LogWriter.addError(logFileName, "Error adding a new service: The class " + className + " could not be found in the .jar file " + jar);
				throw new ConfigFileException("The class " + className + " could not be found in the .jar file " + jar);
			}
			catch (IllegalAccessException e)
			{
				LogWriter.addError(logFileName, "Error adding a new service: The class " + className + " could not be found in the .jar file " + jar);
				throw new ConfigFileException("The class " + className + " could not be found in the .jar file " + jar);
			}

	    	// Populate the service BO
    		Service service = new Service();
    		service.setName(name);
    		service.setVersion(version);
    		service.setServiceJar(jar);
    		service.setClassName(className);
    		service.setHarvestOutLogFileName(MSTConfiguration.getUrlPath() + MSTConfiguration.FILE_SEPARATOR + "logs" + MSTConfiguration.FILE_SEPARATOR + "harvestOut" + MSTConfiguration.FILE_SEPARATOR + name + ".txt");
    		service.setServicesLogFileName(MSTConfiguration.getUrlPath() + MSTConfiguration.FILE_SEPARATOR + "logs" + MSTConfiguration.FILE_SEPARATOR + "service" + MSTConfiguration.FILE_SEPARATOR + name + ".txt");
    		service.setPort(port);
    		service.setStatus(Constants.STATUS_SERVICE_NOT_RUNNING);
    		service.setXccfgFileName(configFile.getAbsolutePath());

    		// Consume whitespace and comment lines in the configuration file
    		String line = consumeCommentsAndWhitespace(in);

    		// The next line is expected to be the header for the input formats.
    		// Reject anything else as an invalid configuration file.
    		if(!line.trim().equals(FILE_INPUT_FORMATS))
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The first section in the configuration file after the first three lines must be labeled \"INPUT FORMATS\"");
    			throw new ConfigFileException("The first section in the configuration file after the first three lines must be labeled \"INPUT FORMATS\"");
    		}

	    	// Parse and add the input formats
    		do
    		{
    			// This line should either be the name of a format or the OUPUT FORMATS heading
    			line = consumeCommentsAndWhitespace(in);
    			if(line.startsWith("name:"))
    			{
    				String formatName = (line.contains("#") ? line.substring(5, line.indexOf("#")) : line.substring(5)).trim();

    				// Get the format's schema
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("schema location:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format schema location.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format schema location.");
    				}

    				String schemaLoc = (line.contains("#") ? line.substring(16, line.indexOf("#")) : line.substring(16)).trim();

    				// Get the format's namespace
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("namespace:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format namspace.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format namespace.");
    				}

    				String namespace = (line.contains("#") ? line.substring(10, line.indexOf("#")) : line.substring(10)).trim();

    				// Get the format from the database
    				Format format = formatDao.getByName(formatName);

    				// If the format was not in the database, get it from the configuration file
    				if(format == null)
    				{
    					format = new Format();
    					format.setName(formatName);
    					format.setSchemaLocation(schemaLoc);
    					format.setNamespace(namespace);
    					formatDao.insert(format);
    				}
    				// Otherwise check whether or not the configuration file provided the same schema location and namespace for the format.
    				// Log a warning if not
    				else
    				{
    					if(!format.getSchemaLocation().equals(schemaLoc))
    						LogWriter.addWarning(logFileName, "The configuration file specified a schema location for the " +
    								             formatName + " format that differed from the one in the database. " +
    								             "The current schema location of " + format.getSchemaLocation() + " will be used " +
							             		 "and the schema location " + schemaLoc + " from the configuration file will be ignored.");

    					if(!format.getNamespace().equals(namespace))
    						LogWriter.addWarning(logFileName, "The configuration file specified a namespace for the " +
    								             formatName + " format that differed from the one in the database. " +
    								             "The current namespace of " + format.getNamespace() + " will be used " +
							             		 "and the namespace " + namespace + " from the configuration file will be ignored.");
    				}

    				// Add the format we just parsed as an input format for the new service
    				service.addInputFormat(format);
    			}
    			else if(!line.equals(FILE_OUTPUT_FORMATS))
    			{
    				LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format name.");
    				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format name.");
    			}
    		} while(!line.equals(FILE_OUTPUT_FORMATS));

    		// Parse and add the output formats
    		do
    		{
    			// This line should either be the name of a format or the ERROR MESSAGES heading
    			line = consumeCommentsAndWhitespace(in);
    			if(line.startsWith("name:"))
    			{
    				String formatName = (line.contains("#") ? line.substring(5, line.indexOf("#")) : line.substring(5)).trim();

    				// Get the format's schema
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("schema location:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format schema location.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format schema location.");
    				}

    				String schemaLoc = (line.contains("#") ? line.substring(16, line.indexOf("#")) : line.substring(16)).trim();

    				// Get the format's namespace
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("namespace:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format namspace.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format namespace.");
    				}

    				String namespace = (line.contains("#") ? line.substring(10, line.indexOf("#")) : line.substring(10)).trim();

    				// Get the format from the database
    				Format format = formatDao.getByName(formatName);

    				// If the format was not in the database, get it from the configuration file
    				if(format == null)
    				{
    					format = new Format();
    					format.setName(formatName);
    					format.setSchemaLocation(schemaLoc);
    					format.setNamespace(namespace);
    					formatDao.insert(format);
    				}
    				// Otherwise check whether or not the configuration file provided the same
    				// schema location and namespace for the format. Log a warning if not
    				else
    				{
    					if(!format.getSchemaLocation().equals(schemaLoc))
    						LogWriter.addWarning(logFileName, "The configuration file specified a schema location for the " +
    								             formatName + " format that differed from the one in the database. " +
    								             "The current schema location of " + format.getSchemaLocation() + " will be used " +
							             		 "and the schema location " + schemaLoc + " from the configuration file will be ignored.");

    					if(!format.getNamespace().equals(namespace))
    						LogWriter.addWarning(logFileName, "The configuration file specified a namespace for the " +
    								             formatName + " format that differed from the one in the database. " +
    								             "The current namespace of " + format.getNamespace() + " will be used " +
							             		 "and the namespace " + namespace + " from the configuration file will be ignored.");
    				}

    				// Add the format we just parsed as an output format for the new service
    				service.addOutputFormat(format);
    			}
    			else if(!line.equals(FILE_ERROR_MESSAGES))
    			{
    				LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format name.");
    				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format name.");
    			}
    		} while(!line.equals(FILE_ERROR_MESSAGES));

    		// Insert the service
    		servicesDao.insert(service);

    		// Parse and add the error codes
    		do
    		{
    			// This line should either be an error code or the SERVICE CONFIG heading
    			line = consumeCommentsAndWhitespace(in);
    			if(line.startsWith("error code:"))
    			{
    				String errorCodeStr = (line.contains("#") ? line.substring(11, line.indexOf("#")) : line.substring(11)).trim();

    				// Get the format's schema
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("error description file:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected error description file.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected error description file.");
    				}

    				String errorDescriptionFile = (line.contains("#") ? line.substring(23, line.indexOf("#")) : line.substring(23)).trim();

    				ErrorCode errorCode = new ErrorCode();
    				errorCode.setErrorCode(errorCodeStr);
    				errorCode.setErrorDescriptionFile(configFolderPath + MSTConfiguration.FILE_SEPARATOR + "serviceErrors" + MSTConfiguration.FILE_SEPARATOR + errorDescriptionFile);
    				errorCode.setService(service);

    				errorCodeDao.insert(errorCode);
    			}
    			else if(!line.equals(FILE_SERVICE_SPECIFIC))
    			{
    				LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected error code.");
    				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected error code.");
    			}
    		} while(!line.equals(FILE_SERVICE_SPECIFIC));

    		// Parse and save the service specific configuration

    		// Contains the service specific configuration
    		StringBuffer buffer = new StringBuffer();

    		// While there are unread lines in the file
    		while(in.ready())
    		{
    			line = in.readLine(); // A line from the configuration file
    			buffer.append(line).append("\n");
    		}

    		// Set the configuration on the service
    		service.setServiceConfig(buffer.toString());
    		servicesDao.update(service);

    		MetadataService.checkService(service.getId(), Constants.STATUS_SERVICE_NOT_RUNNING, true);
    	}
    	catch(DataException e)
    	{
    		log.error("DataException while adding a service: ", e);
    		throw e;
    	}
    	catch(IOException e)
    	{
    		log.error("IOException while adding a service: ", e);
    		throw e;
    	}
    	catch(ConfigFileException e)
    	{
    		log.error("ConfigFileException while adding a service: ", e);
    		throw e;
    	}
    	catch(Exception e)
    	{
    		log.error("Exception while adding a service: ", e);
    	}
    	finally
    	{
    		// Close the configuration file
    		if(in != null)
    			in.close();
	    }
    }


    public void updateService(File configFile, Service service) throws DataException, IndexException, IOException, ConfigFileException
    {
    	// Reload the service and confirm that it's not currently running.
    	// Throw an error if it is
    	service = servicesDao.getById(service.getId());
    	if(service.getStatus().equals(Constants.STATUS_SERVICE_RUNNING) || service.getStatus().equals(Constants.STATUS_SERVICE_PAUSED))
    		throw new DataException("Cannot update a service while it is running.");

    	BufferedReader in = null; // Reads the file
    	
    	String configFolderPath = configFile.getParentFile().getParent();
    	
    	try
    	{ 
    		MetadataService mService = null; // An instance of the service we're adding

    		String logFileName = logDao.getById(Constants.LOG_ID_SERVICE_MANAGEMENT).getLogFileLocation();

    		in = new BufferedReader(new FileReader(configFile));

    		// The name of the service, which must appear in the first line of the configuration file
    		String name = in.readLine();
    		name = (name.indexOf('#') >= 0 ? name.substring(0, name.indexOf('#')).trim() : name.trim());
    		if(name == null || name.length() == 0)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The first line of the service configuration file must be the service's name.");
    			throw new ConfigFileException("The first line of the service configuration file must be the service's name.");
    		}

    		// Verify that the name is unique
    		Service oldService = servicesDao.getByServiceName(name);
    		if(!service.getName().equals(name) && oldService != null)
    		{
    			LogWriter.addError(logFileName, "Error updating service: The service's name was not unique.");
    			throw new ConfigFileException("Cannot update the service " + name + " because a service with that name already exists.");
    		}

    		// The version of the service, which must appear in the second line of the configuration file
    		String version = in.readLine();
    		version = (version.indexOf('#') >= 0 ? version.substring(0, version.indexOf('#')).trim() : version.trim());
    		if(version == null || version.length() == 0)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The second line of the service configuration file must be the service's version.");
    			throw new ConfigFileException("The second line of the service configuration file must be the service's version.");
    		}

    		
    		// The .jar file containing the service, which must appear in the third line of the configuration file
    		String jar = in.readLine();
    		jar = (jar.indexOf('#') >= 0 ? jar.substring(0, jar.indexOf('#')).trim() : jar.trim());
    		if(jar == null || jar.length() == 0 || !jar.endsWith(".jar"))
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The third line of the service configuration file must be the .jar file containing the service.");
    			throw new ConfigFileException("The third line of the service configuration file must be the .jar file containing the service.");
    		}
    		jar = configFolderPath + MSTConfiguration.FILE_SEPARATOR + "serviceJar" + MSTConfiguration.FILE_SEPARATOR + jar;

    		// The name of the service's class, which must appear in the fourth line of the configuration file
    		String className = in.readLine();
    		className = (className.indexOf('#') >= 0 ? className.substring(0, className.indexOf('#')).trim() : className.trim());
    		if(className == null || className.length() == 0)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The fourth line of the service configuration file must be the service's class name.");
    			throw new ConfigFileException("The fourth line of the service configuration file must be the service's class name.");
    		}

    		// The port on which the service's OAI repository operates, which must appear in the fifth line of the configuration file
    		String portString = in.readLine();
    		portString = (portString.indexOf('#') >= 0 ? portString.substring(0, portString.indexOf('#')).trim() : portString.trim());
    		if(portString == null || portString.length() == 0)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The fifth line of the service configuration file must be the service's OAI repository's port.");
    			throw new ConfigFileException("The fifth line of the service configuration file must be the service's OAI repository's port.");
    		}
    		int port = 0;
    		try
    		{
    			port = Integer.parseInt(portString);
    		}
    		catch(NumberFormatException e)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The fifth line of the service configuration file must be the service's OAI repository's port.");
    			throw new ConfigFileException("The fifth line of the service configuration file must be the service's OAI repository's port.");
    		}

    		// The .jar file we need to load the service from
    		File jarFile = new File(jar);

    		// The class loader for the MetadataService class
    		ClassLoader serviceLoader = MetadataService.class.getClassLoader();

    		// Load the class from the .jar file
    		URLClassLoader loader = new URLClassLoader(new URL[] { jarFile.toURI().toURL() }, serviceLoader);
    		try
    		{
				Class<?> clazz = loader.loadClass(className);
				mService = (MetadataService)clazz.newInstance();
			}
    		catch (ClassNotFoundException e)
    		{
    			LogWriter.addError(logFileName, "Error adding a new service: The class " + className + " could not be found in the .jar file " + jar);
				throw new ConfigFileException("The class " + className + " could not be found in the .jar file " + jar);
			}
    		catch (InstantiationException e)
			{
    			LogWriter.addError(logFileName, "Error adding a new service: The class " + className + " could not be found in the .jar file " + jar);
				throw new ConfigFileException("The class " + className + " could not be found in the .jar file " + jar);
			}
			catch (IllegalAccessException e)
			{
				LogWriter.addError(logFileName, "Error adding a new service: The class " + className + " could not be found in the .jar file " + jar);
				throw new ConfigFileException("The class " + className + " could not be found in the .jar file " + jar);
			}

	    	// Populate the service BO
    		service.setName(name);
    		service.setVersion(version);
    		service.setServiceJar(jar);
    		service.setClassName(className);
    		service.setHarvestOutLogFileName(MSTConfiguration.getUrlPath() + MSTConfiguration.FILE_SEPARATOR + "logs" + MSTConfiguration.FILE_SEPARATOR + "harvestOut" + MSTConfiguration.FILE_SEPARATOR + name + ".txt");
    		service.setServicesLogFileName(MSTConfiguration.getUrlPath() + MSTConfiguration.FILE_SEPARATOR + "logs" + MSTConfiguration.FILE_SEPARATOR + "service" + MSTConfiguration.FILE_SEPARATOR + name + ".txt");
    		service.setPort(port);
    		service.setXccfgFileName(configFile.getAbsolutePath());
    		service.setHarvestOutWarnings(0);
    		service.setHarvestOutErrors(0);
    		service.setServicesWarnings(0);
    		service.setServicesErrors(0);
    		service.getInputFormats().clear();
    		service.getOutputFormats().clear();

    		// Consume whitespace and comment lines in the configuration file
    		String line = consumeCommentsAndWhitespace(in);

    		// The next line is expected to be the header for the input formats.
    		// Reject anything else as an invalid configuration file.
    		if(!line.trim().equals(FILE_INPUT_FORMATS))
    		{
    			LogWriter.addError(logFileName, "Error updating service: The first section in the configuration file after the first three lines must be labeled \"INPUT FORMATS\"");
    			throw new ConfigFileException("The first section in the configuration file after the first three lines must be labeled \"INPUT FORMATS\"");
    		}

	    	// Parse and add the input formats
    		do
    		{
    			// This line should either be the name of a format or the OUPUT FORMATS heading
    			line = consumeCommentsAndWhitespace(in);
    			if(line.startsWith("name:"))
    			{
    				String formatName = (line.contains("#") ? line.substring(5, line.indexOf("#")) : line.substring(5)).trim();

    				// Get the format's schema
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("schema location:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format schema location.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format schema location.");
    				}

    				String schemaLoc = (line.contains("#") ? line.substring(16, line.indexOf("#")) : line.substring(16)).trim();

    				// Get the format's namespace
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("namespace:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format namspace.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format namespace.");
    				}

    				String namespace = (line.contains("#") ? line.substring(10, line.indexOf("#")) : line.substring(10)).trim();

    				// Get the format from the database
    				Format format = formatDao.getByName(formatName);

    				// If the format was not in the database, get it from the configuration file
    				if(format == null)
    				{
    					format = new Format();
    					format.setName(formatName);
    					format.setSchemaLocation(schemaLoc);
    					format.setNamespace(namespace);
    					formatDao.insert(format);
    				}
    				// Otherwise check whether or not the configuration file provided the same schema location and namespace for the format.
    				// Log a warning if not
    				else
    				{
    					if(!format.getSchemaLocation().equals(schemaLoc))
    						LogWriter.addWarning(logFileName, "The configuration file specified a schema location for the " +
    								             formatName + " format that differed from the one in the database. " +
    								             "The current schema location of " + format.getSchemaLocation() + " will be used " +
							             		 "and the schema location " + schemaLoc + " from the configuration file will be ignored.");

    					if(!format.getNamespace().equals(namespace))
    						LogWriter.addWarning(logFileName, "The configuration file specified a namespace for the " +
    								             formatName + " format that differed from the one in the database. " +
    								             "The current namespace of " + format.getNamespace() + " will be used " +
							             		 "and the namespace " + namespace + " from the configuration file will be ignored.");
    				}

    				// Add the format we just parsed as an input format for the new service
    				service.addInputFormat(format);
    			}
    			else if(!line.equals(FILE_OUTPUT_FORMATS))
    			{
    				LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format name.");
    				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format name.");
    			}
    		} while(!line.equals(FILE_OUTPUT_FORMATS));

    		// Parse and add the output formats
    		do
    		{
    			// This line should either be the name of a format or the ERROR MESSAGES heading
    			line = consumeCommentsAndWhitespace(in);
    			if(line.startsWith("name:"))
    			{
    				String formatName = (line.contains("#") ? line.substring(5, line.indexOf("#")) : line.substring(5)).trim();

    				// Get the format's schema
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("schema location:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format schema location.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format schema location.");
    				}

    				String schemaLoc = (line.contains("#") ? line.substring(16, line.indexOf("#")) : line.substring(16)).trim();

    				// Get the format's namespace
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("namespace:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format namspace.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format namespace.");
    				}

    				String namespace = (line.contains("#") ? line.substring(10, line.indexOf("#")) : line.substring(10)).trim();

    				// Get the format from the database
    				Format format = formatDao.getByName(formatName);

    				// If the format was not in the database, get it from the configuration file
    				if(format == null)
    				{
    					format = new Format();
    					format.setName(formatName);
    					format.setSchemaLocation(schemaLoc);
    					format.setNamespace(namespace);
    					formatDao.insert(format);
    				}
    				// Otherwise check whether or not the configuration file provided the same
    				// schema location and namespace for the format. Log a warning if not
    				else
    				{
    					if(!format.getSchemaLocation().equals(schemaLoc))
    						LogWriter.addWarning(logFileName, "The configuration file specified a schema location for the " +
    								             formatName + " format that differed from the one in the database. " +
    								             "The current schema location of " + format.getSchemaLocation() + " will be used " +
							             		 "and the schema location " + schemaLoc + " from the configuration file will be ignored.");

    					if(!format.getNamespace().equals(namespace))
    						LogWriter.addWarning(logFileName, "The configuration file specified a namespace for the " +
    								             formatName + " format that differed from the one in the database. " +
    								             "The current namespace of " + format.getNamespace() + " will be used " +
							             		 "and the namespace " + namespace + " from the configuration file will be ignored.");
    				}

    				// Add the format we just parsed as an output format for the new service
    				service.addOutputFormat(format);
    			}
    			else if(!line.equals(FILE_ERROR_MESSAGES))
    			{
    				LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected format name.");
    				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected format name.");
    			}
    		} while(!line.equals(FILE_ERROR_MESSAGES));

    		// Parse and add the error codes
    		do
    		{
    			// This line should either be an error code or the SERVICE CONFIG heading
    			line = consumeCommentsAndWhitespace(in);
    			if(line.startsWith("error code:"))
    			{
    				String errorCodeStr = (line.contains("#") ? line.substring(11, line.indexOf("#")) : line.substring(11)).trim();

    				// Get the format's schema
    				line = consumeCommentsAndWhitespace(in);
    				if(!line.startsWith("error description file:"))
    				{
    					LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected error description file.");
        				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected error description file.");
    				}

    				String errorDescriptionFile = (line.contains("#") ? line.substring(23, line.indexOf("#")) : line.substring(23)).trim();

    				ErrorCode errorCode = errorCodeDao.getByErrorCodeAndService(errorCodeStr, service);
    				if(errorCode == null)
    				{
    					errorCode = new ErrorCode();
    					errorCode.setErrorCode(errorCodeStr);
    					errorCode.setErrorDescriptionFile(configFolderPath + MSTConfiguration.FILE_SEPARATOR + "serviceErrors" + MSTConfiguration.FILE_SEPARATOR + errorDescriptionFile);
    					errorCode.setService(service);
    					errorCodeDao.insert(errorCode);
    				}
    				else
    				{
    					errorCode.setErrorDescriptionFile(configFolderPath + MSTConfiguration.FILE_SEPARATOR + "serviceErrors" + MSTConfiguration.FILE_SEPARATOR + errorDescriptionFile);
    					errorCodeDao.update(errorCode);
    				}
    			}
    			else if(!line.equals(FILE_SERVICE_SPECIFIC))
    			{
    				LogWriter.addError(logFileName, "Error adding a new service: Invalid line in the configuration file: " + line + ".  Expected error code.");
    				throw new ConfigFileException("Invalid line in the configuration file: " + line + ".  Expected error code.");
    			}
    		} while(!line.equals(FILE_SERVICE_SPECIFIC));

    		// Parse and save the service specific configuration

    		// Contains the service specific configuration
    		StringBuffer buffer = new StringBuffer();

    		// While there are unread lines in the file
    		line = in.readLine();
    		while(in.ready())
    		{
    			line = in.readLine(); // A line from the configuration file
    			buffer.append(line).append("\n");
    		}

    		// Set the configuration on the service
    		service.setServiceConfig(buffer.toString());
    		servicesDao.update(service);

    		// TODO what does below line do? Is it necessary? Should it be here or moved to Service Reprocess thread?
    		MetadataService.checkService(service.getId(), Constants.STATUS_SERVICE_NOT_RUNNING, true);

    		// Schedule a job to reprocess records through new service
    		try {
				Job job = new Job(service, 0, Constants.THREAD_SERVICE_REPROCESS);
				job.setOrder(jobService.getMaxOrder() + 1); 
				jobService.insertJob(job);
			} catch (DatabaseConfigException dce) {
				log.error("DatabaseConfig exception occured when ading jobs to database", dce);
			}
    	}
    	finally
    	{
    		// Close the configuration file
    		if(in != null)
    			in.close();
	    }
    }

    /**
     * Inserts a service into the MST
     *
     * @param service service object
     * @throws xc.mst.dao.DataException
     */
    public void insertService(Service service) throws DataException
    {
        servicesDao.insert(service);
    }

    /**
     * Deletes a service from the MST
     *
     * @param service service object
     * @throws xc.mst.dao.DataException
     */
    public void deleteService(Service service) throws IndexException, DataException
    {
    	// Reload the service and confirm that it's not currently running.
    	// Throw an error if it is
    	service = servicesDao.getById(service.getId());
    	if(service.getStatus().equals(Constants.STATUS_SERVICE_RUNNING) || service.getStatus().equals(Constants.STATUS_SERVICE_PAUSED))
    		throw new DataException("Cannot update a service while it is running.");

    	// Delete the records processed by the service and send the deleted
    	// records to subsequent services so they know about the delete
		RecordList records = recordService.getByServiceId(service.getId());

		// A list of services which must be run after this one
		List<Service> affectedServices = new ArrayList<Service>();

		for(Record record : records)
		{
			// Remove from Predecessor
			record.setDeleted(true);
			
			// Get all predecessors & remove the current record as successor
			List<Record> prdecessors =  record.getProcessedFrom();
			for (Record predecessor : prdecessors) {
				predecessor =  recordService.getById(predecessor.getId());
				predecessor.removeSucessor(record);
				recordService.update(predecessor);
			}
			
			record.setUpdatedAt(new Date());
			for(Service nextService : record.getProcessedByServices())
			{
				record.addInputForService(nextService);
				affectedServices.add(nextService);
			}
			recordService.update(record);
		}
		SolrIndexManager.getInstance().commitIndex();

		// Schedule subsequent services to process that the record was deleted
		for(Service nextSerivce : affectedServices)
		{
			try {
				Job job = new Job(nextSerivce, 0, Constants.THREAD_SERVICE);
				job.setOrder(jobService.getMaxOrder() + 1); 
				jobService.insertJob(job);
			} catch (DatabaseConfigException dce) {
				log.error("DatabaseConfig exception occured when ading jobs to database", dce);
			}
		}

        servicesDao.delete(service);
    }

    /**
     * Updates the details related to a service
     *
     * @param service service object
     * @throws xc.mst.dao.DataException
     */
    public void updateService(Service service) throws DataException
    {
        servicesDao.update(service);
    }

    /**
     * Returns a service by ID
     *
     * @param serviceId service ID
     * @return Service object
     * @throws DatabaseConfigException
     */
    public Service getServiceById(int serviceId) throws DatabaseConfigException
    {
        return servicesDao.getById(serviceId);
    }

    /**
     * Returns a service by name
     *
     * @param serviceName name of the service
     * @return service object
     * @throws DatabaseConfigException
     */
    public Service getServiceByName(String serviceName) throws DatabaseConfigException
    {
        return servicesDao.getByServiceName(serviceName);
    }

    /**
     * Returns service by port number
     *
     * @param servicePort service port
     * @return service object
     * @throws DatabaseConfigException
     */
    public Service getServiceByPort(int servicePort) throws DatabaseConfigException
    {
        return servicesDao.getByPort(servicePort);
    }

    /**
     * Given a BufferedReader for a File, skip to the next line in the file
     * which is neither a comment nor whitespace
     *
     * @param in The BufferedReader for the file
     * @return The first non-comment non-whitespace line in the file, or null if we reached the end of the file
     * @throws IOException If an error occurred while reading the file
     */
    private String consumeCommentsAndWhitespace(BufferedReader in) throws IOException
    {
    	while(in.ready())
		{
			String line = in.readLine(); // A line from the configuration file

			// If the line is a valid line, return it
			if(!line.startsWith("#") && line.trim().length() > 0)
				return line.trim();
		}

    	// If we got here we reached the end of the file, so return null
    	return null;
    }
}
