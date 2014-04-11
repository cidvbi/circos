require 'sinatra'
require 'fileutils'
require 'mustache'

class CircosGenerator < Sinatra::Base

    def self.logger
        @logger ||= Logger.new STDOUT
    end

    $image_size = 1000
    $outer_track_included = false

    # Public method to create Circos image based on data from web form
    #
    def self.create_circos_image(parameters)
        unless parameters.empty?
            # Delete any existing Circos data files
            FileUtils.rm_rf(Dir.glob('circos_data/*'))

            # Record whether to include outer track or not
            $outer_track_included = parameters[:include_outer_track] unless parameters[:include_outer_track].nil?

            # Store image size parameter from form
            $image_size = parameters[:image_dimensions].to_i unless parameters[:image_dimensions].empty?

            # Collect genome data using Solr API for PATRIC
            genome_data = get_genome_data(parameters)

            if genome_data.nil? || genome_data.empty?
                logger.error "No genome data found for GID #{parameters[:gid]}"
                return nil
            end

            # Run 'uuidgen' command to create a random string to name temp directory
            # for the circos image and data files
            uuid = `uuidgen`.chomp.gsub(/-/, '')
            folder_name = "image_data/#{uuid}"

            # Create temp directory for this image's data
            Dir.mkdir(folder_name)

            # Create data and config files for Circos
            create_circos_data_files(folder_name, parameters, genome_data)
            create_circos_config_files(folder_name, genome_data)

            # Create directory to store final image
            Dir.mkdir("public/images/#{uuid}")

            logger.info "Starting Circos script..."

            # Run Circos script to generate final image
            `circos -conf #{folder_name}/circos_configs/circos.conf -debug_group summary,timer > circos.log.out`

            logger.info "Circos image was created successfully!"

            return uuid
        end

        logger.error "Circos image could not be created"
        return nil
    end


    ###################################################################
    #############             Private methods             #############
    ###################################################################
    private
    def self.get_genome_data(parameters)
        # Hash for query strings of each feature type. Lookup is based on the
        # first half of the parameter's name,
        # e.g. cds_forward's lookup is "cds"
        feature_types = {
            "cds" => "feature_type:CDS",
            "rna" => "feature_type:*RNA",
            "misc" => "!(feature_type:*RNA OR feature_type:CDS)"
        }

        # Extract genome ID from form data
        gid = parameters[:gid]

        # Build base URL
        query_uri = URI::HTTP.build({
            :host => "macleod.vbi.vt.edu",
            :port => 8983,
            :path => "/solr/dnafeature/select"
        })

        # Hash to store JSON response data from Solr
        genome_data = {}

        parameter_names = ["cds_forward", "cds_reverse", "rna_forward",
                           "rna_reverse", "misc_forward", "misc_reverse"]

        # Iterate over each checked off data type
        parameters.keys.each do |parameter|

            # Skip over parameters that aren't track types
            next unless parameter_names.include? parameter

            logger.info "Getting #{parameter} data"

            # Build query string based on user's input
            feature_type = feature_types[parameter.split("_")[0]]
            strand = parameter.split("_")[1] == 'forward' ? '+' : '-'
            query_data = {
                :wt => "json",
                :q => "gid:#{gid} AND strand:\"#{strand}\"",
                :fq => "annotation_f:PATRIC AND #{feature_type}",
                :sort => "accession asc,start_max asc",
                :indent => "true",
                :rows => 10000
            }

            # Encode the query as a URL and parse the JSON response into a Ruby
            # Hash object
            query_uri.query = URI.encode_www_form(query_data)
            logger.info "Requesting feature data from URL: #{query_uri}"
            response = JSON.parse(open(query_uri).read)

            # Pull out only the gene data from the JSON response
            genome_data[parameter] = response['response']['docs'] if response['responseHeader']['status'] == 0
        end

        # Create a set of all the entered custom track numbers
        track_nums = Set.new
        parameters.keys
                  .select{ |e| /custom_track_.*/.match e }
                  .each { |parameter| track_nums << parameter[/.*_(\d+)$/, 1] }

        # Gather data for each custom track
        track_nums.each do |track_num|
            custom_track_name = "custom_track_#{track_num}"
            feature_type = parameters["custom_track_type_#{track_num}"]
            strand = case parameters["custom_track_strand_#{track_num}"]
                when 'forward' then ' AND strand:"+"'
                when 'reverse' then ' AND strand:"-"'
                when 'both' then ''
            end
            keywords = parameters["custom_track_keyword_#{track_num}"]
            keywords = " AND #{keywords}" unless keywords.empty?

            query_data = {
                :wt => "json",
                :q => "gid:#{gid}#{strand}#{keywords}",
                :fq => "annotation_f:PATRIC AND #{feature_type}",
                :sort => "accession asc,start_max asc",
                :indent => "true",
                :rows => 10000
            }

            query_uri.query = URI.encode_www_form(query_data)
            logger.info "Requesting feature data from URL: #{query_uri}"
            response = JSON.parse(open(query_uri).read)

            # Add custom track data to the set of all genomic data to be used
            genome_data[custom_track_name] = response['response']['docs'] if response['responseHeader']['status'] == 0
        end

        return genome_data
    end

    def self.create_circos_data_files(folder_name, parameters, genome_data)

        # Create folder for all data files
        FileUtils.mkdir("#{folder_name}/circos_data")

        genome = nil
        accessions = {}
        genome_data.each do |feature_type,feature_data|

            # Create a Circos data file for each selected feature
            logger.info "Writing data file for feature, #{feature_type}"

            # File name has the following format: feature.strand.txt
            # e.g. cds.forward.txt, rna.reverse.txt
            file_name = feature_type.gsub(/_/,'.') + ".txt"
            File.open("#{folder_name}/circos_data/#{file_name}", "w+") do |file|
                feature_data.each do |gene|
                    # Store name of the genome
                    genome ||= gene["genome_name"]

                    file.write("#{gene['accession']}\t#{gene['start_max']}\t#{gene['end_min']}\n")
                end
            end
        end

        # Build base URL
        query_uri = URI::HTTP.build({
            :host => "macleod.vbi.vt.edu",
            :port => 8983,
            :path => "/solr/sequenceinfo/select"
        })

        # Data for accession info query
        gid = parameters[:gid]
        query_data = {
            :wt => "json",
            :q => "gid:#{gid}",
            :sort => "accession asc",
            :indent => "true"
        }
        query_uri.query = URI.encode_www_form(query_data)
        logger.info "Requesting accession data from URL: #{query_uri}"
        response = JSON.parse(open(query_uri).read)
        accessions = response['response']['docs']

        # Write karyotype file
        logger.info "Creating karyotype file for genome, #{genome}"
        File.open("#{folder_name}/circos_data/karyotype.txt", "w+") do |file|
            accessions.each do |accession_data|
                file.write("chr\t-\t#{accession_data['accession']}\t#{genome.gsub(/\s/, '_')}\t0\t#{accession_data['length']}\tgrey\n")
            end
        end

        # Write "large tiles" file
        logger.info "Creating large tiles file for genome, #{genome}"
        File.open("#{folder_name}/circos_data/large.tiles.txt", "w+") do |file|
            accessions.each do |accession_data|
                file.write("#{accession_data['accession']}\t0\t#{accession_data['length']}\n")
            end
        end

        return true
    end

    # Method to create Circos configuration files. Currently only creates a
    # plots.conf file as it is the only one that truly must be dynamically
    # generated
    #
    def self.create_circos_config_files(folder_name, genome_data)
        colors = ['vdblue', 'vdgreen', 'lgreen', 'vdred', 'lred', 'vdpurple',
                  'lpurple', 'vdorange', 'lorange', 'vdyellow, lyellow']

        # Create folder for config files
        FileUtils.mkdir("#{folder_name}/circos_configs")

        # Copy static conf files to image's temp directory
        FileUtils.cd('conf_templates') do
            FileUtils.cp(%w(ideogram.conf ticks.conf),
                         "../#{folder_name}/circos_configs")
        end

        logger.info "Writing config file for plots"
        # Open final plot configuration file for creation
        File.open("#{folder_name}/circos_configs/plots.conf", "w+") do |file|
            plots = []
            current_radius = 1.0
            track_thickness = $image_size * 0.03;

            # Build hash for large tile data because it is not included in the
            # genomic data
            if $include_outer_track
                large_tile_data = {}
                large_tile_data['file'] = 'circos_data/large.tiles.txt'
                large_tile_data['thickness'] = "#{track_thickness / 2}p"
                large_tile_data['type'] = 'tile'
                large_tile_data['color'] = colors.shift
                large_tile_data['r1'] = "#{current_radius}r"
                large_tile_data['r0'] = "#{(current_radius -= 0.02)}r"
                plots << large_tile_data
            else
                colors.shift
            end

            # Build hash of plot data for Mustache to render
            genome_data.each_key do |feature_type|
                plot_data = {}
                file_name = feature_type.gsub(/_/,'.') + ".txt"
                plot_data['file'] = "circos_data/#{file_name}"
                plot_data['thickness'] = "#{track_thickness}p"
                plot_data['type'] = 'tile'
                plot_data['color'] = colors.shift
                plot_data['r1'] = "#{current_radius -= 0.01}r"
                plot_data['r0'] = "#{current_radius -= 0.04}r"
                plots << plot_data
            end

            # Render plots config file using template
            file.write(Mustache.render(File.read("conf_templates/plots.mu"), :plots => plots))
        end

        # Split out the UUID from the folder name to obfuscate the final image's
        # path as well
        image_id = folder_name.split('/')[1]

        logger.info "Writing config file for image #{image_id}"

        # Open final image configuration file for creation
        File.open("#{folder_name}/circos_configs/image.conf", "w+") do |file|
            # Render image config file using template
            file.write(Mustache.render(File.read("conf_templates/image.mu"), {
                :path => "public/images/#{image_id}",
                :image_size => $image_size
            }))
        end

        logger.info "Writing main config file for Circos"
        # Open final circos configuration file for creation
        File.open("#{folder_name}/circos_configs/circos.conf", "w+") do |file|
            # Render main circos config file using template
            file.write(Mustache.render(File.read("conf_templates/circos.mu"), :folder => folder_name))
        end

    end
end
