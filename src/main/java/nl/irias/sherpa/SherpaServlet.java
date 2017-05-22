package nl.irias.sherpa;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.logging.Logger;
import java.util.logging.Level;


@SuppressWarnings("serial")
public class SherpaServlet extends HttpServlet {
	String rawHtml; // still needs docsURL filled in
	String rawJavascript; // still needs json filled in
	SherpaJSON sherpaJson; // baseurl will be filled in with just the path.  before returning data, the path will be prefixed by the host to which the http request was sent.
	Map<String, Method> functions;
	Set<String> logParameterFunctions;
	Set<String> logResultFunctions;
	SherpaDoc documentation;
	ThrowableFormatter throwableFormatter;
	SherpaCollector collector;
	String lastModified;


	final String SHERPA_BAD_FUNCTION = "sherpaBadFunction";
	final String SHERPA_BAD_REQUEST = "sherpaBadRequest";
	final String SHERPA_BAD_PARAMS = "sherpaBadParams";
	final String SHERPA_SERVER_ERROR = "sherpaServerError";

	final static Logger logger = Logger.getLogger(SherpaServlet.class.getPackage().getName());

	static class DefaultCollector implements SherpaCollector {
		@Override
		public void sherpaProtocolError() {
		}

		@Override
		public void sherpaBadFunction() {
		}

		@Override
		public void sherpaJavascript() {
		}

		@Override
		public void sherpaJSON() {
		}

		@Override
		public void sherpaFunctionCalled(String name, boolean error, boolean serverError, double duration) {
		}
	}

	static class SherpaJSON {
		public String id;
		public String title;
		public String[] functions;
		public String baseurl;
		public String version;
		final public int sherpaVersion = 0;

		public SherpaJSON(String id, String title, String[] functions, String baseurl, String version) {
			this.id = id;
			this.title = title;
			this.functions = functions;
			this.baseurl = baseurl;
			this.version = version;
		}
	}

	public SherpaServlet(String path, String id, String title, String version, Class<?>[] sections, SherpaDoc sherpaDoc, ThrowableFormatter throwableFormatter, SherpaCollector collector) throws Exception {
		documentation = sherpaDoc;

		lastModified = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now(java.time.ZoneId.of("GMT")));

		if (throwableFormatter == null) {
			throwableFormatter = new DefaultThrowableFormatter();
		}
		this.throwableFormatter = throwableFormatter;

		if (sections.length == 0) {
			throw new Exception("At least one sherpa section is required.");
		}

		this.functions = new HashMap<>();
		this.logParameterFunctions = new HashSet<>();
		this.logResultFunctions = new HashSet<>();
		for (Class<?> c : sections) {
			java.lang.annotation.Annotation _section = c.getAnnotation(SherpaSection.class);
			if (_section == null) {
				throw new Exception("Section does not have SherpaSection annotation. Use @SherpaSection(title=\"...\", docs=\"...\").");
			}
			SherpaSection section = (SherpaSection)_section;

			for (Method m : c.getDeclaredMethods()) {
				SherpaFunction fn = m.getAnnotation(SherpaFunction.class);
				if (fn == null) {
					continue;
				}

				String name = m.getName();

				if(!Modifier.isStatic(m.getModifiers())) {
					throw new Exception(String.format("Function %s must be static if you want to export it as Sherpa function.", name));
				}

				if (this.functions.containsKey(name)) {
					throw new Exception(String.format("Duplicate function name %s.", name));
				}

				this.functions.put(name, m);
				if (fn.logParameters()) {
					this.logParameterFunctions.add(name);
				}
				if (fn.logResult()) {
					this.logResultFunctions.add(name);
				}
			}
		}

		this.sherpaJson = new SherpaJSON(id, title, this.functions.keySet().toArray(new String[]{}), path, version);

		try (InputStream htmlStream = SherpaServlet.class.getClassLoader().getResourceAsStream("nl/irias/sherpa/index.html")) {
			this.rawHtml = readAll(htmlStream)
				.replace("{{.id}}", escapeHTML(id))
				.replace("{{.title}}", escapeHTML(title))
				.replace("{{.version}}", escapeHTML(version));
			// note: docsURL still needs to be filled in!
		}

