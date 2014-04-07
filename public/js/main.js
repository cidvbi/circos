function validateForm() {
    var goButton = document.getElementById("go");
    var gid = document.getElementById("gid");
    var inputs = document.getElementsByTagName('input');
    var errorMessage = document.getElementById('errorMessage');
    var oneChecked = false;
    for (var i = inputs.length - 1; i >= 0; i--) {
        // Check that at least one checkbox has been checked
        if (inputs[i].type == "checkbox" && inputs[i].checked == true) {
            oneChecked = true;
        };
    };

    if (gid.value == "" || !oneChecked) {
        errorMessage.innerHTML = "A GID must be entered and at least one checkbox must be checked";
        return false;
    };
    return true;
}

var customTrackCount = 0;
function addCustomTrack() {
    var add_custom_track = document.getElementById("add_custom_track");
    var custom_tracks_div = document.getElementById("custom_tracks");
    if (add_custom_track.checked) {
        // Add new section for creating custom tracks
        custom_tracks_div.innerHTML += "<h2>Define any custom tracks below:</h2>";
        new_custom_track =
            "<div name=\"custom_track\" id=\"custom_track_"+customTrackCount+"\"> \
                <label for=\"custom_track_type_"+customTrackCount+"\">Track Type:</label> \
                <select name=\"custom_track_type_"+customTrackCount+"\" id=\"custom_track_type_"+customTrackCount+"\"> \
                    <option selected value=\"cds\">CDS</option> \
                    <option value=\"rna\">RNA</option> \
                    <option value=\"misc\">Miscellaneous</option> \
                </select> \
                &nbsp; \
                <label for=\"custom_track_keyword_"+customTrackCount+"\">Keywords:</label> \
                <input name=\"custom_track_keyword_"+customTrackCount+"\" id=\"custom_track_keyword_"+customTrackCount+"\" \
                    type=\"text\" placeholder=\"Track keywords\"/> \
                &nbsp; \
                <button name=\"create_additional_custom_track\" \
                    id=\"create_additional_custom_track\" type=\"button\" \
                    onclick=\"nextCustomTrack();\">+</button> \
                <br><br> \
            </div>";

        custom_tracks_div.innerHTML += new_custom_track;
        customTrackCount++;
    } else {
        // Remove section for creating custom tracks
        custom_tracks_div.innerHTML = "";
    }
}

function nextCustomTrack() {
    if (customTrackCount <= 2) {
        var custom_tracks_div = document.getElementById("custom_tracks");
        new_custom_track =
            "<div name=\"custom_track\" id=\"custom_track_"+customTrackCount+"\"> \
                <label for=\"custom_track_type_"+customTrackCount+"\">Track Type:</label> \
                <select name=\"custom_track_type_"+customTrackCount+"\" id=\"custom_track_type_"+customTrackCount+"\"> \
                    <option selected value=\"cds\">CDS</option> \
                    <option value=\"rna\">RNA</option> \
                    <option value=\"misc\">Miscellaneous</option> \
                </select> \
                &nbsp; \
                <label for=\"custom_track_keyword_"+customTrackCount+"\">Keywords:</label> \
                <input name=\"custom_track_keyword_"+customTrackCount+"\" id=\"custom_track_keyword_"+customTrackCount+"\" \
                    type=\"text\" placeholder=\"Track keywords\"/> \
                &nbsp; \
                <button name=\"create_additional_custom_track\" \
                    id=\"create_additional_custom_track\" type=\"button\" \
                    onclick=\"nextCustomTrack();\">+</button> \
                <button name=\"remove_custom_track\" \
                    id=\"create_additional_custom_track\" type=\"button\" \
                    onclick=\"removeCustomTrack("+customTrackCount+");\">-</button> \
                <br><br> \
            </div>";
        custom_tracks_div.innerHTML += new_custom_track;
        customTrackCount++;
    }
}

function removeCustomTrack(custom_track_id) {
    var custom_track_div = document.getElementById("custom_track_"+custom_track_id);
    custom_track_div.parentNode.removeChild(custom_track_div);
    customTrackCount--;
}
