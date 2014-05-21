package org.patricbrc.circos;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircosServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = LoggerFactory.getLogger(CircosGenerator.class);

	CircosGenerator circosGenerator;

	public void init(ServletConfig config) throws ServletException {
		String contextPath = config.getServletContext().getRealPath(File.separator);
		circosGenerator = new CircosGenerator(contextPath);
		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		request.getRequestDispatcher("/jsp/index.jsp").include(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		Map<String, Object> parameters = new LinkedHashMap<>();
		int fileCount = 0;
		// Parse request before run circosGenerator
		try {
			List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
			for (FileItem item : items) {
				if (item.isFormField()) {
					parameters.put(item.getFieldName(), item.getString());
				}
				else {
					parameters.put("file_" + fileCount, item);
					fileCount++;
				}
			}
		}
		catch (FileUploadException e) {
			throw new ServletException("Cannot parse multipart reqeust", e);
		}

		// Generate Circo Image
		logger.info("parameters: {}", parameters.toString());

		String imageId = circosGenerator.createCircosImage(parameters);
		logger.info("imageId:{}", imageId);

		if (imageId != null) {
			response.getWriter().write(imageId);
		}
	}
}