		try (InputStream stream = SherpaServlet.class.getClassLoader().getResourceAsStream("nl/irias/sherpa/sherpa.js")) {
			this.rawJavascript = readAll(stream);
		}

		if (collector == null) {
			collector = new DefaultCollector();
		}
		this.collector = collector;
	}

	private String getBaseUrl(HttpServletRequest request, String path) {
		String host = request.getHeader("x-forwarded-host");
		if (host == null) {
			host = request.getHeader("host");
		}
		String scheme = request.getHeader("x-forwarded-proto");
		if (scheme == null) {
			scheme = "http";
		}
		return scheme + "://" + host + path;
	}

	private String makeSherpaJson(SherpaJSON s, HttpServletRequest request) throws IOException {
		return new ObjectMapper().writeValueAsString(new SherpaJSON(s.id, s.title, s.functions, getBaseUrl(request, s.baseurl), s.version));
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String name = request.getPathInfo();
		if (name == null) {
			fileNotFound(response);
			return;
		}
		name = name.substring(1);

		if (name.equals("")) {
			if(attempt304(request, response)) {
				return;
			}
			response.setContentType("text/html; charset=utf-8");
			response.setHeader("cache-control", "no-cache, max-age=0");
			response.setHeader("last-modified", lastModified);
			response.setStatus(HttpServletResponse.SC_OK);
			response.getOutputStream().write(this.rawHtml.replace("{{.docsURL}}", "https://sherpa.irias.nl/#" + getBaseUrl(request, this.sherpaJson.baseurl)).getBytes("UTF-8"));
			return;
		}

		if (name.equals("sherpa.js")) {
			collector.sherpaJavascript();
			if(attempt304(request, response)) {
				return;
			}
			response.setContentType("text/javascript; charset=utf-8");
			response.setHeader("cache-control", "no-cache, max-age=0");
			response.setHeader("last-modified", lastModified);
			response.setStatus(HttpServletResponse.SC_OK);
			response.getOutputStream().write(this.rawJavascript.replace("SHERPA_JSON", makeSherpaJson(this.sherpaJson, request)).getBytes("UTF-8"));
			return;

		}

		if (name.equals("sherpa.json")) {
			collector.sherpaJSON();
			CORS(response);
			if(attempt304(request, response)) {
				return;
			}
			response.setContentType("application/json; charset=utf-8");
			response.setHeader("cache-control", "no-cache, max-age=0");
			response.setHeader("last-modified", lastModified);
			response.setStatus(HttpServletResponse.SC_OK);
			response.getOutputStream().write(makeSherpaJson(this.sherpaJson, request).getBytes("UTF-8"));
			return;
		}

		String callback = request.getParameter("callback");
		if (callback != null && !validCallback(callback)) {
			collector.sherpaProtocolError();
			respondError(response, callback, SHERPA_BAD_REQUEST, "invalid callback string", HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		String body = request.getParameter("body");
		if (body == null) {
			body = "{\"params\": []}";
		}

		try {
			call(response, callback, name, new ByteArrayInputStream(body.getBytes("UTF-8")));
		} catch (Exception e) {
			collector.sherpaProtocolError();
			throw new ServletException(e.getMessage());
		}
	}

	private static void fileNotFound(HttpServletResponse response) throws IOException {
		// seems to be a bug in jetty, a handler for /xyz/* gets us here for /xyz with a null pathinfo...
		response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		response.setContentType("text/plain; charset=utf-8");
		response.getOutputStream().write("404 - file not found".getBytes("UTF-8"));
	}

	private boolean attempt304(HttpServletRequest request, HttpServletResponse response) {
		String ifMod = request.getHeader("If-Modified-Since");
		if (ifMod != null && ifMod.equals(lastModified)) {
			response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return true;
		}
		return false;
	}

	private void call(HttpServletResponse response, String callback, String name, InputStream body) throws Exception {
		Method m = this.functions.get(name);
		if (m == null) {
			if (name.equals("_docs") && documentation != null) {
				// xxx there is no parameter checking here...
				respondOK(response, callback, new SherpaResponse(documentation, null));
				collector.sherpaFunctionCalled("_docs", false, false, 0);
				return;
			}

			respondError(response, callback, SHERPA_BAD_FUNCTION, "function does not exit", HttpServletResponse.SC_NOT_FOUND);
			collector.sherpaBadFunction();
			return;
		}

		double t0 = now();

		// xxx would be better to read the params array elements as raw json bytes, parse them once later.  is this possible with jackson?
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);

		SherpaRequest req;
		try {
			req = mapper.readValue(body, SherpaRequest.class);
		} catch (JsonMappingException e) {
			respondErrorOK(response, callback, SHERPA_BAD_REQUEST, "could not parse request parameters: "+e.getMessage());
			collector.sherpaFunctionCalled(name, true, false, now()-t0);
			return;
		}

		Class<?>[] paramTypes = m.getParameterTypes();
		if (req.params.length != paramTypes.length) {
			collector.sherpaProtocolError();
			respondErrorOK(response, callback, SHERPA_BAD_REQUEST, String.format("wrong number of parameters: expected %d, got %d", paramTypes.length, req.params.length));
			collector.sherpaFunctionCalled(name, true, false, now()-t0);
			return;
		}
		Object[] params = new Object[paramTypes.length];
		for (int i = 0; i < params.length; i++) {
			try {
				params[i] = mapper.readValue(mapper.writeValueAsString(req.params[i]), paramTypes[i]);
			} catch (JsonMappingException e) {
				respondErrorOK(response, callback, SHERPA_BAD_REQUEST, String.format("could not parse parameter %d: %s", i, e.getMessage()));
				collector.sherpaFunctionCalled(name, true, false, now()-t0);
				return;
			}
		}

		// reset time again, we are not interested in the time it takes to execute the function
		t0 = now();

		Object result;
		try {
			if (logger.isLoggable(Level.FINER)) {
				if (this.logParameterFunctions.contains(name)) {
					logger.log(Level.FINER, "calling function {0} with parameters {1}", new Object[]{name, trim(mapper.writeValueAsString(params), 4*1024)});
				} else {
					logger.log(Level.FINER, "calling function {0} (parameters hidden due to sensitivity)", new Object[]{name});
				}
			}
			result = m.invoke(null, params);

		} catch (InvocationTargetException e) {
			Throwable ee = e.getCause();

			if (ee instanceof SherpaUserException) {
				logger.log(Level.FINE, "user exception from function "+name);
				logger.log(Level.FINEST, "SherpaUserException from function "+name, ee);
				collector.sherpaFunctionCalled(name, true, false, now()-t0);
			} else {
				logger.log(Level.SEVERE, "exception from function "+name, ee);
				collector.sherpaFunctionCalled(name, true, true, now()-t0);
			}

			if (ee instanceof SherpaInternalServerException) {
				SherpaInternalServerException eee = (SherpaInternalServerException)ee;
				respondError(response, callback, eee.code, ee.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return;
			} else if (ee instanceof SherpaException) {
				SherpaException eee = (SherpaException)ee;
				respondErrorOK(response, callback, eee.code, ee.getMessage());
				return;
			} else {
				String msg;
				try {
					msg = throwableFormatter.format(ee);
				} catch (Exception eee) {
					logger.log(Level.SEVERE, "exception while formatting exception", eee);
					msg = "error while formatting earlier error";
				}
				respondErrorOK(response, callback, SHERPA_SERVER_ERROR, msg);
				return;
			}
		} catch (java.lang.IllegalAccessException e) {
			logger.log(Level.FINE, "exception (1) calling "+name, e);
			respondErrorOK(response, callback, SHERPA_SERVER_ERROR, e.getMessage());
			collector.sherpaFunctionCalled(name, true, true, now()-t0);
			return;
		} catch (java.lang.IllegalArgumentException e) {
			logger.log(Level.FINE, "exception (2) calling "+name, e);
			respondErrorOK(response, callback, SHERPA_BAD_PARAMS, "bad parameters: " + e.getMessage());
			collector.sherpaFunctionCalled(name, true, false, now()-t0);
			return;
		}

		collector.sherpaFunctionCalled(name, false, false, now()-t0);

		if (logger.isLoggable(Level.FINER)) {
			if (this.logResultFunctions.contains(name)) {
				logger.log(Level.FINER, "invocation of {0} successful, result: {1}", new Object[]{name, trim(mapper.writeValueAsString(result), 4*1024)});
			} else {
				logger.log(Level.FINER, "invocation of {0} successful (result hidden due to sensitivity)", new Object[]{name});
			}
		}
		respondOK(response, callback, new SherpaResponse(result, null));
	}

	private static String trim(String s, int n) {
		if (s.length() > 4*1024) {
			return s.substring(0, 4*1024) + "...";
		}
		return s;
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String callback = null;

		String name = request.getPathInfo();
		if (name == null) {
			fileNotFound(response);
			return;
		}
		name = name.substring(1);

		ContentType ct = parseContentType(request.getContentType());
		if (!ct.type.equals("application/json") || (!ct.charset.equals("") && !ct.charset.equals("utf-8"))) {
			respondErrorOK(response, callback, SHERPA_BAD_REQUEST, "content-type of request should be application/json");
			return;
		}
		try {
			call(response, callback, name, request.getInputStream());
		} catch (Exception e) {
			throw new ServletException(e.getMessage());
		}
	}

	@Override
	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String name = request.getPathInfo();
		if (name == null) {
			fileNotFound(response);
			return;
		}

		CORS(response);
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

	private static String escapeHTML(String html) {
		return html
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}

	private String readAll(InputStream s) throws IOException {
		// xxx a function like this has to be in the standard library somewhere...

		ByteArrayOutputStream o = new ByteArrayOutputStream();

		byte[] buf = new byte[1024];
		for (;;) {
			int n = s.read(buf);
			if (n == -1) {
				// eof!
				break;
			}
			o.write(buf, 0, n);
		}
		return o.toString("UTF-8");
	}

	private void CORS(HttpServletResponse r) {
		r.setHeader("Access-Control-Allow-Origin", "*");
		r.setHeader("Access-Control-Allow-Methods", "GET, POST");
		r.setHeader("Access-Control-Allow-Headers", "Content-Type");
	}

	private void respondErrorOK(HttpServletResponse r, String callback, String code, String message) throws IOException, UnsupportedEncodingException {
		respondError(r, callback, code, message, HttpServletResponse.SC_OK);
	}

	private void respondError(HttpServletResponse r, String callback, String code, String message, int status) throws IOException, UnsupportedEncodingException {
		respond(r, callback, new SherpaResponse(null, new SherpaError(code, message)), status);
	}

	private void respondOK(HttpServletResponse response, String callback, SherpaResponse resp) throws IOException, UnsupportedEncodingException {
		respond(response, callback, resp, HttpServletResponse.SC_OK);
	}

	private void respond(HttpServletResponse response, String callback, SherpaResponse resp, int status) throws IOException, UnsupportedEncodingException {
		CORS(response);
		response.setHeader("Cache-Control", "no-store");

		OutputStream out = response.getOutputStream();

		if (callback == null) {
			response.setContentType("application/json; charset=utf-8");
			response.setStatus(status);
			new ObjectMapper().writeValue(out, resp);
			return;
		}

		response.setContentType("text/javascript; charset=utf-8");
		response.setStatus(status);

		out.write((callback + "(\n\t").getBytes("UTF-8"));

		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
		mapper.writeValue(out, resp);
		out.write(");".getBytes("UTF-8"));
	}

	// return whether callback js snippet is valid.
	// this is a coarse test.  we disallow some valid js identifiers, like "\u03c0",
	// and we allow many invalid ones, such as js keywords, "0intro" and identifiers starting/ending with ".", or having multiple dots.
	private boolean validCallback(String cb) {
		if (cb.equals("")) {
			return false;
		}
		for (char c : cb.toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '$' || c == '.') {
				continue;
			}
			return false;
		}
		return true;
	}

	static class ContentType {
		String type;
		String charset;

		public ContentType(String type, String charset) {
			this.type = type.trim().toLowerCase();
			this.charset = charset.trim().toLowerCase();
		}
	}

	private ContentType parseContentType(String ct) {
		String[] l = ct.split(";", 2);
		if (l.length == 1) {
			return new ContentType(ct, "");
		}
		return new ContentType(l[0], l[1]);
	}

	private double now() {
		return System.currentTimeMillis()/1000;
	}
}
