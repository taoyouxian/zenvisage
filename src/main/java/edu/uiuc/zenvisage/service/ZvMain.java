/**
 *
 */
package edu.uiuc.zenvisage.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import edu.uiuc.zenvisage.data.Query;
import edu.uiuc.zenvisage.data.remotedb.SQLQueryExecutor;
import edu.uiuc.zenvisage.data.remotedb.SchemeToMetatable;
import edu.uiuc.zenvisage.data.remotedb.VisualComponent;
import edu.uiuc.zenvisage.data.roaringdb.db.Column;
import edu.uiuc.zenvisage.data.roaringdb.db.ColumnMetadata;
import edu.uiuc.zenvisage.data.remotedb.VisualComponentList;
import edu.uiuc.zenvisage.data.remotedb.WrapperType;
import edu.uiuc.zenvisage.data.roaringdb.db.Database;
import edu.uiuc.zenvisage.data.roaringdb.db.DatabaseMetaData;
import edu.uiuc.zenvisage.data.roaringdb.executor.Executor;
import edu.uiuc.zenvisage.data.roaringdb.executor.ExecutorResult;
import edu.uiuc.zenvisage.service.cluster.Clustering;
import edu.uiuc.zenvisage.service.cluster.KMeans;
import edu.uiuc.zenvisage.service.distance.DTWDistance;
import edu.uiuc.zenvisage.service.distance.Distance;
import edu.uiuc.zenvisage.service.distance.Euclidean;
import edu.uiuc.zenvisage.service.distance.SegmentationDistance;
import edu.uiuc.zenvisage.model.*;
import edu.uiuc.zenvisage.service.utility.DataReformation;
import edu.uiuc.zenvisage.service.utility.LinearNormalization;
import edu.uiuc.zenvisage.service.utility.Normalization;
import edu.uiuc.zenvisage.service.utility.Original;
import edu.uiuc.zenvisage.service.utility.PiecewiseAggregation;
import edu.uiuc.zenvisage.server.DatabaseAutoLoader;
import edu.uiuc.zenvisage.server.UploadHandleServlet;
import edu.uiuc.zenvisage.service.utility.Zscore;
import edu.uiuc.zenvisage.zql.executor.ZQLExecutor;
import edu.uiuc.zenvisage.zql.executor.ZQLTable;
import edu.uiuc.zenvisage.zqlcomplete.executor.Name;
import edu.uiuc.zenvisage.zqlcomplete.executor.ZQLRow;
import edu.uiuc.zenvisage.zqlcomplete.executor.ZQLRowResult;
import edu.uiuc.zenvisage.zqlcomplete.executor.ZQLRowVizResult;
import edu.uiuc.zenvisage.zqlcomplete.querygraph.QueryGraph;
import edu.uiuc.zenvisage.zqlcomplete.querygraph.ZQLParser;
import edu.uiuc.zenvisage.service.distance.*;

/**
 * @author tarique
 *
 */
public class ZvMain {

	private Result cachedResult = new Result();
	private BaselineQuery cachedQuery = new BaselineQuery();
//	private InMemoryDatabase inMemoryDatabase;
//	private Map<String,Database> inMemoryDatabases = new HashMap<String,Database>();

	private Database inMemoryDatabase;

	//public Executor executor = new Executor(inMemoryDatabase);
	public Analysis analysis;
	public Distance distance;
	public Normalization normalization;
	public Normalization outputNormalization;
	public PiecewiseAggregation paa;
	public ArrayList<List<Double>> data;
	public String databaseName;
	public String buffer = null;
	private static SQLQueryExecutor sqlQueryExecutor;

