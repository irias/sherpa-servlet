package nl.irias.sherpa;

@SuppressWarnings("serial")
public class SherpaPermissionDeniedException extends SherpaUserException {
	public SherpaPermissionDeniedException(String message) {
		super("sherpaPermissionDenied", message);
	}
}
