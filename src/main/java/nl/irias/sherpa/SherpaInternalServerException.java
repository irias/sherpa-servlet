package nl.irias.sherpa;

@SuppressWarnings("serial")
public class SherpaInternalServerException extends SherpaServerException {

	public SherpaInternalServerException(String message) {
		super(message);
	}

	public SherpaInternalServerException(String code, String message) {
		super(code, message);
	}
}
