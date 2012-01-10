/**
 * Copyright (c) 2010 eXtensible Catalog Organization
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
 * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
 * website http://www.extensiblecatalog.org/.
 *
 */
package xc.mst.services.marcaggregation.dao;


import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import xc.mst.services.impl.dao.GenericMetadataServiceDAO;
import xc.mst.utils.TimingLogger;

/**
*
* @author John Brand
*
*/
public class MarcAggregationServiceDAO extends GenericMetadataServiceDAO {

    private final static Logger LOG = Logger.getLogger(MarcAggregationServiceDAO.class);

    protected final static String bibsProcessedLongId_table = "bibsProcessedLongId";
    protected final static String bibsProcessedStringId_table = "bibsProcessedStringId";
    protected final static String bibsYet2ArriveLongId_table = "bibsYet2ArriveLongId";
    protected final static String bibsYet2ArriveStringId_table = "bibsYet2ArriveStringId";
    protected final static String held_holdings_table = "held_holdings";

    protected final static String matchpoints_010a_table = "matchpoints_010a";
    protected final static String matchpoints_020a_table = "matchpoints_020a";
    protected final static String matchpoints_022a_table = "matchpoints_022a";
    protected final static String matchpoints_024a_table = "matchpoints_024a";
    protected final static String matchpoints_028a_table = "matchpoints_028a";
    protected final static String matchpoints_035a_table = "matchpoints_035a";
    protected final static String matchpoints_130a_table = "matchpoints_130a";
    protected final static String matchpoints_240a_table = "matchpoints_240a";
    protected final static String matchpoints_245a_table = "matchpoints_245a";
    protected final static String matchpoints_260abc_table = "matchpoints_260abc";



    @SuppressWarnings("unchecked")
    //perhaps will move this up to the generic layer - since 2 services will end up with identical code.
    public void persistBibMaps(
        ) {
        TimingLogger.start("MarcAggregationServiceDAO.persistBibMaps");
        TimingLogger.stop("MarcAggregationServiceDAO.persistBibMaps");
    }

    @SuppressWarnings("unchecked")
    public void persistMatchpointMaps(
        ) {
        TimingLogger.start("MarcAggregationServiceDAO.persistMatchpointMaps");


        TimingLogger.stop("MarcAggregationServiceDAO.persistMatchpointMaps");
    }

    protected List<Map<String, Object>> getMaps(String tableName, int page) {
//        TimingLogger.start("getMaps");
        int recordsAtOnce = 250000;
        List<Map<String, Object>> rowList =null;//= this.jdbcTemplate.queryForList(
//                "select org_code, bib_001, record_id from " + tableName +
//                        " limit " + (page * recordsAtOnce) + "," + recordsAtOnce);
//        TimingLogger.stop("getMaps");
        return rowList;
    }

    @SuppressWarnings("unchecked")
    public void loadMaps(
        ) {
    }
}
