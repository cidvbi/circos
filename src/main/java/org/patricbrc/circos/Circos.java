package org.patricbrc.circos;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Circos {
	private String genomeId;

	private String uuid;

	private String tmpDir;

	private boolean includeOuterTrack = false;

	private int imageSize = 1000;

	private float trackWidth = 0.03f;

	private String gcContentPlotType = null;

	private String gcSkewPlotType = null;

	private Map<String, List<Map<String, Object>>> genomeData;

	public Circos(String dir) {
		uuid = UUID.randomUUID().toString();
		tmpDir = dir + "/images/" + uuid;
	}

	public String getGcContentPlotType() {
		return gcContentPlotType;
	}

	public String getGcSkewPlotType() {
		return gcSkewPlotType;
	}

	public Map<String, List<Map<String, Object>>> getGenomeData() {
		return genomeData;
	}

	public String getGenomeId() {
		return genomeId;
	}

	public int getImageSize() {
		return imageSize;
	}

	public String getTmpDir() {
		return tmpDir;
	}

	public float getTrackWidth() {
		return trackWidth;
	}

	public String getUuid() {
		return uuid;
	}

	public boolean isIncludeOuterTrack() {
		return includeOuterTrack;
	}

	public void setGcContentPlotType(String gcContentPlotType) {
		this.gcContentPlotType = gcContentPlotType;
	}

	public void setGcSkewPlotType(String gcSkewPlotType) {
		this.gcSkewPlotType = gcSkewPlotType;
	}

	public void setGenomeData(Map<String, List<Map<String, Object>>> genomeData) {
		this.genomeData = genomeData;
	}

	public void setGenomeId(String id) {
		this.genomeId = id;
	}

	public void setImageSize(int imageSize) {
		this.imageSize = imageSize;
	}

	public void setIncludeOuterTrack(boolean includeOuterTrack) {
		this.includeOuterTrack = includeOuterTrack;
	}

	public void setTrackWidth(float trackWidth) {
		this.trackWidth = trackWidth;
	}
}