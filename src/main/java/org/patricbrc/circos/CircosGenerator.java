package org.patricbrc.circos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Policy.Parameters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
			String folderName = "image_data/" + uuid;

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
/*
			// Create directory to store final image
			try {
				Files.createDirectory(Paths.get("public/images/" + uuid));
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			logger.info("Starting Circos script...");

			// Run Circos script to generate final image
			// `circos -conf #{folder_name}/circos_configs/circos.conf -debug_group summary,timer > circos.log.out`
			String command = "circos -conf " + folderName + "/circos_configs/circos.conf -debug_group summary,timer > circos.log.out";
			try {
				logger.info("running command: " + command);
				Runtime.getRuntime().exec(command);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			logger.info("Circos image was created successfully!");
*/
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
					//logger.info(sd.getFieldValueMap().toString());
					//doc.putAll(sd.getFieldValueMap());
					doc.put("genome_name", sd.get("genome_name"));
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
				int num = Integer.parseInt(key.substring(key.lastIndexOf("_")+1));
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
					//doc.putAll(sd.getFieldValueMap());
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
			Files.createDirectory(Paths.get(folderName + "/circos_data"));
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
			File f = new File(folderName + "/circos_data/" + fileName);
			f.setWritable(true);
			try {
				PrintWriter fWriter = new PrintWriter(f);
				for (Object obj: featureData) {
					JSONObject gene = (JSONObject) obj;
					genome = gene.get("genome_name").toString();
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
		queryData.setSort("accession", SolrQuery.ORDER.asc);
		
		logger.info("Requesting accession data from URL: {}", queryData.getQuery());
		QueryResponse qr;
		try {
			qr = solrServer.query(queryData, SolrRequest.METHOD.POST);
			SolrDocumentList sdl = qr.getResults();
			for (SolrDocument sd : sdl) {
				JSONObject doc = new JSONObject();
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
			File karyotype = new File(folderName + "/circos_data/karyotype.txt");
			karyotype.setWritable(true);
			PrintWriter writer = new PrintWriter(karyotype);
			for (Object obj: accessions) {
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
		/*
        unless $gc_content_plot_type.nil?
            logger.info 'Creating data file for GC content'

            accession_sequence_data.each do |accession,sequence|
                total_seq_length = sequence.length
                gc_content_values = {}

                # Iterate over each window_size-sized block and calculate its GC
                # content.
                # For instance, if the sequence length were 1,234,567 and the
                # window size were 1000, we would iterate 1234 times, with the
                # last iteration being the window from 1,234,001 to 1,234,566
                for i in 0..(total_seq_length / window_size)

                    # Only use 0 as start index for first iteration, otherwise
                    # with a window_size of 1000, start should be something like
                    # 1001, 2001, and so on.
                    start_index = i == 0 ? 0 : i * window_size + 1

                    # End index should either be 'window_size' greater than the start or
                    # if we are at the last iteration, the end of the sequence.
                    end_index = [(i + 1) * window_size, total_seq_length - 1].min

                    # Store number of 'g' and 'c' characters from the sequence
                    gc_count = sequence[start_index..end_index].chars
                                                               .reject{ |e| e.match(/[gcGC]/).nil? }
                                                               .size
                    gc_percentage = gc_count / window_size.to_f

                    # Store percentage in gc_content_values hash as value with the
                    # range from the start index to the end index as the key
                    gc_content_values[start_index..end_index] = gc_percentage.round(5)
                end

                # Write GC content data for this accession
                File.open("#{folder_name}/circos_data/gc.content.txt", "a+") do |file|
                    gc_content_values.each do |range,percentage|
                        file.write("#{accession}\t#{range.first}\t#{range.last}\t#{percentage}\n")
                    end
                end

                genome_data['gc_content'] = true
            end
        end
		*/
		// Create GC skew data file
		/*
        unless $gc_skew_plot_type.nil?
            logger.info 'Creating data file for GC skew'

            accession_sequence_data.each do |accession,sequence|
                total_seq_length = sequence.length
                gc_skew_values = {}

                for i in 0..(total_seq_length / window_size)
                    start_index = i == 0 ? 0 : i * window_size + 1
                    end_index = [(i + 1) * window_size, total_seq_length - 1].min

                    g_count = sequence[start_index..end_index].chars
                                                              .reject{ |e| e.match(/[gG]/).nil? }
                                                              .size
                    c_count = sequence[start_index..end_index].chars
                                                              .reject{ |e| e.match(/[cC]/).nil? }
                                                              .size
                    gc_skew = (g_count - c_count) / (g_count + c_count).to_f

                    gc_skew_values[start_index..end_index] = gc_skew.round(5)
                end

                # Write GC skew data for this accession
                File.open("#{folder_name}/circos_data/gc.skew.txt", "a+") do |file|
                    gc_skew_values.each do |range,skew|
                        file.write("#{accession}\t#{range.first}\t#{range.last}\t#{skew}\n")
                    end
                end

                genome_data['gc_skew'] = true
            end
        end
		*/
		// Write "large tiles" file
		logger.info("Creating large tiles file for genome, {}", genome);
		try {
			File file = new File(folderName + "/circos_data/large.tiles.txt");
			file.setWritable(true);
			PrintWriter writer = new PrintWriter(file);
			for (Object obj: accessions) {
				JSONObject accession = (JSONObject) obj;
				
				writer.format("%s\t0\t%d\n", accession.get("accession"), accession.get("length"));
			}
			writer.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		// Move uploaded data files to the new image's 'circos_data' directory
        /*
        unless parameters[:file_chooser].nil?
            uploaded_file_name_list = parameters[:file_chooser].map { |e| e[:tempfile] }

            # Get file plot type parameters from web form and convert the symbols
            # to strings
            $file_plot_types = parameters.select { |k,v| k.include? "file_plot_type" }
                                         .map { |k,v| v }

            unless uploaded_file_name_list.nil?
                uploaded_file_name_list.each_with_index do |upload_tempfile,i|
                    File.open("#{folder_name}/circos_data/user.upload.#{i}.txt", "w") do |file|
                        first_line = upload_tempfile.readline
                        file_plot_type = $file_plot_types[i]

                        if file_plot_type == 'tile'
                            next if first_line.split(/\s+/).size != 3
                        else
                            next if first_line.split(/\s+/).size != 4
                        end

                        # Read from the server's temporary version of the file and write
                        # it to a data file in the new image's directory
                        upload_tempfile.rewind
                        tempfile_contents = upload_tempfile.read

                        file.write(tempfile_contents)

                        # Add custom track to genome data so that it will be plotted
                        genome_data["user_upload_#{i}"] = true
                    end
                end
            end
        end
		 */
		return true;
	}

	private void createCircosConfigFiles(String folderName, JSONObject genomeData) {
		List<String> colors = new LinkedList<String>();
		colors.addAll(Arrays.asList(new String[] { "vdblue", "vdgreen", "lgreen", "vdred", "lred", "vdpurple", "lpurple", "vdorange", "lorange",
				"vdyellow", "lyellow" }));
		
		// Create folder for config files
		try {
			Files.createDirectory(Paths.get(folderName + "/circos_configs"));
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		
		// Copy static conf files to image's temp directory
		try {
			Files.copy(Paths.get("conf_templates/ideogram.conf"), Paths.get(folderName + "/circos_configs/ideogram.conf"));
			Files.copy(Paths.get("conf_templates/ticks.conf"), Paths.get(folderName + "/circos_configs/ticks.conf"));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		logger.info("Writing config file for plots");

		// Open final plot configuration file for creation
		try {
			// File.open("#{folder_name}/circos_configs/plots.conf", "w+") do |file|
			File file = new File(folderName + "/circos_configs/plots.conf");
			file.setWritable(true);
			PrintWriter writer = new PrintWriter(file);
			
			//tileplots = []
			ArrayList<HashMap<String,String>> tilePlots = new ArrayList<HashMap<String, String>>();
			float currentRadius = 1.0f;
			float trackThickness = imageSize * trackWidth;

			// Build hash for large tile data because it is not included in the
			// genomic data

			if (includeOuterTrack) {
				HashMap<String, String> largeTileData = new HashMap<String, String>();
				largeTileData.put("file", "circos_data/large.tiles.txt");
				//largeTileData.put("thickness", "#{track_thickness / 2}p"); // TODO: review this
				largeTileData.put("thickness", Float.toString((trackThickness/2)));
				largeTileData.put("type", "tile");
				largeTileData.put("color", colors.get(0)); colors = colors.subList(1, colors.size()-1); // colors.shift
				largeTileData.put("r1", Float.toString(currentRadius)); // {}r?
				// largeTileData.put("r0", #{(current_radius -= 0.02)}r"// TODO: review this
				largeTileData.put("r0", Float.toString((currentRadius -= 0.02)));
				//tileplots << largeTileData; // TODO: review this
				tilePlots.add(largeTileData);
			} else {
				//colors.shift;
				colors = colors.subList(1, colors.size()-1);
			}
			
			// Space in between tracks
			float trackBuffer = trackWidth - 0.03f;

			// nontileplots = []
			ArrayList<HashMap<String,String>> nonTilePlots = new ArrayList<HashMap<String,String>>();

			// Build hash of plot data for Mustache to render
			Iterator<String> keys = genomeData.keySet().iterator();
			while (keys.hasNext()) {
				String featureType = keys.next();
				HashMap<String, String> plotData = new HashMap<String, String>();
				
				// Handle user uploaded files
				if (featureType.contains("user_upload")) {
					int userUploadNumber = Integer.parseInt(featureType.substring(featureType.lastIndexOf("_")));
					//plotType = filePlotTypes.get
				}
				else if (featureType.contains("gc")) {
					
				}
				else {
					plotData.put("file", "circos_data/" + featureType.replace("_", ".") + ".txt");
					plotData.put("thickness", Float.toString(trackThickness));
					plotData.put("type", "tile");
					plotData.put("color", colors.get(0)); colors = colors.subList(1, colors.size()-1);
					float r1 = (currentRadius -= (0.01 + trackBuffer));
					float r0 = (currentRadius -= (0.04 + trackBuffer));
					plotData.put("r1", Float.toString(r1));
					plotData.put("r0", Float.toString(r0));
					tilePlots.add(plotData);
				}
			}
            /*
            genome_data.each_key do |feature_type|
                plot_data = {}

                # Handle user uploaded files
                if feature_type.include? 'user_upload'
                    user_upload_number = feature_type.split('_').last.to_i
                    plot_type = $file_plot_types[user_upload_number]

                    if plot_type == 'tile' || plot_type == 'heatmap'
                        file_name = feature_type.gsub(/_/,'.') + '.txt'
                        plot_data['file'] = "circos_data/#{file_name}"
                        plot_data['thickness'] = "#{track_thickness}p"
                        plot_data['type'] = plot_type

                        color = plot_type == 'tile' ? colors.shift : 'rdbu-10-div'
                        plot_data['color'] = color

                        plot_data['r1'] = "#{(current_radius -= (0.01 + track_buffer)).round(2)}r"
                        plot_data['r0'] = "#{(current_radius -= (0.04 + track_buffer)).round(2)}r"

                        tileplots << plot_data
                    else
                        file_name = feature_type.gsub(/_/,'.') + ".txt"
                        plot_data['file'] = "circos_data/#{file_name}"

                        plot_data['type'] = plot_type
                        plot_data['color'] = colors.shift

                        plot_data['r1'] = "#{(current_radius -= (0.01 + track_buffer)).round(2)}r"
                        plot_data['r0'] = "#{(current_radius -= (0.10 + track_buffer)).round(2)}r"

                        plot_data['min'] = 0.0
                        plot_data['max'] = 1.0

                        plot_data['extendbin'] = "extend_bin = no" if plot_type == 'histogram'

                        # If the line color is "vdred", the base color will be
                        # just "red" and the plot's background will be "vvlred"
                        base_color = plot_data['color'].gsub(/^[vld]+/, '')
                        plot_data['plotbgcolor'] = "vvl#{base_color}"

                        nontileplots << plot_data
                    end
                # Handle GC content/skew tracks
                elsif feature_type.include? 'gc'
                    # Dynamically access specific global variable for the GC content/skew's plot type
                    plot_type = eval("$#{feature_type}_plot_type")

                    if plot_type == 'heatmap'
                        file_name = feature_type.gsub(/_/,'.') + '.txt'
                        plot_data['file'] = "circos_data/#{file_name}"
                        plot_data['thickness'] = "#{track_thickness}p"
                        plot_data['type'] = plot_type

                        color = plot_type == 'tile' ? colors.shift : 'rdbu-10-div'
                        plot_data['color'] = color

                        plot_data['r1'] = "#{(current_radius -= (0.01 + track_buffer)).round(2)}r"
                        plot_data['r0'] = "#{(current_radius -= (0.04 + track_buffer)).round(2)}r"

                        tileplots << plot_data
                    else
                        file_name = feature_type.gsub(/_/,'.') + ".txt"
                        plot_data['file'] = "circos_data/#{file_name}"

                        plot_data['type'] = plot_type
                        plot_data['color'] = colors.shift

                        plot_data['r1'] = "#{(current_radius -= (0.01 + track_buffer)).round(2)}r"
                        plot_data['r0'] = "#{(current_radius -= (0.10 + track_buffer)).round(2)}r"

                        plot_data['min'] = feature_type == 'gc_skew' ? -1.0 : 0.0
                        plot_data['max'] = 1.0

                        plot_data['extendbin'] = "extend_bin = no" if plot_type == 'histogram'

                        # If the line color is "vdred", the base color will be
                        # just "red" and the plot's background will be "vvlred"
                        base_color = plot_data['color'].gsub(/^[vld]+/, '')
                        plot_data['plotbgcolor'] = "vvl#{base_color}"

                        nontileplots << plot_data
                    end

                # Handle default/custom tracks
                else
                    file_name = feature_type.gsub(/_/,'.') + ".txt"
                    plot_data['file'] = "circos_data/#{file_name}"
                    plot_data['thickness'] = "#{track_thickness}p"
                    plot_data['type'] = 'tile'
                    plot_data['color'] = colors.shift
                    plot_data['r1'] = "#{(current_radius -= (0.01 + track_buffer)).round(2)}r"
                    plot_data['r0'] = "#{(current_radius -= (0.04 + track_buffer)).round(2)}r"
                    tileplots << plot_data
                end
            end
            */
			logger.info(tilePlots.toString());
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
		String imageId = folderName.split("/")[1];

		logger.info("Writing config file for image {}", imageId);

		// Open final image configuration file for creation
		try {
			File file = new File(folderName + "/circos_configs/image.conf");
			file.setWritable(true);
			PrintWriter writer = new PrintWriter(file);
			
			Template tmpl = Mustache.compiler().compile(new BufferedReader(new FileReader("conf_templates/image.mu")));
			Map<String, String> data = new HashMap<String, String>();
			data.put("path", "public/images/" + imageId);
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
			File file = new File(folderName + "/circos_configs/circos.conf");
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