	public ZvMain() throws IOException, InterruptedException, SQLException{
		sqlQueryExecutor = new SQLQueryExecutor();
		System.out.println("ZVMAIN LOADED");
	}

//	public void loadData() throws IOException, InterruptedException{
//
//		inMemoryDatabase = createDatabase("real_estate","/data/real_estate.txt","/data/real_estate.csv");
//		inMemoryDatabases.put("real_estate", inMemoryDatabase);
//
//
//		inMemoryDatabase = createDatabase("cmu", "/data/cmuwithoutidschema.txt", "/data/fullcmuwithoutid.csv");
//		inMemoryDatabases.put("cmu", inMemoryDatabase);
//
//
//		inMemoryDatabase = createDatabase("cmutesting", "/data/cmuhaha.txt", "/data/cmuhaha.csv");
//		inMemoryDatabases.put("cmutesting", inMemoryDatabase);
//
//		inMemoryDatabase = createDatabase("sales", "/data/sales.txt", "/data/sales.csv");
//		inMemoryDatabases.put("sales", inMemoryDatabase);
//
//		System.out.println("Done loading data");
//	}

//	public static Database createDatabase(String name,String schemafile,String datafile) throws IOException, InterruptedException{
//    	Database database = new Database(name,schemafile,datafile);
//    	return database;
//
//    }

