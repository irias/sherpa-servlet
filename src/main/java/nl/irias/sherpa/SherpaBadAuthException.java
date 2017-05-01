package nl.irias.sherpa;

@SuppressWarnings("serial")
public class SherpaBadAuthException extends SherpaUserException {
	public SherpaBadAuthException(String message) {
		super("sherpaBadAuth", message);
	}
}
