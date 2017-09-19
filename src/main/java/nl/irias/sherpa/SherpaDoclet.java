package nl.irias.sherpa;

import com.sun.javadoc.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;
import java.io.FileOutputStream;

import nl.irias.sherpa.SherpaDoc;
import nl.irias.sherpa.SherpaFunctionDoc;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SherpaDoclet {

	static class Function {
		int line;
		SherpaFunctionDoc fn;

		public Function(int line, SherpaFunctionDoc fn) {
			this.line = line;
			this.fn = fn;
		}
	}

	public static boolean start(RootDoc root) throws Exception {
		String sherpadoc = "sherpadoc.json";
		List<String> sherpaSections = null; // null means: all sections
		for (String[] opt : root.options()) {
			if (opt[0].equals("-sherpadoc")) {
				if (opt.length != 2) {
					throw new Exception("bad params for -sherpadoc");
				}
				sherpadoc = opt[1];
			}
			if (opt[0].equals("-sherpasections")) {
				if (opt.length != 2) {
					throw new Exception("bad params for -sherpasections");
				}
				sherpaSections = Arrays.asList(opt[1].split("[:]"));
			}
		}

		HashMap<String,SherpaDoc> sections = new HashMap<>();

		for (ClassDoc cd : root.classes()) {
			SherpaDoc section = parseSherpaSection(cd);
			if (section == null || (sherpaSections != null && !sherpaSections.contains(cd.name()))) {
				continue;
			}

			sections.put(cd.name(), section);

			List<Function> functions = new ArrayList<>();
			for (MethodDoc md : cd.methods()) {
				SherpaFunctionDoc function = parseSherpaFunction(md);
				if (function != null) {
					functions.add(new Function(md.position().line(), function));
				}
			}
			Collections.sort(functions, (a, b) -> a.line - b.line);
			section.functions = functions.stream().map(f -> f.fn).collect(Collectors.toList());
		}

		if (sections.size() == 0) {
			return false;
		}

		List<SherpaDoc> docs = new ArrayList<>(sections.values());
		if (sherpaSections != null) {
			final List<String> sherpaSections_ = sherpaSections;
			Collections.sort(docs, (a, b) -> sherpaSections_.indexOf(a.className) - sherpaSections_.indexOf(b.className));
		}

		SherpaDoc rootDoc = null;
		for (SherpaDoc doc : docs) {
			if (rootDoc == null) {
				rootDoc = doc;
			} else {
				rootDoc.sections.add(doc);
			}
		}
		new ObjectMapper().writeValue(new FileOutputStream(sherpadoc), rootDoc);
		return true;
	}

	public static int optionLength(String option) {
		if(option.equals("-sherpadoc")) {
			return 2;
		}
		if(option.equals("-sherpasections")) {
			return 2;
		}
		return 0;
	}

	public static SherpaDoc parseSherpaSection(ClassDoc cd) {
		String title = null;
		String text = null;
		boolean isSherpaSection = false;

		for (AnnotationDesc ad : cd.annotations()) {
			if (ad.annotationType().qualifiedName().equals("nl.irias.sherpa.SherpaSection")) {
				isSherpaSection = true;
				for (AnnotationDesc.ElementValuePair evp : ad.elementValues()) {
					if (evp.element().name().equals("title")) {
						title = (String)evp.value().value();
					}
				}
			}
		}
		if (isSherpaSection && title != null) {
			return new SherpaDoc(cd.name(), title, cd.commentText());
		}
		return null;
	}

	public static SherpaFunctionDoc parseSherpaFunction(MethodDoc md) {
		for (AnnotationDesc ad : md.annotations()) {
			if (ad.annotationType().qualifiedName().equals("nl.irias.sherpa.SherpaFunction")) {
				String name = md.name();
				String text = md.commentText();
				String synopsis = "";

				for (AnnotationDesc.ElementValuePair evp : ad.elementValues()) {
					if (evp.element().name().equals("synopsis")) {
						synopsis = evp.value().toString();
					}
				}
				if (synopsis.equals("")) {
					ArrayList<String> params = new ArrayList<>();
					for (Parameter p : md.parameters()) {
						params.add(p.name() + " " + friendlyName(p.type().toString()));
					}
					synopsis = String.format("%s(%s)", name, String.join(", ", params));
					String returnType = friendlyName(md.returnType().toString());
					if (!returnType.equals("void")) {
						synopsis += ": " + returnType;
					}
				}
				return new SherpaFunctionDoc(name, synopsis + "\n" + text);
			}
		}
		return null;
	}

	public static String friendlyName(String name) {
		String r = name.replaceAll("[a-zA-Z0-9]+\\.", "");
		switch (r) {
		default:
			break;

		case "Byte":
		case "Short":
		case "Integer":
		case "Long":
		case "Float":
		case "Double":
		case "Character":
		case "Boolean":
		case "String":
			r = r.toLowerCase();
			break;
		}

		return r;
	}

	// needed to get parametrized types
	public static LanguageVersion languageVersion() {
		return LanguageVersion.JAVA_1_5;
	}
}
