# Circos Image Generator

## Introduction

This repository contains a simple web server for the dynamic generation of Circos plots using data accessed through VBI's Solr API. The server makes one or more requests to Solr based on whatever genomic data that the user selects to be plotted on the web form. Currently there are six choices for plottable data:

* CDS (Forward/reverse)
* RNA (Forward/reverse)
* Miscellaneous (Forward/reverse)

The data the user selects to plot could be any combination of these six choices. After the user submits the form, the server then formats the data it receives into properly formatted data files that Circos' image generation script can use to create plots. The configuration files that Circos uses in creating the image must also be dynamically created based on what the users selects from the web form. Finally, the Circos script is executed using the newly produced data and config files and presents the image to the user.
