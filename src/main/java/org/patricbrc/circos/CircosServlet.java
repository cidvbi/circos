package org.patricbrc.circos;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletContext;
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

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		request.getRequestDispatcher("/jsp/index.jsp").include(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		HashMap<String, String> parameters = new HashMap<String, String>();

		// Parse request before run circosGenerator
		try {
			List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
			for (FileItem item : items) {
				if (item.isFormField()) {
					parameters.put(item.getFieldName(), item.getString());
				}
				else {
					// TODO: process form file field
					// String fieldName = item.getFieldName();
					// String filename = FilenameUtils.getName(item.getName());
					// InputStream filecontent = item.getInputStream();
				}
			}
		}
		catch (FileUploadException e) {
			throw new ServletException("Cannot parse multipart reqeust", e);
		}

		// getContextPath
		ServletContext servletContext = getServletContext();
		String contextPath = servletContext.getRealPath(File.separator);
		parameters.put("realpath", contextPath);

		// Generate Circo Image
		logger.info("parameters: {}", parameters.toString());
		
		CircosGenerator circosGenerator = new CircosGenerator();
		String imageId = circosGenerator.createCircosImage(parameters);
		logger.info("imageId:{}", imageId);

		if (imageId != null) {
			response.getWriter().write(imageId);
		}
	}
}