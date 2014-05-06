package org.patricbrc.circos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

public class CircosGenerator {

	private static final Logger logger = LoggerFactory.getLogger(CircosGenerator.class);

	private final String DIR_CONFIG = "/circos_configs";

	private final String DIR_DATA = "/circos_data";

	int imageSize = 1000;

	boolean includeOuterTrack = false;

	float trackWidth = 0.03f;

	String filePlotTypes = "tiles";

	String gcContentPlotType = null;

	String gcSkewPlotType = null;

	public String createCircosImage(Map<?, ?> parameters) {
		if (parameters.isEmpty()) {
			logger.error("Circos image could not be created");
			return null;
		}
		else {
			String uuid = null;
			// FileUtils.rm_rf(Dir.glob('circos_data/*'))

			// Record whether to include GC content track or not
			if (parameters.containsKey("gc_content_plot_type")) {
				gcContentPlotType = parameters.get("gc_content_plot_type").toString();
			}
			if (parameters.containsKey("gc_skew_plot_type")) {
				gcSkewPlotType = parameters.get("gc_skew_plot_type").toString();
			}

			// Record whether to include outer track or not
			includeOuterTrack = (parameters.get("include_outer_track").equals("on"));

			// Store image size parameter from form
			if (parameters.containsKey("image_dimensions") && parameters.get("image_dimensions").equals("") == false) {
				imageSize = Integer.parseInt(parameters.get("image_dimensions").toString());
			}

			// Convert track width parameter to percentage and store it
			if (parameters.containsKey("track_width")) {
				trackWidth = (float) (Integer.parseInt(parameters.get("track_width").toString()) / 100.0);
			}

			// Collect genome data using Solr API for PATRIC
			JSONObject genomeData = getGenomeData(parameters);

			if (genomeData == null || genomeData.isEmpty()) {
				logger.error("No genome data found for GID {}", parameters.get("gid"));
				return null;
			}

			// Run 'uuidgen' command to create a random string to name temp directory
			// for the circos image and data files
			uuid = UUID.randomUUID().toString().replace("-", "");

			String folderName = parameters.get("realpath") + "/images/" + uuid;
			logger.info("folderName: {}", folderName);
			// Create temp directory for this image's data
			try {
				Files.createDirectory(Paths.get(folderName));
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			// Create data and config files for Circos
			createCircosDataFiles(folderName, parameters, genomeData);
			createCircosConfigFiles(folderName, genomeData);

			// Create directory to store final image
			// try {
			// Files.createDirectory(Paths.get("public/images/" + uuid));
			// }
			// catch (IOException e) {
			// e.printStackTrace();
			// }

			logger.info("Starting Circos script...");

			// Run Circos script to generate final image
			// `circos -conf #{folder_name}/circos_configs/circos.conf -debug_group summary,timer > circos.log.out`
			String command = "circos -conf " + folderName + DIR_CONFIG + "/circos.conf -debug_group summary,timer";
			try {
				logger.info("running command: " + command);
				Process p = Runtime.getRuntime().exec(command);
				p.waitFor();
				logger.info(IOUtils.toString(p.getInputStream()));
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			logger.info("Circos image was created successfully!");

			return uuid;
		}
	}

	@SuppressWarnings("unchecked")
	private JSONObject getGenomeData(Map<?, ?> parameters) {
		// Hash for query strings of each feature type. Lookup is based on the
		// first half of the parameter's name,
		// e.g. cds_forward's lookup is "cds"
		Map<String, String> featureTypes = new HashMap<String, String>();
		featureTypes.put("cds", "feature_type:CDS");
		featureTypes.put("rna", "feature_type:*RNA");
		featureTypes.put("misc", "!(feature_type:*RNA OR feature_type:CDS)");

		// Extract genome ID from form data
		String gid = parameters.get("gid").toString();

		// Build base URL
		SolrServer solrServer = new HttpSolrServer("http://macleod.vbi.vt.edu:8983/solr/dnafeature");

		// Hash to store JSON response data from Solr
		JSONObject genomeData = new JSONObject();

		List<String> parameterNames = new ArrayList<String>();
		parameterNames.addAll(Arrays
				.asList(new String[] { "cds_forward", "cds_reverse", "rna_forward", "rna_reverse", "misc_forward", "misc_reverse" }));

		// Iterate over each checked off data type
		Iterator<String> paramKeys = (Iterator<String>) parameters.keySet().iterator();
		while (paramKeys.hasNext()) {
			String parameter = paramKeys.next();

			// Skip over parameters that aren't track types
			if (parameterNames.contains(parameter) == false) {
				continue;
			}

			logger.info("Getting {} data", parameter);

			// Build query string based on user's input
			String featureType = featureTypes.get(parameter.split("_")[0]);
			String strand = parameter.split("_")[1].equals("forward") ? "+" : "-";
			SolrQuery queryData = new SolrQuery();
			queryData.setQuery("gid:" + gid + " AND strand:\"" + strand + "\"");
			queryData.setFilterQueries("annotation_f:PATRIC AND " + featureType);
			queryData.setFields("accession, start_max, end_min");
			queryData.setSort("accession", SolrQuery.ORDER.asc);
			queryData.setSort("start_max", SolrQuery.ORDER.asc);
			queryData.setRows(10000);

			// Encode the query as a URL and parse the JSON response into a Ruby
			// Hash object
			// Pull out only the gene data from the JSON response
			try {
				logger.info("Requesting feature data from URL {}", queryData.getQuery());
				JSONArray docs = new JSONArray();
				QueryResponse qr = solrServer.query(queryData, SolrRequest.METHOD.POST);
				SolrDocumentList sdl = qr.getResults();
				for (SolrDocument sd : sdl) {
					JSONObject doc = new JSONObject();
					doc.put("accession", sd.get("accession"));
					doc.put("start_max", sd.get("start_max"));
					doc.put("end_min", sd.get("end_min"));
					docs.add(doc);
				}
				genomeData.put(parameter, docs);
			}
			catch (SolrServerException e) {
				e.printStackTrace();
			}
		}

		// Create a set of all the entered custom track numbers
		HashSet<Integer> trackNums = new HashSet<Integer>();
		paramKeys = (Iterator<String>) parameters.keySet().iterator();
		while (paramKeys.hasNext()) {
			String key = paramKeys.next();
			if (key.matches(".*_(\\d+)$")) {
				int num = Integer.parseInt(key.substring(key.lastIndexOf("_") + 1));
				logger.info("{} matches {}", key, num);
				trackNums.add(num);
			}
		}
		// parameters.keys.select{ |e| /custom_track_.*/.match e }.each { |parameter| track_nums << parameter[/.*_(\d+)$/, 1] }

		// Gather data for each custom track
		for (Integer trackNum : trackNums) {
			String customTrackName = "custom_track_" + trackNum;
			String featureType = parameters.get("custom_track_type_" + trackNum).toString();

			String strand;
			switch (parameters.get("custom_track_strand_" + trackNum).toString()) {
			case "forward":
				strand = " AND strand:\"+\"";
				break;
			case "reverse":
				strand = " AND strand:\"-\"";
				break;
			default:
				strand = "";
				break;
			}

			String keywords = parameters.get("custom_track_keyword_" + trackNum).toString();
			if (keywords.isEmpty() == false) {
				keywords = " AND " + keywords;
			}

			SolrQuery queryData = new SolrQuery();
			queryData.setQuery("gid:" + gid + strand + keywords);
			queryData.setFilterQueries("annotation_f:PATRIC AND " + featureType);
			queryData.setSort("accession", SolrQuery.ORDER.asc);
			queryData.setSort("start_max", SolrQuery.ORDER.asc);
			queryData.setRows(10000);

			try {
				logger.info("Requesting feature data from URL {}", queryData.getQuery());
				JSONArray docs = new JSONArray();
				QueryResponse qr = solrServer.query(queryData, SolrRequest.METHOD.POST);
				SolrDocumentList sdl = qr.getResults();
				for (SolrDocument sd : sdl) {
					JSONObject doc = new JSONObject();
					// doc.putAll(sd.getFieldValueMap());
					doc.put("accession", sd.get("accession"));
					doc.put("start_max", sd.get("start_max"));
					doc.put("end_min", sd.get("end_min"));
					docs.add(doc);
				}
				genomeData.put(customTrackName, docs);
			}
			catch (SolrServerException e) {
				e.printStackTrace();
			}
		}

		return genomeData;
	}

	private boolean createCircosDataFiles(String folderName, Map<?, ?> parameters, JSONObject genomeData) {

		// Create folder for all data files
		try {
			Files.createDirectory(Paths.get(folderName + DIR_DATA));
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		String genome = null;
		JSONArray accessions = new JSONArray();
		Iterator<?> iter = genomeData.keySet().iterator();
		while (iter.hasNext()) {
			// genome_data.each do |feature_type,feature_data| // TODO: review
			String featureType = (String) iter.next();
			JSONArray featureData = (JSONArray) genomeData.get(featureType);

			// Create a Circos data file for each selected feature
			logger.info("Writing data file for feature, {}", featureType);

			// File name has the following format: feature.strand.txt
			// e.g. cds.forward.txt, rna.reverse.txt
			String fileName = featureType.replace("_", ".") + ".txt";
			File f = new File(folderName + DIR_DATA + "/" + fileName);
			f.setWritable(true);
			try {
				PrintWriter fWriter = new PrintWriter(f);
				for (Object obj : featureData) {
					JSONObject gene = (JSONObject) obj;
					// genome = gene.get("genome_name").toString();
					fWriter.format("%s\t%d\t%d\n", gene.get("accession"), gene.get("start_max"), gene.get("end_min"));
				}
				fWriter.close();
			}
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}

		SolrServer solrServer = new HttpSolrServer("http://macleod.vbi.vt.edu:8983/solr/sequenceinfo");
		SolrQuery queryData = new SolrQuery();
		queryData.setQuery("gid:" + parameters.get("gid"));
		queryData.setFields("genome_name, accession, length, sequence");
		queryData.setSort("accession", SolrQuery.ORDER.asc);

		logger.info("Requesting accession data from URL: {}", queryData.getQuery());
		QueryResponse qr;
		try {
			qr = solrServer.query(queryData, SolrRequest.METHOD.POST);
			SolrDocumentList sdl = qr.getResults();
			for (SolrDocument sd : sdl) {
				JSONObject doc = new JSONObject();
				if (genome == null) {
					genome = sd.get("genome_name").toString();
				}
				doc.put("accession", sd.get("accession"));
				doc.put("length", sd.get("length"));
				doc.put("sequence", sd.get("sequence"));
				accessions.add(doc);
			}
		}
		catch (SolrServerException e) {
			e.printStackTrace();
		}

		HashMap<String, String> accessionSequenceData = new HashMap<String, String>();

		// Write karyotype file
		logger.info("Creating karyotype file for genome,{}", genome);
		try {
			File file = new File(folderName + DIR_DATA + "/karyotype.txt");
			file.setWritable(true);
			PrintWriter writer = new PrintWriter(file);
			for (Object obj : accessions) {
				JSONObject accession = (JSONObject) obj;

				writer.format("chr\t-\t %s\t %s\t 0\t %d\t grey\n", accession.get("accession"), genome.replace(" ", "_"), accession.get("length"));
				if (gcContentPlotType != null || gcSkewPlotType != null) {
					accessionSequenceData.put(accession.get("accession").toString(), accession.get("sequence").toString());
				}
			}
			writer.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		// Default window size for GC calculations
		int windowSize = 2000;

		// Create GC content data file
		if (gcContentPlotType != null) {
			logger.info("Creating data file for GC content");

			// accession_sequence_data.each do |accession,sequence|
			iter = accessionSequenceData.keySet().iterator();
			while (iter.hasNext()) {
				String accession = (String) iter.next();
				String sequence = accessionSequenceData.get(accession);
				int totalSeqLength = sequence.length();
				HashMap<String, Float> gcContentValues = new HashMap<String, Float>();

				// Iterate over each window_size-sized block and calculate its GC
				// content.
				// For instance, if the sequence length were 1,234,567 and the
				// window size were 1000, we would iterate 1234 times, with the
				// last iteration being the window from 1,234,001 to 1,234,566
				for (int i = 0; i < (totalSeqLength / windowSize); i++) {

					// Only use 0 as start index for first iteration, otherwise
					// with a window_size of 1000, start should be something like
					// 1001, 2001, and so on.
					int startIndex = (i == 0) ? 0 : (i * windowSize + 1);

					// End index should either be 'window_size' greater than the start or
					// if we are at the last iteration, the end of the sequence.
					int endIndex = Math.min((i + 1) * windowSize, totalSeqLength - 1);

					// Store number of 'g' and 'c' characters from the sequence
					Pattern pattern = Pattern.compile("[gcGC]");
					Matcher matcher = pattern.matcher(sequence.subSequence(startIndex, endIndex));
					int gcCount;
					for (gcCount = 0; matcher.find(); gcCount++)
						;
					float gcPercentage = (gcCount / (float) windowSize);

					// Store percentage in gc_content_values hash as value with the
					// range from the start index to the end index as the key
					gcContentValues.put(startIndex + ".." + endIndex, gcPercentage); // .round(5)
				}

				// Write GC content data for this accession
				try {
					File file = new File(folderName + DIR_DATA + "/gc.content.txt");
					file.setWritable(true);
					PrintWriter writer = new PrintWriter(file);
					iter = gcContentValues.keySet().iterator();
					while (iter.hasNext()) {
						String range = (String) iter.next();
						String strIndex = range.split("\\.\\.")[0];
						String endIndex = range.split("\\.\\.")[1];
						float percentage = gcContentValues.get(range);
						// logger.info("{}, {}, {}", accession, strIndex, endIndex);
						writer.format("%s\t%s\t%s\t%f\n", accession, strIndex, endIndex, percentage);
					}
					writer.close();
				}
				catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				genomeData.put("gc_content", true);
			}
		}

		// Create GC skew data file
		if (gcSkewPlotType != null) {
			logger.info("Creating data file for GC skew");
			iter = accessionSequenceData.keySet().iterator();
			while (iter.hasNext()) {
				String accession = (String) iter.next();
				String sequence = accessionSequenceData.get(accession);
				int totalSeqLength = sequence.length();
				HashMap<String, Float> gcSkewValues = new HashMap<String, Float>();

				for (int i = 0; i < (totalSeqLength / windowSize); i++) {
					int startIndex = (i == 0) ? 0 : (i * windowSize + 1);
					int endIndex = Math.min((i + 1) * windowSize, totalSeqLength - 1);

					Pattern ptrnGContent = Pattern.compile("[gG]");
					Pattern ptrnCContent = Pattern.compile("[cC]");
					Matcher mtchrG = ptrnGContent.matcher(sequence.subSequence(startIndex, endIndex));
					Matcher mtchrC = ptrnCContent.matcher(sequence.subSequence(startIndex, endIndex));

					int gCount, cCount;
					for (gCount = 0; mtchrG.find(); gCount++)
						;
					for (cCount = 0; mtchrC.find(); cCount++)
						;
					float gcSkew = (float) (gCount - cCount) / (gCount + cCount);
					gcSkewValues.put(startIndex + ".." + endIndex, gcSkew);
				}

				// Write GC skew data for this accession
				try {
					File file = new File(folderName + DIR_DATA + "/gc.skew.txt");
					file.setWritable(true);
					PrintWriter writer = new PrintWriter(file);
					iter = gcSkewValues.keySet().iterator();
					while (iter.hasNext()) {
						String range = (String) iter.next();
						String strIndex = range.split("\\.\\.")[0];
						String endIndex = range.split("\\.\\.")[1];
						float skew = gcSkewValues.get(range);
						writer.format("%s\t%s\t%s\t%f\n", accession, strIndex, endIndex, skew);
					}
					writer.close();
				}
				catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				genomeData.put("gc_skew", true);
			}
		}
		// Write "large tiles" file
		logger.info("Creating large tiles file for genome, {}", genome);
		try {
			File file = new File(folderName + DIR_DATA + "/large.tiles.txt");
			file.setWritable(true);
			PrintWriter writer = new PrintWriter(file);
			for (Object obj : accessions) {
				JSONObject accession = (JSONObject) obj;

				writer.format("%s\t0\t%d\n", accession.get("accession"), accession.get("length"));
			}
			writer.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		// Move uploaded data files to the new image's 'circos_data' directory

		// unless parameters[:file_chooser].nil?
		// uploaded_file_name_list = parameters[:file_chooser].map { |e| e[:tempfile] }
		//
		// # Get file plot type parameters from web form and convert the symbols
		// # to strings
		// $file_plot_types = parameters.select { |k,v| k.include? "file_plot_type" }.map { |k,v| v }
		//
		// unless uploaded_file_name_list.nil?
		// uploaded_file_name_list.each_with_index do |upload_tempfile,i|
		// File.open("#{folder_name}/circos_data/user.upload.#{i}.txt", "w") do |file|
		// first_line = upload_tempfile.readline
		// file_plot_type = $file_plot_types[i]
		//
		// if file_plot_type == 'tile'
		// next if first_line.split(/\s+/).size != 3
		// else
		// next if first_line.split(/\s+/).size != 4
		// end
		//
		// # Read from the server's temporary version of the file and write
		// # it to a data file in the new image's directory
		// upload_tempfile.rewind
		// tempfile_contents = upload_tempfile.read
		//
		// file.write(tempfile_contents)
		//
		// # Add custom track to genome data so that it will be plotted
		// genome_data["user_upload_#{i}"] = true
		// end
		// end
		// end
		// end

		return true;
	}

	private void createCircosConfigFiles(String folderName, JSONObject genomeData) {
		List<String> colors = new LinkedList<String>();
		colors.addAll(Arrays.asList(new String[] { "vdblue", "vdgreen", "lgreen", "vdred", "lred", "vdpurple", "lpurple", "vdorange", "lorange",
				"vdyellow", "lyellow" }));

		// Create folder for config files
		try {
			Files.createDirectory(Paths.get(folderName + DIR_CONFIG));
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		// Copy static conf files to image's temp directory
		try {
			Files.copy(Paths.get("conf_templates/ideogram.conf"), Paths.get(folderName + DIR_CONFIG + "/ideogram.conf"));
			Files.copy(Paths.get("conf_templates/ticks.conf"), Paths.get(folderName + DIR_CONFIG + "/ticks.conf"));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		logger.info("Writing config file for plots");

		// Open final plot configuration file for creation
		try {
			// File.open("#{folder_name}/circos_configs/plots.conf", "w+") do |file|
			File file = new File(folderName + DIR_CONFIG + "/plots.conf");
			file.setWritable(true);
			PrintWriter writer = new PrintWriter(file);

			ArrayList<HashMap<String, String>> tilePlots = new ArrayList<HashMap<String, String>>();
			float currentRadius = 1.0f;
			float trackThickness = imageSize * trackWidth;

			// Build hash for large tile data because it is not included in the
			// genomic data

			if (includeOuterTrack) {
				HashMap<String, String> largeTileData = new HashMap<String, String>();
				largeTileData.put("file", folderName + DIR_DATA + "/large.tiles.txt");
				largeTileData.put("thickness", Float.toString((trackThickness / 2)) + "p");
				largeTileData.put("type", "tile");
				largeTileData.put("color", colors.get(0));
				colors = colors.subList(1, colors.size() - 1); // colors.shift
				largeTileData.put("r1", Float.toString(currentRadius) + "r");
				largeTileData.put("r0", Float.toString((currentRadius -= 0.02)) + "r");
				// tileplots << largeTileData; // TODO: review this
				tilePlots.add(largeTileData);
			}
			else {
				// colors.shift;
				colors = colors.subList(1, colors.size() - 1);
			}

			// Space in between tracks
			float trackBuffer = trackWidth - 0.03f;

			ArrayList<HashMap<String, String>> nonTilePlots = new ArrayList<HashMap<String, String>>();

			// Build hash of plot data for Mustache to render
			Iterator<String> keys = genomeData.keySet().iterator();
			while (keys.hasNext()) {
				String featureType = keys.next();
				HashMap<String, String> plotData = new HashMap<String, String>();

				// Handle user uploaded files
				if (featureType.contains("user_upload")) {
					// int userUploadNumber = Integer.parseInt(featureType.substring(featureType.lastIndexOf("_")));
					// plotType = filePlotTypes.get
					// TODO: need to implement from here
				}
				else if (featureType.contains("gc")) { // gc_content or gc_skew
					String plotType;
					if (featureType.equals("gc_content")) {
						plotType = this.gcContentPlotType;
					}
					else {
						plotType = this.gcSkewPlotType;
					}
					if (plotType.equals("heatmap")) {
						plotData.put("file", folderName + DIR_DATA + "/" + featureType.replace("_", ".") + ".txt");
						plotData.put("thickness", Float.toString(trackThickness) + "p");
						plotData.put("type", plotType);
						plotData.put("color", "rdbu-10-div");
						float r1 = (currentRadius -= (0.01 + trackBuffer));
						float r0 = (currentRadius -= (0.04 + trackBuffer));
						plotData.put("r1", Float.toString(r1) + "r");
						plotData.put("r0", Float.toString(r0) + "r");

						tilePlots.add(plotData);
					}
					else {
						plotData.put("file", folderName + DIR_DATA + "/" + featureType.replace("_", ".") + ".txt");
						plotData.put("type", plotType);
						plotData.put("color", colors.get(0));
						colors = colors.subList(1, colors.size() - 1);
						float r1 = (currentRadius -= (0.01 + trackBuffer));
						float r0 = (currentRadius -= (0.10 + trackBuffer));
						plotData.put("r1", Float.toString(r1) + "r");
						plotData.put("r0", Float.toString(r0) + "r");
						plotData.put("min", (featureType.equals("gc_skew") ? "-1.0" : "0.0"));
						plotData.put("max", "1.0");
						if (plotType.equals("histogram")) {
							plotData.put("extendbin", "extend_bin = no");
						}
						else {
							plotData.put("extendbin", "");
						}
						// plotData.put("plotbgcolor", value);
						plotData.put("plotbgcolor", "white"); // temporary value

						nonTilePlots.add(plotData);
					}
				}
				else {
					// handle default/custom tracks
					plotData.put("file", folderName + DIR_DATA + "/" + featureType.replace("_", ".") + ".txt");
					plotData.put("thickness", Float.toString(trackThickness) + "p");
					plotData.put("type", "tile");
					plotData.put("color", colors.get(0));
					colors = colors.subList(1, colors.size() - 1); // color.shift
					float r1 = (currentRadius -= (0.01 + trackBuffer));
					float r0 = (currentRadius -= (0.04 + trackBuffer));
					plotData.put("r1", Float.toString(r1) + "r");
					plotData.put("r0", Float.toString(r0) + "r");
					tilePlots.add(plotData);
				}
			}

			// Render plots config file using template
			// file.write(Mustache.render(File.read("conf_templates/plots.mu"), :tileplots => tileplots, :nontileplots => nontileplots))
			Template tmpl = Mustache.compiler().compile(new FileReader("conf_templates/plots.mu"));
			HashMap<String, ArrayList<HashMap<String, String>>> data = new HashMap<String, ArrayList<HashMap<String, String>>>();
			data.put("tileplots", tilePlots);
			data.put("nontileplots", nonTilePlots);
			tmpl.execute(data, writer);
			writer.close();
		}
		catch (IOException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}

		// Split out the UUID from the folder name to obfuscate the final image's
		// path as well
		// String imageId = folderName.substring(folderName.lastIndexOf("/"));
		// logger.info("Writing config file for image {}", imageId);

		// Open final image configuration file for creation
		try {
			File file = new File(folderName + DIR_CONFIG + "/image.conf");
			file.setWritable(true);
			PrintWriter writer = new PrintWriter(file);

			Template tmpl = Mustache.compiler().compile(new BufferedReader(new FileReader("conf_templates/image.mu")));
			Map<String, String> data = new HashMap<String, String>();
			data.put("path", folderName);
			data.put("image_size", Integer.toString(imageSize));
			tmpl.execute(data, writer);
			writer.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		logger.info("Writing main config file for Circos");

		// Open final circos configuration file for creation
		try {
			File file = new File(folderName + DIR_CONFIG + "/circos.conf");
			file.setWritable(true);
			PrintWriter writer = new PrintWriter(file);

			Template tmpl = Mustache.compiler().compile(new BufferedReader(new FileReader("conf_templates/circos.mu")));
			Map<String, String> data = new HashMap<String, String>();
			data.put("folder", folderName);
			// Render main circos config file using template
			tmpl.execute(data, writer);
			writer.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}