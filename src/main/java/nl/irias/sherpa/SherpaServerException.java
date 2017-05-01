package nl.irias.sherpa;

// for exceptions due to bad behaviour by the server.  analogous to the 5xx errors for http.
@SuppressWarnings("serial")
public class SherpaServerException extends SherpaException {
	public SherpaServerException(String message) {
		super("sherpaServerError", message);
	}

	public SherpaServerException(String code, String message) {
		super(code, message);
	}
}
