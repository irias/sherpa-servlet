package nl.irias.sherpa;

@FunctionalInterface
public interface SherpaExceptionTransformer {
	Exception transform(Exception e);
}
