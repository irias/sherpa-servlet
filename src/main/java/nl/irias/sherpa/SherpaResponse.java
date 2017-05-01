package nl.irias.sherpa;

public class SherpaResponse {
	public Object result;
	public SherpaError error;

	public SherpaResponse(Object result, SherpaError error) {
		this.result = result;
		this.error = error;
	}
}
