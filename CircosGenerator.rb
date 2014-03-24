require 'sinatra'
require 'fileutils'

class CircosGenerator < Sinatra::Base

    def self.logger
        @logger ||= Logger.new STDOUT
    end

    def self.create_circos_image(parameters)
        unless parameters.empty?
            genome_data = get_genome_data(parameters)
            create_circos_data_files(genome_data)
        end
    end

    private
    def self.get_genome_data(parameters)
        # Hash for query strings of each feature type. Lookup is based on the
        # first half of the parameter's name,
        # e.g. cds_forward's first half is "cds"
        feature_types = {
            "cds" => "feature_type:CDS",
            "rna" => "feature_type:RNA",
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
        # Iterate over each checked off data type
        parameters.keys.each do |key|
            # Skip "Go" button and GID field parameters
            next if key == 'go' || key == 'gid'
            logger.info "Getting #{key} data"

            # Build query string based on user's input
            feature_type = feature_types[key.split("_")[0]]
            strand = key.split("_")[1] == 'forward' ? '+' : '-'
            query_data = {
                :wt => "json",
                :q => "gid:#{gid} AND strand:\"#{strand}\"",
                :fq => "annotation_f:PATRIC AND #{feature_type}",
                :indent => "true",
                :rows => 10000
            }

            # Encode the query as a URL and parse the JSON response into a Ruby
            # Hash object
            query_uri.query = URI.encode_www_form(query_data)
            logger.info "Requesting data from URL: #{query_uri}"
            response = JSON.parse(open(query_uri).read)

            # Pull out only the gene data from the JSON response
            genome_data[key] = response['response']['docs']
        end

        return genome_data
    end

    def self.create_circos_data_files(genome_data)
        # Delete any existing Circos data files
        FileUtils.rm_rf(Dir.glob('circos_data/*'))

        genome = ""
        accessions = {}
        genome_data.each do |feature_type,feature_data|

            # Create a Circos data file for each selected feature
            logger.info "Writing data file for feature, #{feature_type}"

            # File name has the following format: feature.strand.txt
            # e.g. cds.forward.txt OR rna.reverse.txt
            file_name = feature_type.gsub(/_/,'.') + ".txt"
            File.open("circos_data/#{file_name}", "w+") do |file|

                # Sort features primarily by their accession and secondarily by
                # their start_max
                feature_data.sort { |a,b|
                    comp = a['accession'] <=> b['accession']
                    comp.zero? ? (a['start_max'] <=> b['start_max']) : comp
                }.each do |gene|
                    # Store name of the genome
                    genome = gene["genome_name"] if genome.empty?

                    # File data writing goes here
                    accession = gene['accession']
                    start_max = gene['start_max']
                    end_min = gene['end_min']

                    file.write("#{accession}\t#{start_max}\t#{end_min}\n")

                    # Add the current accession to accessions list to create the
                    # karyotype file. End values are stored to write length of the
                    # accession to the karyotype file later.
                    if accessions[accession].nil?
                        # Create a hash entry for the current accession if one doesn't
                        # exist
                        accessions[accession] = { :end => [end_min] }
                    else
                        # Otherwise add end to list for the current
                        # accession
                        accessions[accession][:end] << end_min
                    end
                end
            end
        end

        # Write karyotype file
        logger.info "Creating karyotype file for genome, #{genome}"
        File.open("circos_data/karyotype.txt", "w+") do |file|
            accessions.keys.sort.each do |accession|
                a_data = accessions[accession]
                # Get the lowest start and the largest end for the karyotype
                # file
                max_end = a_data[:end].max
                file.write("chr\t-\t#{accession}\t#{genome}\t0\t#{max_end}\tgray\n")
            end
        end

        return true
    end

end