	public void fileUpload(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException, InterruptedException, SQLException {
		UploadHandleServlet uploadHandler = new UploadHandleServlet();
		List<String> names = uploadHandler.upload(request, response);
		uploadDatasettoDB(names,true);
	}

		
   public static void uploadDatasettoDB(List<String> names, boolean overwrite) throws SQLException, IOException, InterruptedException{
		SchemeToMetatable schemeToMetatable = new SchemeToMetatable();
		
		if (names.size() == 3) {

			/*create csv table*/
			if(!sqlQueryExecutor.isTableExists(names.get(0))){
				
				/*insert zenvisage_metafilelocation*/
				String locationTupleSQL = "INSERT INTO zenvisage_metafilelocation (database, metafilelocation, csvfilelocation) VALUES "+
						"('" + names.get(0) +"', '"+ names.get(2)+"', '"+ names.get(1)+"');";
				if(sqlQueryExecutor.insert(locationTupleSQL, "zenvisage_metafilelocation", "database", names.get(0))){
					System.out.println("Metafilelocation Data successfully inserted into Postgres");
				} else {
					System.out.println("Metafilelocation already exists!");
				}
				
				/*insert zenvisage_metatable*/
				
				if(sqlQueryExecutor.insert(schemeToMetatable.schemeFileToMetaSQLStream(names.get(2), names.get(0)), "zenvisage_metatable", "tablename",  names.get(0))){
					System.out.println("MetaType Data successfully inserted into Postgres");
				} else {
					System.out.println("MetaType already exists!");
				}
				
				sqlQueryExecutor.createTable(schemeToMetatable.createTableSQL);
				sqlQueryExecutor.insertTable(names.get(0), names.get(1), schemeToMetatable.columns);
				System.out.println(names.get(0) + " not exists! Created " + names.get(0) + " table from "+names.get(1));
				System.out.println("Successful upload! "+ names.get(0) +" "+names.get(2) + " "+  names.get(1));
				
			} else if(overwrite) {//
				sqlQueryExecutor.dropTable(names.get(0));
				sqlQueryExecutor.createTable(schemeToMetatable.schemeFileToCreatTableSQL(names.get(2), names.get(0)));
				sqlQueryExecutor.insertTable(names.get(0), names.get(1), schemeToMetatable.columns);
				System.out.println(names.get(0) + " exists! Overwrite and create " + names.get(0) + " from "+names.get(1));
			}

			new Database(names.get(0), names.get(2), names.get(1), true);
			//inMemoryDatabase = createDatabase(names.get(0), names.get(2), names.get(1));


//			inMemoryDatabases.put(names.get(0), inMemoryDatabase);
		}
		
	}
	
	
	
	
//   public String runZQLCompleteQuery(String zqlQuery) throws IOException, InterruptedException, SQLException{
//		  System.out.println(zqlQuery);
//	   	  inMemoryDatabase = inMemoryDatabases.get("real_estate");
//		  executor = new Executor(inMemoryDatabase);
//		  edu.uiuc.zenvisage.zqlcomplete.executor.ZQLExecutor.executor=executor;
//		  edu.uiuc.zenvisage.zqlcomplete.executor.ZQLTable zqlTable = new ObjectMapper().readValue(zqlQuery, edu.uiuc.zenvisage.zqlcomplete.executor.ZQLTable.class);
//     	  String result=new ObjectMapper().writeValueAsString(edu.uiuc.zenvisage.zqlcomplete.executor.ZQLExecutor.execute(zqlTable));
//     	  System.out.println(result);
//     	  return result;
//		  return new ObjectMapper().writeValueAsString(ZQLExecutor.execute(ZQLTest.createZQLTable()));
//
//		}

   /**
    * 
    * @param zqlQuery Receives as a string the JSON format of a ZQLTable
    * @return String representing JSON format of Result (output of running ZQLTable through our query graph)
    * @throws IOException
    * @throws InterruptedException
    */
   public String runQueryGraph(String zqlQuery) throws IOException, InterruptedException{
	   System.out.println(zqlQuery);
	   edu.uiuc.zenvisage.zqlcomplete.executor.ZQLTable zqlTable = new ObjectMapper().readValue(zqlQuery, edu.uiuc.zenvisage.zqlcomplete.executor.ZQLTable.class);
	   ZQLParser parser = new ZQLParser();
	   QueryGraph graph;
	   try {
		   graph = parser.processZQLTable(zqlTable);
		   VisualComponentList output = edu.uiuc.zenvisage.zqlcomplete.querygraph.QueryGraphExecutor.execute(graph);
		   //convert it into front-end format.
		   String result = new ObjectMapper().writeValueAsString(convertVCListtoVisualOutput(output));
		   //System.out.println(" Query Graph Execution Results Are:");
		   //System.out.println(result);
		   System.out.println("Done");
		   return result;
	   } catch (SQLException e) {
		   e.printStackTrace();
		   return "";
	   }
   }

   public Result convertVCListtoVisualOutput(VisualComponentList vcList){
		Result finalOutput = new Result();
		//VisualComponentList -> Result. Only care about the outputcharts. this is for submitZQL
	    for(VisualComponent viz : vcList.getVisualComponentList()) {
	    	Chart outputChart = new Chart();

	    	outputChart.setzType( viz.getzAttribute() );
	    	outputChart.setxType( viz.getxAttribute() );
	    	outputChart.setyType( viz.getyAttribute() );
	    	outputChart.title = viz.getZValue().getStrValue();
	    	outputChart.setNormalizedDistance(viz.getScore());
	    	// outputChart.setxType((++i) + " : " + viz.getZValue().getStrValue());
	    	// outputChart.setyType("avg" + "(" + viz.getyAttribute() + ")");
	    	// outputChart.title = "From Query Graph";

	    	for(WrapperType xValue : viz.getPoints().getXList()) {
	    		outputChart.xData.add(xValue.toString());
	    	}
	    	for(WrapperType yValue : viz.getPoints().getYList()) {
	    		outputChart.yData.add(yValue.toString());
	    	}
	    	finalOutput.outputCharts.add(outputChart);
	    }
		return finalOutput;
	 }

   //old
//   public String runZQLQuery(String zqlQuery) throws IOException, InterruptedException{
//		  inMemoryDatabase = inMemoryDatabases.get("real_estate");
//		  executor = new Executor(inMemoryDatabase);
//		  ZQLExecutor.executor=executor;
//		  ZQLTable zqlTable = new ObjectMapper().readValue(zqlQuery,ZQLTable.class);
//		  return new ObjectMapper().writeValueAsString(ZQLExecutor.execute(zqlTable));
//		  return new ObjectMapper().writeValueAsString(ZQLExecutor.execute(ZQLTest.createZQLTable()));
//
//		}


//	public String getScatterPlot(String query) throws JsonParseException, JsonMappingException, IOException {
//		System.out.print(query);
//		ScatterPlotQuery q = new ObjectMapper().readValue(query, ScatterPlotQuery.class);
//		Map<String, ScatterResult> output = executor.getScatterData(q);
//		if (output == null) return "";
//		Result finalOutput = new Result();
//		finalOutput.method = q.method;
//		if (q.method == "ScatterRep") {
//			ScatterRep.compute(output, q, finalOutput);
//		}
//		else {
//			ScatterRank.compute(output, q, finalOutput);
//		}
//		ObjectMapper mapper = new ObjectMapper();
//		return mapper.writeValueAsString(finalOutput);
//	}

	/**
	 * Given a front end sketch or drag and drop, run similarity search through the query graph backend
	 * @param zvQuery
	 * @return
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public String runSimilaritySearch(String zvQuery) throws InterruptedException, IOException {
		String result = "";
		
		ZvQuery args = new ObjectMapper().readValue(zvQuery, ZvQuery.class);
	    ZQLParser parser = new ZQLParser();
	   //QueryGraph graph = parser.processZQLTable(zqlTable);
	   //VisualComponentList output = edu.uiuc.zenvisage.zqlcomplete.querygraph.QueryGraphExecutor.execute(graph);		
		
		return result;
	}
	
	private ZQLTable createSimilairtySearchTable(ZvQuery args) {
		ZQLTable table = new ZQLTable();
		List<ZQLRow> rows = new ArrayList<ZQLRow>();
		
		Name name1 = new Name();
		name1.setName("f1");
		
		return null;
	}

	public String runCreateClasses(String query) throws IOException, SQLException{
		System.out.println("Create Dynamic Classes Configuration Query:" + query);
	    DynamicClass dc = new ObjectMapper().readValue(query, DynamicClass.class);
	    sqlQueryExecutor.persistDynamicClassPowerSetMethod(dc);
	    sqlQueryExecutor.persistDynamicClassDetails(dc);
	    
        //Testing
        //String retrieved = runRetrieveClasses("{\"dataset\": \"real_estate\"}");
        //System.out.println("Retrieved query:"+retrieved);

	    return "Success";
	    
	}
	
	public String runRetrieveClasses(String query) throws IOException, SQLException{
		DynamicClass dc = sqlQueryExecutor.retrieveDynamicClassDetails(query);
		String retrieved = new ObjectMapper().writeValueAsString(dc);
		System.out.println("Retrieved Dynamic Classes Configuration Query:"+retrieved);
		return retrieved;
	}
	/* Will be obsolete when the new separated query method is utilized */
//	public String runDragnDropInterfaceQuery(String query) throws InterruptedException, IOException{
//		// get data from database
//		System.out.println(query);
//		 ZvQuery args = new ObjectMapper().readValue(query,ZvQuery.class);
//		 Query q = new Query("query").setGrouby(args.groupBy+","+args.xAxis).setAggregationFunc(args.aggrFunc).setAggregationVaribale(args.aggrVar);
//		 if (args.method.equals("SimilaritySearch"))
//			 setFilter(q, args);
//		 ExecutorResult executorResult = executor.getData(q);
//		 if (executorResult == null) return "";
//		 LinkedHashMap<String, LinkedHashMap<Float, Float>> output = executorResult.output;
//		 // setup result format
//		 Result finalOutput = new Result();
//		 finalOutput.method = args.method;
//		 //finalOutput.xUnit = inMemoryDatabase.getColumnMetaData(args.xAxis).unit;
//		 //finalOutput.yUnit = inMemoryDatabase.getColumnMetaData(args.yAxis).unit;
//		 // generate new result for query
//		 ChartOutputUtil chartOutput = new ChartOutputUtil(finalOutput, args, executorResult.xMap);
//		 // generate the corresponding distance metric
//		 if (args.distance_metric.equals("Euclidean")) {
//			 distance = new Euclidean();
//		 }
//		 else {
//			 distance = new DTWDistance();
//		 }
//		 // generate the corresponding data normalization metric
//		 if (args.distanceNormalized) {
//			 normalization = new Zscore();
////			 normalization = new Original();
//		 }
//		 else {
//			 normalization = new Original();
//		 }
//		 // generate the corresponding output normalization
//
//		 outputNormalization = new Original();
//		 // reformat database data
//		 DataReformation dataReformatter = new DataReformation(outputNormalization);
//		 double[][] normalizedgroups = dataReformatter.reformatData(output);
//		 // generate the corresponding analysis method
//		 if (args.method.equals("Outlier")) {
//			 Clustering cluster = new KMeans(distance, normalization, args);
//			 analysis = new Outlier(executor,inMemoryDatabase,chartOutput,distance,normalization,cluster,args);
//		 }
//		 else if (args.method.equals("RepresentativeTrends")) {
//			 Clustering cluster = new KMeans(distance, normalization, args);
//			 analysis = new Representative(executor,inMemoryDatabase,chartOutput,distance,normalization,cluster,args);
//		 }
//		 else if (args.method.equals("SimilaritySearch")) {
//			 paa = new PiecewiseAggregation(normalization, args, inMemoryDatabase);
//			 analysis = new Similarity(executor,inMemoryDatabase,chartOutput,distance,normalization,paa,args,dataReformatter);
//			 ((Similarity) analysis).setDescending(false);
//		 }
//		 else if (args.method.equals("DissimilaritySearch")) {
//			 paa = new PiecewiseAggregation(normalization, args, inMemoryDatabase);
//			 analysis = new Similarity(executor,inMemoryDatabase,chartOutput,distance,normalization,paa,args,dataReformatter);
//			 ((Similarity) analysis).setDescending(true);
//		 }
//		 analysis.compute(output, normalizedgroups, args);
//
//		 ObjectMapper mapper = new ObjectMapper();
//		 return mapper.writeValueAsString(analysis.getChartOutput().finalOutput);
//	}


	public String runDragnDropInterfaceQuerySeparated(String query, String method) throws InterruptedException, IOException, SQLException{
		// get data from database
//		System.out.println(query);

		 ZvQuery args = new ObjectMapper().readValue(query,ZvQuery.class);

		 Query q = new Query("query").setGrouby(args.groupBy+","+args.xAxis).setAggregationFunc(args.aggrFunc).setAggregationVaribale(args.aggrVar);
		 if (method.equals("SimilaritySearch"))
			 setFilter(q, args);

//		 ExecutorResult executorResult = executor.getData(q);
//		 if (executorResult == null) return "";
//		 LinkedHashMap<String, LinkedHashMap<Float, Float>> output = executorResult.output;
		 /*
		  * Instead of calling roaring db, we feed in VC output from postgres
		  * ExecutorResult executorResult = executor.getData(q);
		  * if (executorResult == null) return "";
		  * LinkedHashMap<String, LinkedHashMap<Float, Float>> output = executorResult.output;
		  */
		 System.out.println("Before SQL");
		 //sqlQueryExecutor.ZQLQuery(Z, X, Y, table, whereCondition);
		 sqlQueryExecutor.ZQLQueryEnhanced(q.getZQLRow(), this.databaseName);
		 System.out.println("After SQL");
		 LinkedHashMap<String, LinkedHashMap<Float, Float>> output =  sqlQueryExecutor.getVisualComponentList().toInMemoryHashmap();
		 System.out.println("After To HashMap");
		 output = cleanUpDataWithAllZeros(output);
		 // setup result format
		 Result finalOutput = new Result();
		 finalOutput.method = method;
		 //finalOutput.xUnit = inMemoryDatabase.getColumnMetaData(args.xAxis).unit;
		 //finalOutput.yUnit = inMemoryDatabase.getColumnMetaData(args.yAxis).unit;
		 // generate new result for query

//		 ChartOutputUtil chartOutput = new ChartOutputUtil(finalOutput, args, executorResult.xMap);
		 /*
		  * We don't have xMap now since we use posgres
		  * ChartOutputUtil chartOutput = new ChartOutputUtil(finalOutput, args, executorResult.xMap);
		  */
		 ChartOutputUtil chartOutput = new ChartOutputUtil(finalOutput, args, HashBiMap.create());

		 // generate the corresponding distance metric
		 if (args.distance_metric.equals("Euclidean")) {
			 distance = new Euclidean();
		 }
		 else if (args.distance_metric.equals("Segmentation")){
			 distance = new SegmentationDistance();
		 }
		 else if (args.distance_metric.equals("MVIP")){
			 distance = new MVIP();
		 }
		 else {
			 distance = new DTWDistance();
		 }
		 // generate the corresponding data normalization metric
		 if (args.distanceNormalized) {
//			 normalization = new LinearNormalization();
			 normalization = new LinearNormalization();
//			 normalization = new Original();
		 }
		 else {
//			 normalization = new Zscore();
			 normalization = new LinearNormalization();
		 }
		 // generate the corresponding output normalization

		 outputNormalization = new Original();
		 // reformat database data
		 DataReformation dataReformatter = new DataReformation(normalization);
		 double[][] normalizedgroups;

		 System.out.println("Before Methods");
		 // generate the corresponding analysis method
		 if (method.equals("Outlier")) {
			 normalizedgroups = dataReformatter.reformatData(output);
			 Clustering cluster = new KMeans(distance, normalization, args);
			 analysis = new Outlier(chartOutput,new Euclidean(),normalization,cluster,args);
		 }
		 else if (method.equals("RepresentativeTrends")) {
			 normalizedgroups = dataReformatter.reformatData(output);
			 Clustering cluster = new KMeans(distance, normalization, args);
			 analysis = new Representative(chartOutput,new Euclidean(),normalization,cluster,args);
		 }
		 else if (method.equals("SimilaritySearch")) {
			 //paa = new PiecewiseAggregation(normalization, args, inMemoryDatabase); // O(1)

			 if (args.considerRange) {
				 double[][][] overlappedDataAndQueries = dataReformatter.getOverlappedData(output, args); // O(V*P)
				 normalizedgroups = overlappedDataAndQueries[0];
				 double[][] overlappedQuery = overlappedDataAndQueries[1];
				 analysis = new Similarity(chartOutput,distance,normalization,args,dataReformatter, overlappedQuery);
			 }
			 else {
				 normalizedgroups = dataReformatter.reformatData(output);
				 double[] interpolatedQuery = dataReformatter.getInterpolatedData(args.dataX, args.dataY, args.xRange, normalizedgroups[0].length); // O(P)
				 analysis = new Similarity(chartOutput,distance,normalization,paa,args,dataReformatter, interpolatedQuery);
			 }

			 ((Similarity) analysis).setDescending(false);
		 }
		 else { //(method.equals("DissimilaritySearch"))
			 //paa = new PiecewiseAggregation(normalization, args, inMemoryDatabase);

			 if (args.considerRange) {
				 double[][][] overlappedDataAndQueries = dataReformatter.getOverlappedData(output, args);
				 normalizedgroups = overlappedDataAndQueries[0];
				 double[][] overlappedQuery = overlappedDataAndQueries[1];
				 analysis = new Similarity(chartOutput,distance,normalization,args,dataReformatter, overlappedQuery);
			 }
			 else {
				 normalizedgroups = dataReformatter.reformatData(output);
				 double[] interpolatedQuery = dataReformatter.getInterpolatedData(args.dataX, args.dataY, args.xRange, normalizedgroups[0].length);
				 analysis = new Similarity(chartOutput,distance,normalization,paa,args,dataReformatter, interpolatedQuery);
			 }
			 ((Similarity) analysis).setDescending(true);
		 }
		 System.out.println("After Interpolation and normalization");

		 analysis.compute(output, normalizedgroups, args);
		 System.out.println("After Distance calulations");

		 ObjectMapper mapper = new ObjectMapper();
		 System.out.println("After Interpolation and normalization");
		 String res = mapper.writeValueAsString(analysis.getChartOutput().finalOutput);
		 System.out.println("After mapping to output string");
		 return res;
	}


	/**
	 * @param query
	 * @return
	 * @throws IOException
	 * @throws JsonMappingException
	 * @throws JsonParseException
	 * @throws InterruptedException
	 */
//	public String getBaselineData(String query) throws JsonParseException, JsonMappingException, IOException, InterruptedException {
//		BaselineQuery bq = new ObjectMapper().readValue(query, BaselineQuery.class);
//		if (!bq.equals(cachedQuery)) {
//			List<LinkedHashMap<String, LinkedHashMap<Float, Float>>> output = new ArrayList<LinkedHashMap<String, LinkedHashMap<Float, Float>>>();
//			for (int i = 0; i < bq.yAxis.size(); i++) {
//				Query q = new Query("query").setGrouby(bq.zAxis + "," + bq.xAxis).setAggregationFunc(bq.aggrFunc)
//						.setAggregationVaribale(bq.yAxis.get(i));
//				setBaselineFilter(q, bq);
//				ExecutorResult executorResult = executor.getData(q);
//				if (executorResult == null)
//					return "";
//
//				output.add(executorResult.output);
//			}
//			Result finalOutput = new Result();
//			finalOutput.method = "Basic search";
//			ChartOutputUtil chartOutput = new ChartOutputUtil(finalOutput, null, null);
//			chartOutput.baselineOutput(output, bq, finalOutput);
//			cachedResult = finalOutput;
//		}
//		Result response = new Result(cachedResult, bq.pageNum);
//		ObjectMapper mapper = new ObjectMapper();
//		return mapper.writeValueAsString(response);
//	}

	public String outlier(String method,String sql,String outliercount) throws IOException{
		return readFile();
	}
	
	LinkedHashMap<String, LinkedHashMap<Float, Float>> cleanUpDataWithAllZeros(LinkedHashMap<String, LinkedHashMap<Float, Float>> output) {
		List<String> toRemove = new ArrayList<String>();
		for (String s : output.keySet()) {
			LinkedHashMap<Float, Float> v = output.get(s);
			int flag = 1;
			for (Float f : v.keySet()) {
				if (v.get(f) != 0) {
					flag = 0;
					break;
				}
			}
			if (flag == 1) {
				toRemove.add(s);
			}
		}
		for (String s: toRemove) {
			output.remove(s);
		}
		return output;
	}


//	public String getDatabaseNames() throws JsonGenerationException, JsonMappingException, IOException{
//		return new ObjectMapper().writeValueAsString(inMemoryDatabases.keySet());
//	}


	public String getInterfaceFomData(String query) throws IOException, InterruptedException, SQLException{
		FormQuery fq = new ObjectMapper().readValue(query,FormQuery.class);
		this.databaseName = fq.getDatabasename();
		//inMemoryDatabase = inMemoryDatabases.get(this.databaseName);
		String locations[] = sqlQueryExecutor.getMetaFileLocation(databaseName);
				//System.out.println(locations[0]+"\n"+locations[1]);
		inMemoryDatabase = new Database(this.databaseName, locations[0], locations[1], false);
		//executor = new Executor(inMemoryDatabase);
		

		buffer = new ObjectMapper().writeValueAsString(inMemoryDatabase.getFormMetdaData());
		System.out.println(buffer);
//		System.out.println( new ObjectMapper().writeValueAsString(inMemoryDatabases.get(fq.getDatabasename()).getFormMetdaData()) );
		return buffer;
}



	/**
	 * @param q
	 * @param arg
	 */
	public void setFilter(Query q, ZvQuery arg) {
		if (arg.predicateValue.equals("")) return;
		Query.Filter filter = new Query.FilterPredicate(arg.predicateColumn,Query.FilterOperator.fromString(arg.predicateOperator),arg.predicateValue);
		q.setFilter(filter);
	}

	public void setBaselineFilter(Query q, BaselineQuery bq) {
		if (bq.predicateValue.equals("")) return;
		Query.Filter filter = new Query.FilterPredicate(bq.predicateColumn, Query.FilterOperator.fromString(bq.predicateOperator), bq.predicateValue);
		q.setFilter(filter);
	}

	public String readFile() throws IOException {
	    BufferedReader br = new BufferedReader(new FileReader("/src/data1.txt"));
	    try {
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();
	        while (line != null) {
	            sb.append(line);
	            line = br.readLine();
	        }
	        return sb.toString();
	    } finally {
	        br.close();
	    }
	}

}
