package nl.irias.sherpa;

import java.lang.reflect.Field;

/**
 * SherpaDocHelper makes it easy to add information about types used in functions to your sherpa documentation.
 */
public abstract class SherpaDocHelper {
	/**
	 * DescribeData will lookup "sectionName" in the sherpa documentation "doc" (which you must have parsed before hand). It adds type information for all "classes": It shows lists all public fields, found through reflection.
	 */
	public static void describeData(SherpaDoc doc, String sectionName, Class<?>... classes) throws Exception {
		SherpaDoc d = getDoc(doc, sectionName);
		if (d == null) {
			throw new Exception("missing section "+sectionName);
		}

		String s = "\n\n## Data\n\nBelow you'll find the data structures used in the functions of this section.\n";

		for (Class<?> cl : classes) {
			String name = SherpaDoclet.friendlyName(cl.getName());
			s += "\n### " + name + "\n\n";
			for (Field f : cl.getFields()) {
				s += String.format("- `%s`: %s\n", f.getName(), SherpaDoclet.friendlyName(f.getGenericType().getTypeName()));
			}
		}
		d.text += escapeHtml(s);
	}

	public static SherpaDoc getDoc(SherpaDoc doc, String sectionName) {
		if (doc.title.equals(sectionName)) {
			return doc;
		}
		for (SherpaDoc section : doc.sections) {
			section = getDoc(section, sectionName);
			if (section != null) {
				return section;
			}
		}
		return null;
	}

	private static String escapeHtml(String s) {
		return s
			.replace("&", "&amp;")
			.replace("\"", "&quot;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}
}
