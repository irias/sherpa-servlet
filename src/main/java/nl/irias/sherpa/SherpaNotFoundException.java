package nl.irias.sherpa;

@SuppressWarnings("serial")
public class SherpaNotFoundException extends SherpaUserException {
	public SherpaNotFoundException(String message) {
		super("sherpaNotFound", message);
	}
}
