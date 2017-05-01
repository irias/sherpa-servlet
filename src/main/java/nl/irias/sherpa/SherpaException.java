package nl.irias.sherpa;

@SuppressWarnings("serial")
public abstract class SherpaException extends Exception {
	String code;

	public SherpaException(String message) {
		super(message);
		this.code = "sherpaError";
	}

	public SherpaException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
