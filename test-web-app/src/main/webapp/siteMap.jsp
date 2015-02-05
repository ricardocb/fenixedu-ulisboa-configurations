<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@page import="org.fenixedu.bennu.core.domain.Bennu"%>
<%@page import="org.fenixedu.academic.domain.Degree"%>
<%@page import="org.fenixedu.cms.domain.Site"%>
<html:xhtml />
<head>
	<meta charset="utf-8">
	<meta http-equiv="Content-Language" content="pt-PT" />
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
	<meta http-equiv="X-UA-Compatible" content="IE=edge">
	
	<style type="text/css">
		body {
			background-color: #f1f1f1;
		}
		.site-map {
			position: absolute;
			top: 50px;
			left: 100px;
			font-family: sans-serif;
		}
		.site-map-title {
			font-weight: bold;
			font-size: 18px;
		}
	</style>
</head>

<body>
	<div class="site-map">
		<div class="site-map-title">
			<span>Cursos:</span>			
		</div>
		<ul>
		<%
				for (Degree degree : Bennu.getInstance().getDegreesSet()) {
					if (degree.getSite() != null) {
						Site site = degree.getSite();
		%>
						<li><a href="<%= site.getFullUrl() %>">[<%= degree.getSigla() %>] <%= site.getName().getContent() %></a></li>
		<%
						
					}
				}
		%>
		</ul>
	</div>
</body>