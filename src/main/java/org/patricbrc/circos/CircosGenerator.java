package org.patricbrc.circos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

public class CircosGenerator {

	private static final Logger logger = LoggerFactory.getLogger(CircosGenerator.class);

	private final String DIR_CONFIG = "/conf";

	private final String DIR_DATA = "/data";

	private String appDir;

	private Template tmplPlotConf;

	private Template tmplImageConf;

	private Template tmplCircosConf;

	CircosData circosData;

	public CircosGenerator(String path) {
		appDir = path;
		circosData = new CircosData();
		try {
			tmplPlotConf = Mustache.compiler().compile(new BufferedReader(new FileReader(path + "/conf_templates/plots.mu")));
			tmplImageConf = Mustache.compiler().compile(new BufferedReader(new FileReader(path + "/conf_templates/image.mu")));
			tmplCircosConf = Mustache.compiler().compile(new BufferedReader(new FileReader(path + "/conf_templates/circos.mu")));
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public String createCircosImage(Map<String, Object> parameters) {
		if (parameters.isEmpty()) {
			logger.error("Circos image could not be created");
			return null;
		}
		else {
			// create instance
			Circos circos = new Circos(appDir);
			circos.setGenomeId(parameters.get("gid").toString());

			// Record whether to include GC content track or not
			if (parameters.containsKey("gc_content_plot_type")) {
				circos.setGcContentPlotType(parameters.get("gc_content_plot_type").toString());
			}
			if (parameters.containsKey("gc_skew_plot_type")) {
				circos.setGcSkewPlotType(parameters.get("gc_skew_plot_type").toString());
			}

			// Record whether to include outer track or not
			if (parameters.containsKey("include_outer_track")) {
				circos.setIncludeOuterTrack(parameters.get("include_outer_track").equals("on"));
			}

			// Store image size parameter from form
			if (parameters.containsKey("image_dimensions") && parameters.get("image_dimensions").equals("") == false) {
				circos.setImageSize(Integer.parseInt(parameters.get("image_dimensions").toString()));
			}

			// Convert track width parameter to percentage and store it
			if (parameters.containsKey("track_width")) {
				circos.setTrackWidth((float) (Integer.parseInt(parameters.get("track_width").toString()) / 100.0));
			}

			// Collect genome data using Solr API for PATRIC
			circos.setGenomeData(this.getGenomeData(parameters));

			// Create temp directory for this image's data
			String tmpFolderName = circos.getTmpDir();
			try {
				Files.createDirectory(Paths.get(tmpFolderName));
			}
			catch (IOException e) {
				e.printStackTrace();
				return null;
			}

			// Create data and config files for Circos
			createCircosDataFiles(parameters, circos);
			createCircosConfigFiles(circos);

			// Run Circos script to generate final image
			// `circos -conf #{folder_name}/circos_configs/circos.conf -debug_group summary,timer > circos.log.out`
			String command = "circos -conf " + tmpFolderName + DIR_CONFIG + "/circos.conf -debug_group summary,timer";
			try {
				logger.info("Starting Circos script: " + command);
				Process p = Runtime.getRuntime().exec(command);
				p.waitFor();
				logger.info(IOUtils.toString(p.getInputStream()));
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			return circos.getUuid();
		}
	}

	private Map<String, List<Map<String, Object>>> getGenomeData(Map<String, Object> parameters) {
		String gId = parameters.get("gid").toString();
		List<String> defaultDataTracks = new ArrayList<>();
		defaultDataTracks.addAll(Arrays.asList(new String[] { "cds_forward", "cds_reverse", "rna_forward", "rna_reverse", "misc_forward",
				"misc_reverse" }));
		Map<String, List<Map<String, Object>>> genomeData = new LinkedHashMap<>();

		// Iterate over each checked off data type
		Iterator<String> paramKeys = (Iterator<String>) parameters.keySet().iterator();
		while (paramKeys.hasNext()) {
			String parameter = paramKeys.next();
			// Skip over parameters that aren't track types
			if (defaultDataTracks.contains(parameter) == false) {
				continue;
			}
			// Build query string based on user's input
			String featureType = parameter.split("_")[0];
			String strand = parameter.split("_")[1].equals("forward") ? "+" : "-";

			genomeData.put(parameter, circosData.getFeatures(gId, featureType, strand, null));
		}

		// Create a set of all the entered custom track numbers
		// parameters.keys.select{ |e| /custom_track_.*/.match e }.each { |parameter| track_nums << parameter[/.*_(\d+)$/, 1] }
		Set<Integer> trackNums = new HashSet<>();
		paramKeys = (Iterator<String>) parameters.keySet().iterator();
		while (paramKeys.hasNext()) {
			String key = paramKeys.next();
			if (key.matches("custom_track_.*_(\\d+)$")) {
				int num = Integer.parseInt(key.substring(key.lastIndexOf("_") + 1));
				logger.info("{} matches {}", key, num);
				trackNums.add(num);
			}
		}

		// Gather data for each custom track
		for (Integer trackNum : trackNums) {
			String customTrackName = "custom_track_" + trackNum;
			String featureType = parameters.get("custom_track_type_" + trackNum).toString();
			String paramStrand = parameters.get("custom_track_strand_" + trackNum).toString();
			String strand;
			switch (paramStrand) {
			case "forward":
				strand = "+";
				break;
			case "reverse":
				strand = "-";
				break;
			default:
				strand = null;
			}
			String keywords = null;
			if (parameters.containsKey("custom_track_keyword_" + trackNum)) {
				keywords = parameters.get("custom_track_keyword_" + trackNum).toString();
			}
			genomeData.put(customTrackName, circosData.getFeatures(gId, featureType, strand, keywords));
		}
		return genomeData;
	}

	private void createCircosDataFiles(Map<String, Object> parameters, Circos circos) {

		// Create folder for all data files
		String dirData = circos.getTmpDir() + DIR_DATA;
		try {
			Files.createDirectory(Paths.get(dirData));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		Map<String, List<Map<String, Object>>> genomeData = circos.getGenomeData();
		Iterator<String> iter = genomeData.keySet().iterator();
		while (iter.hasNext()) {
			String track = iter.next();
			List<Map<String, Object>> featureData = genomeData.get(track);

			// Create a Circos data file for each selected feature
			logger.info("Writing data file for track, {}", track);

			// File name has the following format: feature.strand.txt e.g. cds.forward.txt, rna.reverse.txt
			String fileName = "/" + track.replace("_", ".") + ".txt";
			try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(dirData + fileName)))) {
				for (Map<String, Object> gene : featureData) {
					writer.format("%s\t%d\t%d\tid=%d\n", gene.get("accession"), gene.get("start_max"), gene.get("end_min"), gene.get("na_feature_id"));
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		String genome = circosData.getGenomeName(circos.getGenomeId());

		List<Map<String, Object>> accessions = circosData.getAccessions(circos.getGenomeId());

		// Write karyotype file
		logger.info("Creating karyotype file for genome,{}", genome);
		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(dirData + "/karyotype.txt")))) {
			for (Map<String, Object> accession : accessions) {
				writer.format("chr\t-\t %s\t %s\t 0\t %d\t grey\n", accession.get("accession"), genome.replace(" ", "_"), accession.get("length"));
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		// Default window size for GC calculations
		int defaultWindowSize = 2000;

		// Create GC content data file
		if (circos.getGcContentPlotType() != null) {
			logger.info("Creating data file for GC content");

			Map<String, Float> gcContentValues = new LinkedHashMap<>();

			for (Map<String, Object> accession : accessions) {
				String accessionID = accession.get("accession").toString();
				String sequence = accession.get("sequence").toString();
				int totalSeqLength = sequence.length();

				// Iterate over each window_size-sized block and calculate its GC content.
				// For instance, if the sequence length were 1,234,567 and the window size were 1000, we would iterate 1234 times, with the last
				// iteration being the window from 1,234,001 to 1,234,566
				for (int i = 0; i < (totalSeqLength / defaultWindowSize); i++) {

					// Only use 0 as start index for first iteration, otherwise with a window_size of 1000, start should be something like 1001, 2001,
					// and so on.
					int startIndex = (i == 0) ? 0 : (i * defaultWindowSize + 1);

					// End index should either be 'window_size' greater than the start or if we are at the last iteration, the end of the sequence.
					int endIndex = Math.min((i + 1) * defaultWindowSize, totalSeqLength - 1);

					int currentWindowSize = endIndex - startIndex;

					// Store number of 'g' and 'c' characters from the sequence
					Pattern pattern = Pattern.compile("[gcGC]");
					Matcher matcher = pattern.matcher(sequence.subSequence(startIndex, endIndex));
					int gcCount;
					for (gcCount = 0; matcher.find(); gcCount++)
						;
					float gcPercentage = (gcCount / (float) currentWindowSize);

					// Store percentage in gc_content_values hash as value with the range from the start index to the end index as the key
					gcContentValues.put(accessionID + ":" + startIndex + ".." + endIndex, gcPercentage); // .round(5)
				}
			}
			// Write GC content data for this accession
			try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(dirData + "/gc.content.txt")))) {
				Iterator<String> iterGC = gcContentValues.keySet().iterator();
				while (iterGC.hasNext()) {
					String range = iterGC.next();
					String[] rangeId = range.split(":");
					String[] rangeLoc = rangeId[1].split("\\.\\.");
					// logger.info("{}, {}, {}", accession, strIndex, endIndex);
					writer.format("%s\t%s\t%s\t%f\n", rangeId[0], rangeLoc[0], rangeLoc[1], gcContentValues.get(range));
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			genomeData.put("gc_content", new ArrayList<Map<String, Object>>());
		}

		// Create GC skew data file
		if (circos.getGcSkewPlotType() != null) {
			logger.info("Creating data file for GC skew");

			Map<String, Float> gcSkewValues = new LinkedHashMap<>();
			for (Map<String, Object> accession : accessions) {
				String accessionId = accession.get("accession").toString();
				String sequence = accession.get("sequence").toString();
				int totalSeqLength = sequence.length();

				for (int i = 0; i < (totalSeqLength / defaultWindowSize); i++) {
					int startIndex = (i == 0) ? 0 : (i * defaultWindowSize + 1);
					int endIndex = Math.min((i + 1) * defaultWindowSize, totalSeqLength - 1);

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
					gcSkewValues.put(accessionId + ":" + startIndex + ".." + endIndex, gcSkew);
				}
			}
			// Write GC skew data for this accession
			try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(dirData + "/gc.skew.txt")));) {
				Iterator<String> iterGC = gcSkewValues.keySet().iterator();
				while (iterGC.hasNext()) {
					String range = iterGC.next();
					String[] rangeId = range.split(":");
					String[] rangeLoc = rangeId[1].split("\\.\\.");
					writer.format("%s\t%s\t%s\t%f\n", rangeId[0], rangeLoc[0], rangeLoc[1], gcSkewValues.get(range));
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			genomeData.put("gc_skew", new ArrayList<Map<String, Object>>());
		}
		// Write "large tiles" file
		logger.info("Creating large tiles file for genome, {}", genome);
		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(dirData + "/large.tiles.txt")))) {
			for (Map<String, Object> accession : accessions) {
				writer.format("%s\t0\t%d\n", accession.get("accession"), accession.get("length"));
			}
			writer.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		// Process upload files
		List<Map<String, Object>> fileupload = new ArrayList<Map<String, Object>>();
		Set<Integer> trackNums = new HashSet<>();
		Iterator<String> paramKeys = (Iterator<String>) parameters.keySet().iterator();
		while (paramKeys.hasNext()) {
			String key = paramKeys.next();
			if (key.matches("file_(\\d+)$")) {
				int num = Integer.parseInt(key.substring(key.lastIndexOf("_") + 1));
				FileItem item = (FileItem) parameters.get("file_" + num);
				// logger.info("{} matches, filename={}", key, item.getName().toString());
				if (item.getName().toString().equals("") == false) {
					trackNums.add(num);
				}
			}
		}
		for (Integer trackNum : trackNums) {
			if (parameters.containsKey("file_" + trackNum)) {
				FileItem item = (FileItem) parameters.get("file_" + trackNum);
				try {
					String fileName = "user.upload." + trackNum + ".txt";
					String plotType = parameters.get("file_plot_type_" + trackNum).toString();
					boolean isValid = true;

					try (BufferedReader br = new BufferedReader(new InputStreamReader(item.getInputStream()))) {
						String line;
						while ((line = br.readLine()) != null && isValid == true) {
							String[] tab = line.split("\t");
							if (plotType.equals("tile")) {
								if (tab.length == 3) {
								}
								else if (tab[3].contains("id=") == false) {
									isValid = false;
								}
								else {
									isValid = false;
								}
							}
							else if (plotType.equals("line") || plotType.equals("histogram") || plotType.equals("heatmap")) {
								try {
									Float.parseFloat(tab[3]);
								}
								catch (NumberFormatException | NullPointerException ex) {
									isValid = false;
								}
							}
							else {
								isValid = false;
							}
						}
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					if (isValid) {
						item.write(new File(dirData + "/" + fileName));
						Map<String, Object> file = new HashMap<>();
						file.put("file_name", fileName);
						file.put("plot_type", plotType);
						fileupload.add(file);
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		if (trackNums.size() > 0) {
			genomeData.put("user_upload", fileupload);
		}
	}

	private void createCircosConfigFiles(Circos circos) {
		Map<String, List<Map<String, Object>>> genomeData = circos.getGenomeData();
		List<String> colors = new LinkedList<>();
		colors.addAll(Arrays.asList(new String[] { "vdblue", "vdgreen", "lgreen", "vdred", "lred", "vdpurple", "lpurple", "vdorange", "lorange",
				"vdyellow", "lyellow" }));
		String gId = circos.getGenomeId();
		String dataDir = circos.getTmpDir() + DIR_DATA;
		String confDir = circos.getTmpDir() + DIR_CONFIG;

		// Create folder for config files
		// Copy static conf files to temp directory
		try {
			Files.createDirectory(Paths.get(confDir));
			Files.copy(Paths.get(appDir + "/conf_templates/ideogram.conf"), Paths.get(confDir + "/ideogram.conf"));
			Files.copy(Paths.get(appDir + "/conf_templates/ticks.conf"), Paths.get(confDir + "/ticks.conf"));
		}
		catch (IOException e) {
			logger.error(e.getMessage());
		}

		logger.info("Writing config file for plots");
		// Open final plot configuration file for creation
		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(confDir + "/plots.conf")))) {
			List<Map<String, String>> tilePlots = new ArrayList<>();
			float currentRadius = 1.0f;
			float trackThickness = circos.getImageSize() * circos.getTrackWidth();

			// Build hash for large tile data because it is not included in the
			// genomic data

			if (circos.isIncludeOuterTrack()) {
				Map<String, String> largeTileData = new HashMap<>();
				largeTileData.put("file", dataDir + "/large.tiles.txt");
				largeTileData.put("thickness", Float.toString((trackThickness / 2)) + "p");
				largeTileData.put("type", "tile");
				largeTileData.put("color", colors.remove(0));
				largeTileData.put("r1", Float.toString(currentRadius) + "r");
				largeTileData.put("r0", Float.toString((currentRadius -= 0.02)) + "r");
				largeTileData.put("gid", gId);
				tilePlots.add(largeTileData);
			}
			else {
				colors.remove(0);
			}

			// Space in between tracks
			float trackBuffer = circos.getTrackWidth() - 0.03f;

			List<Map<String, String>> nonTilePlots = new ArrayList<>();

			// Build hash of plot data for Mustache to render
			Iterator<String> keys = genomeData.keySet().iterator();
			while (keys.hasNext()) {
				String track = keys.next();
				Map<String, String> plotData = new HashMap<>();

				// Handle user uploaded files
				if (track.contains("user_upload")) {
					List<Map<String, Object>> files = genomeData.get(track);

					for (Map<String, Object> file : files) {
						plotData = new HashMap<>();
						String plotType = file.get("plot_type").toString();

						if (plotType.equals("tile") || plotType.equals("heatmap")) {
							plotData.put("file", dataDir + "/" + file.get("file_name"));
							plotData.put("thickness", Float.toString(trackThickness) + "p");
							plotData.put("type", plotType);
							if (plotType.equals("tile")) {
								plotData.put("color", colors.remove(0));
							}
							else {
								plotData.put("color", "rdbu-10-div");
							}
							float r1 = (currentRadius -= (0.01 + trackBuffer));
							float r0 = (currentRadius -= (0.04 + trackBuffer));
							plotData.put("r1", Float.toString(r1) + "r");
							plotData.put("r0", Float.toString(r0) + "r");
							plotData.put("gid", gId);

							tilePlots.add(plotData);
						}
						else {
							plotData.put("file", dataDir + "/" + file.get("file_name"));
							plotData.put("type", plotType);
							plotData.put("color", colors.remove(0));
							float r1 = (currentRadius -= (0.01 + trackBuffer));
							float r0 = (currentRadius -= (0.10 + trackBuffer));
							plotData.put("r1", Float.toString(r1) + "r");
							plotData.put("r0", Float.toString(r0) + "r");
							plotData.put("min", "0.0");
							plotData.put("max", "1.0");
							if (plotType.equals("histogram")) {
								plotData.put("extendbin", "extend_bin = no");
							}
							else {
								plotData.put("extendbin", "");
							}
							String baseColor = plotData.get("color").replaceAll("^[vld]+", "");
							plotData.put("plotbgcolor", "vvl" + baseColor);
							// plotData.put("plotbgcolor", "white"); // temporary value

							nonTilePlots.add(plotData);
						}
					}
				}
				else if (track.contains("gc")) { // gc_content or gc_skew
					String plotType;
					if (track.equals("gc_content")) {
						plotType = circos.getGcContentPlotType();
					}
					else {
						plotType = circos.getGcSkewPlotType();
					}
					if (plotType.equals("heatmap")) {
						plotData.put("file", dataDir + "/" + track.replace("_", ".") + ".txt");
						plotData.put("thickness", Float.toString(trackThickness) + "p");
						plotData.put("type", plotType);
						plotData.put("color", "rdbu-10-div");
						float r1 = (currentRadius -= (0.01 + trackBuffer));
						float r0 = (currentRadius -= (0.04 + trackBuffer));
						plotData.put("r1", Float.toString(r1) + "r");
						plotData.put("r0", Float.toString(r0) + "r");

						plotData.put("gid", gId);
						tilePlots.add(plotData);
					}
					else {
						plotData.put("file", dataDir + "/" + track.replace("_", ".") + ".txt");
						plotData.put("type", plotType);
						plotData.put("color", colors.remove(0));
						float r1 = (currentRadius -= (0.01 + trackBuffer));
						float r0 = (currentRadius -= (0.10 + trackBuffer));
						plotData.put("r1", Float.toString(r1) + "r");
						plotData.put("r0", Float.toString(r0) + "r");
						plotData.put("min", (track.equals("gc_skew") ? "-1.0" : "0.0"));
						plotData.put("max", "1.0");
						if (plotType.equals("histogram")) {
							plotData.put("extendbin", "extend_bin = no");
						}
						else {
							plotData.put("extendbin", "");
						}
						String baseColor = plotData.get("color").replaceAll("^[vld]+", "");
						plotData.put("plotbgcolor", "vvl" + baseColor);
						// plotData.put("plotbgcolor", "white"); // temporary value

						nonTilePlots.add(plotData);
					}
				}
				else {
					// handle default/custom tracks
					plotData.put("file", dataDir + "/" + track.replace("_", ".") + ".txt");
					plotData.put("thickness", Float.toString(trackThickness) + "p");
					plotData.put("type", "tile");
					plotData.put("color", colors.remove(0));
					float r1 = (currentRadius -= (0.01 + trackBuffer));
					float r0 = (currentRadius -= (0.04 + trackBuffer));
					plotData.put("r1", Float.toString(r1) + "r");
					plotData.put("r0", Float.toString(r0) + "r");
					plotData.put("gid", gId);
					tilePlots.add(plotData);
				}
			}

			// plots configuration file
			Map<String, List<Map<String, String>>> data = new HashMap<>();
			data.put("tileplots", tilePlots);
			data.put("nontileplots", nonTilePlots);

			tmplPlotConf.execute(data, writer);
		}
		catch (IOException e) {
			logger.error(e.getMessage());
		}

		// image configuration file
		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(confDir + "/image.conf")))) {
			Map<String, String> data = new HashMap<String, String>();
			data.put("path", circos.getTmpDir());
			data.put("image_size", Integer.toString(circos.getImageSize()));

			tmplImageConf.execute(data, writer);
		}
		catch (IOException e) {
			logger.error(e.getMessage());
		}

		// circos configuration file
		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(confDir + "/circos.conf")))) {
			Map<String, String> data = new HashMap<String, String>();
			data.put("folder", circos.getTmpDir());

			tmplCircosConf.execute(data, writer);
		}
		catch (IOException e) {
			logger.error(e.getMessage());
		}
	}
}