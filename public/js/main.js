$(document).ready(function() {
    $('#image_dimensions').keyup(function () {
        this.value = this.value.replace(/[^0-9\.]/g,'');
    });

    $("#track_width").change(function() {
        $("#slider_value").html(this.value + "%");
    });

    $("#file_chooser").change(function() {
        var chosen_files = $("#file_chooser").get(0).files;
        var file_list_div = $("#file_list");
        file_list_div.html("");
        $(chosen_files).each(function(i) {
            file_name = this.name;
            file_list_div.append("<b>" + file_name + "</b>&nbsp;&nbsp;" +
                                 "<label for=\"file_plot_type_"+i+"\">Plot Type:</label>&nbsp;&nbsp;" +
                                 "<select name=\"file_plot_type_"+i+"\" id=\"file_plot_type_"+i+"\">" +
                                 "    <option value=\"tile\">Tiles</option>" +
                                 "    <option value=\"line\">Line Plot</option>" +
                                 "    <option value=\"histogram\">Histogram</option>" +
                                 "    <option value=\"heatmap\">Heatmap</option>" +
                                 "</select><br>");
        });
    });

    $("#image_data_form").submit(function(e) {
        if (validateForm()) {
            var formObj = $(this);
            var formURL = formObj.attr("action");
            var formData = new FormData(this);
            var circos_result = $("#circos_result");

            $("#go").prop("disabled", true);
            $.ajax({
                url: formURL,
                type: "POST",
                data:  formData,
                mimeType: "multipart/form-data",
                contentType: false,
                cache: false,
                processData: false
            })
            .done(function(response) {
                var image_file = "images/"+response+"/circos.svg";
                circos_result.html(
                    "<h2>Your generated Circos plot:</h2> \
                    <object type=\"image/svg+xml\" data=\""+image_file+"\"> \
                        Your browser does not support SVG. \
                    </object>"
                );
            })
            .fail(function() {
                $("#errorMessage").html("The Circos image could not be created");
            })
            .always(function() {
                $("#go").removeAttr("disabled");
            });
            circos_result.html("<h2>Circos image is being generated...</h2>");
        }
        e.preventDefault();
        return false;
    });
});

function validateForm() {
    var goButton = $("#go");
    var gid = $("#gid");
    var inputs = $('input');
    var errorMessageDiv = $('#errorMessage');
    var oneChecked = false;

    inputs.each(function(index) {
        // Check that at least one checkbox has been checked
        if (this.type == "checkbox" && this.checked) {
            oneChecked = true;
        };
    });

    if (gid.val() == "" || !oneChecked) {
        errorMessageDiv.html("A GID must be entered and at least one default track must be selected");
        return false;
    };

    return true;
}



// Variable to store current number of custom tracks
var customTrackCount = 0;

/*
This function adds only the first custom track's inputs to the page or removes
all custom tracks' inputs from the page if the check box is unchecked
*/
function firstCustomTrack() {
    var add_custom_track = document.getElementById("add_custom_track");
    var top_level_custom_tracks_div = document.getElementById("custom_tracks");
    if (add_custom_track.checked) {
        // Add new section for creating custom tracks
        top_level_custom_tracks_div.innerHTML = "<h2>Define custom tracks below:</h2>";
        var new_custom_track_div = document.createElement('div');
        new_custom_track_div.id = "custom_track_"+customTrackCount;

        var new_custom_track_form =
"<label for=\"custom_track_type_"+customTrackCount+"\">Track Type:</label> \
<select name=\"custom_track_type_"+customTrackCount+"\" id=\"custom_track_type_"+customTrackCount+"\"> \
    <option selected value=\"cds\">CDS</option> \
    <option value=\"rna\">RNA</option> \
    <option value=\"misc\">Miscellaneous</option> \
</select> \
&nbsp; \
<label for=\"custom_track_strand_"+customTrackCount+"\">Strand Direction:</label> \
<select name=\"custom_track_strand_"+customTrackCount+"\" id=\"custom_track_strand_"+customTrackCount+"\"> \
    <option selected value=\"forward\">Forward</option> \
    <option value=\"reverse\">Reverse</option> \
    <option value=\"both\">Both</option> \
</select> \
&nbsp; \
<label for=\"custom_track_keyword_"+customTrackCount+"\">Keywords:</label> \
<input name=\"custom_track_keyword_"+customTrackCount+"\" id=\"custom_track_keyword_"+customTrackCount+"\" \
    type=\"text\" placeholder=\"Track keywords\"/> \
&nbsp; \
<button name=\"create_additional_custom_track_"+customTrackCount+"\" \
    id=\"create_additional_custom_track_"+customTrackCount+"\" type=\"button\" \
    onclick=\"nextCustomTrack();\" \
    title=\"Add another track\">+</button> \
<br><br>";

        new_custom_track_div.innerHTML = new_custom_track_form;
        top_level_custom_tracks_div.appendChild(new_custom_track_div);

        customTrackCount++;
    } else {
        // Remove section for creating custom tracks
        top_level_custom_tracks_div.innerHTML = "<br>";
        customTrackCount = 0;
    }
}

