<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ taglib prefix = "c" uri = "http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
	<title>Circos Image Generator</title>
	<script src="//ajax.googleapis.com/ajax/libs/jquery/1.11.0/jquery.min.js"></script>
	<script src="//cdn.sencha.io/ext-4.1.1a-gpl/ext-all.js"></script>
	<link rel="stylesheet" href="//cdn.sencha.io/ext-4.1.1a-gpl/resources/css/ext-all.css">
	<script type="text/javascript" src="<c:url value="/js/tab.js" />" ></script>
	<link rel="stylesheet" type="text/css" href="<c:url value="/css/tab.css" />">
	<link rel="stylesheet" type="text/css" href="//patricbrc.org/patric/css/patric.css">
	<link rel="stylesheet" type="text/css" href="//patricbrc.org/patric/css/main.css">
</head>
<body>
	<div id="circosPanel">
		<div id="circosGraph"></div>
	</div>
</body>
</html>