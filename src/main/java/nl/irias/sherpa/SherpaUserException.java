package nl.irias.sherpa;

// for exceptions due to bad behaviour by the client.  analogous to the 4xx errors for http.
@SuppressWarnings("serial")
public class SherpaUserException extends SherpaException {
	public SherpaUserException(String message) {
		super("sherpaUserError", message);
	}

	public SherpaUserException(String code, String message) {
		super(code, message);
	}
}
