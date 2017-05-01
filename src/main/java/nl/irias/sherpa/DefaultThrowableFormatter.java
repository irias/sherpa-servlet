package nl.irias.sherpa;

public class DefaultThrowableFormatter implements ThrowableFormatter {
	@Override
	public String format(Throwable t) {
		return t.toString();
	}
}
