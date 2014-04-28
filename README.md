# Circos Image Generator

## Introduction

This repository contains a simple web server for the dynamic generation of Circos plots using data accessed through VBI's Solr API. The server makes one or more requests to Solr based on whatever genomic data that the user selects to be plotted on the web form. Currently there are six choices for plottable data:

* CDS (Forward/reverse)
* RNA (Forward/reverse)
* Miscellaneous (Forward/reverse)

The data the user selects to plot could be any combination of these six choices. After the user submits the form, the server then formats the data it receives into properly formatted data files that Circos' image generation script can use to create plots. The configuration files that Circos uses in creating the image must also be dynamically created based on what the users selects from the web form. Finally, the Circos script is executed using the newly produced data and config files and presents the image to the user.

## Running the server

Once all of Circos' dependencies have been installed, running the Sinatra server is relatively simple:

    bundle install
    rackup -p 4567
    
## Using the site

The left half of the page contains options for creating an image with default tracks using PATRIC's data, the option to create "custom" tracks which simply filter data from PATRIC however the user wishes, as well as options for customizing the Circos-generated image itself (image size, width of tracks, etc.).

The right half of the page is solely a panel for the user to upload their own data, the format of which is explained in detail on the page.

