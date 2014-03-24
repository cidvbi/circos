require "sinatra"
require "open-uri"
require "json"
require_relative 'CircosGenerator'

# Route for initial page load
get "/" do
    erb :index
end

# Route for processing submitted form data
post "/" do
    CircosGenerator::create_circos_image(params)

    'Got the data!'
end