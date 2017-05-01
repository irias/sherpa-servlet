package nl.irias.sherpa;

// for gathering statistics
public interface SherpaCollector {
	void sherpaProtocolError();
	void sherpaBadFunction();
	void sherpaJavascript();
	void sherpaJSON();
	void sherpaFunctionCalled(String name, boolean error, boolean serverError, double duration);
}