function nextCustomTrack() {
    if (customTrackCount <= 2) {
        var top_level_custom_tracks_div = document.getElementById("custom_tracks");

        var new_custom_track_div = document.createElement('div');
        new_custom_track_div.id = "custom_track_"+customTrackCount;

        var new_custom_track_form =
"<div name=\"custom_track\" id=\"custom_track_"+customTrackCount+"\"> \
    <label for=\"custom_track_type_"+customTrackCount+"\">Track Type:</label> \
    <select name=\"custom_track_type_"+customTrackCount+"\" id=\"custom_track_type_"+customTrackCount+"\"> \
        <option selected value=\"cds\">CDS</option> \
        <option value=\"rna\">RNA</option> \
        <option value=\"misc\">Miscellaneous</option> \
    </select> \
    &nbsp; \
    <label for=\"custom_track_strand_"+customTrackCount+"\">Strand Direction:</label> \
    <select name=\"custom_track_strand_"+customTrackCount+"\" id=\"custom_track_strand_"+customTrackCount+"\"> \
        <option selected value=\"forward\">Forward</option> \
        <option value=\"reverse\">Reverse</option> \
        <option value=\"both\">Both</option> \
    </select> \
    &nbsp; \
    <label for=\"custom_track_keyword_"+customTrackCount+"\">Keywords:</label> \
    <input name=\"custom_track_keyword_"+customTrackCount+"\" id=\"custom_track_keyword_"+customTrackCount+"\" \
        type=\"text\" placeholder=\"Track keywords\"/> \
    &nbsp; \
    <button name=\"create_additional_custom_track_"+customTrackCount+"\" \
        id=\"create_additional_custom_track_"+customTrackCount+"\" type=\"button\" \
        onclick=\"nextCustomTrack();\" \
        title=\"Add another track\">+</button> \
    <button name=\"remove_custom_track_"+customTrackCount+"\" \
        id=\"create_additional_custom_track_"+customTrackCount+"\" type=\"button\" \
        onclick=\"removeCustomTrack("+customTrackCount+");\" \
        title=\"Remove this track\">-</button> \
    <br><br> \
</div>";

        new_custom_track_div.innerHTML = new_custom_track_form;
        top_level_custom_tracks_div.appendChild(new_custom_track_div);

        // Disable this button after it has been used
        var addButton = document.getElementById("create_additional_custom_track_"+(customTrackCount - 1));
        addButton.disabled = true;

        customTrackCount++;
    }
}

function removeCustomTrack(custom_track_id) {
    var custom_track_div = document.getElementById("custom_track_"+custom_track_id);
    custom_track_div.parentNode.removeChild(custom_track_div);

    customTrackCount--;

    // Reenable previous button
    var previousAddButton = document.getElementById("create_additional_custom_track_"+(customTrackCount - 1));
    previousAddButton.disabled = false;
}
