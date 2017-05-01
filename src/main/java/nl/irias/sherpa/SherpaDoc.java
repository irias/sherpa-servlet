package nl.irias.sherpa;

import java.util.List;
import java.util.ArrayList;

public class SherpaDoc {
	public String className;
	public String title;
	public String text;
	public List<SherpaFunctionDoc> functions;
	public List<SherpaDoc> sections;

	public SherpaDoc() {
		this.functions = new ArrayList<>();
		this.sections = new ArrayList<>();
	}

	public SherpaDoc(String className, String title, String text) {
		this.className = className;
		this.title = title;
		this.text = text;
		this.functions = new ArrayList<>();
		this.sections = new ArrayList<>();
	}
}
