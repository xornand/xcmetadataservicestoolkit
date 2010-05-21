/**
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */

package xc.mst.services;

import java.util.List;

import xc.mst.bo.provider.Format;
import xc.mst.bo.provider.Set;
import xc.mst.bo.service.Service;
import xc.mst.repo.Repository;

public interface MetadataService {
	
	public void install();
	
	public void uninstall();
	
	public void update();
	
	//public void process(Record r);
	
	public Repository getRepository();
	
	public void process(Repository repo, Format format, Set set);
	
	// leftover
	public void runService(int serviceId, int outputSetId);
	public void setStatus(String status);
	public boolean sendReportEmail(String problem);
	public void setCanceled(boolean isCanceled);
	public void setPaused(boolean isPaused);
	public Service getService();
	public void setService(Service service);
	public String getServiceName();
	public String getServiceStatus();
	public int getProcessedRecordCount();
	public int getTotalRecordCount();
	public List<String> getUnprocessedErrorRecordIdentifiers();
	public void setUnprocessedErrorRecordIdentifiers(List<String> l);

}