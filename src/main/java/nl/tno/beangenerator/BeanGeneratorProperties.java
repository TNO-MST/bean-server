package nl.tno.beangenerator;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import nl.tno.omt.helpers.OmtJavaMapping;

/**
 * This class defines several options that can be set or unset to control the
 * code expansion by the Bean Generator.
 *
 * @author bergtwvd
 */
public class BeanGeneratorProperties {

	private static final Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(Boolean.TRUE));

	static public BeanGeneratorProperties read(File file) throws Exception {
		return jsonb.fromJson(new FileReader(file), BeanGeneratorProperties.class);
	}

	static public BeanGeneratorProperties read(URL url) throws Exception {
		return jsonb.fromJson(url.openStream(), BeanGeneratorProperties.class);
	}

	static public void write(File file, BeanGeneratorProperties properties) throws IOException {
		try (FileWriter fw = new FileWriter(file)) {
			jsonb.toJson(properties, fw);
		}
	}

	static public void write(StringBuilder sb, BeanGeneratorProperties properties) {
		sb.append(jsonb.toJson(properties));
	}

	/**
	 * Use fully qualified Java class names to ensure uniqueness, with the
	 * effect of long class names.
	 */
	private boolean useFQclassName = false;

	/**
	 * Use the Java List<T> type instead of T[] for OMT array data types.
	 */
	private boolean useList = false;

	/**
	 * Do not use notice message in each file.
	 */
	private boolean useNoNotice = false;

	/**
	 * Use boxed Java types for each Java class property. Boxing only applies to
	 * Java classes that relate to OMT classes, and not the Java classes that
	 * relate to OMT data types.
	 */
	private boolean useUnboxedType = false;

	/**
	 * Use a public vs a private modifier for Java class properties. This allows
	 * field-level access to class properties, rather than via the get/set
	 * methods.
	 */
	private boolean usePublicModifier = false;

	/**
	 * Default package names for modules; the first argument is a regex, the
	 * second is the package name.
	 */
	private Map<String, String> defaultPackageNames = new HashMap();

	/**
	 * Default group id that is prefixed to a package name.
	 */
	private String groupId = "nl.tno";

	/**
	 * Set of OMT encoding names. For OMT fixed record fields that have an
	 * encoding in this set no code will be generated. The encoding concerns a
	 * padding, and the encoding if for a byte array by definition.
	 */
	private Set<String> paddingEncodings = new HashSet();

	/**
	 * Additional representation mapping to the corresponding Java primitive
	 * type.
	 */
	private Map<String, OmtJavaMapping.JavaPrimitiveType> additionalRepresentationMapping = new HashMap();

	/**
	 * Additional OMT datatype mapping to corresponding Java type, and required Java import.
	 */
	private Map<String, Entry<String, List<String>>> additionalDatatypeMapping = new HashMap();

	public BeanGeneratorProperties() {
		// default package name mapping for NETN package names
		defaultPackageNames.put(".*netn.?ais.*", "netn.ais");
		defaultPackageNames.put(".*netn.?base.*", "netn.base");
		defaultPackageNames.put(".*netn.?cbrn.*", "netn.cbrn");
		defaultPackageNames.put(".*netn.?com.*", "netn.com");
		defaultPackageNames.put(".*netn.?etr.*", "netn.etr");
		defaultPackageNames.put(".*netn.?log.*", "netn.log");
		defaultPackageNames.put(".*netn.?metoc.*", "netn.metoc");
		defaultPackageNames.put(".*netn.?mrm.*", "netn.mrm");
		defaultPackageNames.put(".*netn.?org.*", "netn.org");
		defaultPackageNames.put(".*netn.?physical.*", "netn.physical");
		defaultPackageNames.put(".*netn.?se.*", "netn.se");
		defaultPackageNames.put(".*netn.?tmr.*", "netn.tmr");

		// default pattern for RPR package name
		defaultPackageNames.put(".*rpr.?fom.*", "rpr");

		// default pattern for CyberDEM package name
		defaultPackageNames.put(".*cyber.*", "cyber");

		// default pattern for MIM package name
		defaultPackageNames.put(".*hlastandardmim.*", "mim");

		// default RPR2 padding encodings; currently only these are supported and relevant
		paddingEncodings.add("RPRpaddingTo32Array");
		paddingEncodings.add("RPRpaddingTo64Array");

		// additional representation mappings for RPR2
		additionalRepresentationMapping.put("RPRunsignedInteger8BE", OmtJavaMapping.JavaPrimitiveType.BYTE);
		additionalRepresentationMapping.put("RPRunsignedInteger16BE", OmtJavaMapping.JavaPrimitiveType.SHORT);
		additionalRepresentationMapping.put("RPRunsignedInteger32BE", OmtJavaMapping.JavaPrimitiveType.INTEGER);
		additionalRepresentationMapping.put("RPRunsignedInteger64BE", OmtJavaMapping.JavaPrimitiveType.LONG);

		// additional datatype mappings for NETN3
		additionalDatatypeMapping.put("UuidArrayOfHLAbyte16", new SimpleEntry("UUID", List.of("java.util.UUID")));
		additionalDatatypeMapping.put("UUID", new SimpleEntry("UUID", List.of("java.util.UUID")));
		additionalDatatypeMapping.put("TransactionId", new SimpleEntry("UUID", List.of("java.util.UUID")));
	}

	public boolean isUseFQclassName() {
		return useFQclassName;
	}

	public void setUseFQclassName(boolean useFQclassName) {
		this.useFQclassName = useFQclassName;
	}

	public boolean isUseList() {
		return useList;
	}

	public void setUseList(boolean useList) {
		this.useList = useList;
	}

	public boolean isUseNoNotice() {
		return useNoNotice;
	}

	public void setNoNotice(boolean noNotice) {
		this.useNoNotice = noNotice;
	}

	public boolean isUseUnboxedType() {
		return useUnboxedType;
	}

	public void setUseUnboxedType(boolean useUnboxedType) {
		this.useUnboxedType = useUnboxedType;
	}

	public boolean isUsePublicModifier() {
		return usePublicModifier;
	}

	public void setUsePublicModifier(boolean usePublicModifier) {
		this.usePublicModifier = usePublicModifier;
	}

	public Map<String, String> getDefaultPackageNames() {
		return defaultPackageNames;
	}

	public void setDefaultPackageNames(Map<String, String> defaultPackageNames) {
		this.defaultPackageNames = defaultPackageNames;
	}

	public Set<String> getPaddingEncodings() {
		return paddingEncodings;
	}

	public void setPaddingEncodings(Set<String> paddingEncodings) {
		this.paddingEncodings = paddingEncodings;
	}

	public Map<String, OmtJavaMapping.JavaPrimitiveType> getAdditionalRepresentationMapping() {
		return additionalRepresentationMapping;
	}

	public void setAdditionalRepresentationMapping(Map<String, OmtJavaMapping.JavaPrimitiveType> representations) {
		this.additionalRepresentationMapping = representations;
	}

	public Map<String, Entry<String, List<String>>> getAdditionalDatatypeMapping() {
		return additionalDatatypeMapping;
	}

	public void setAdditionalDatatypeMapping(Map<String, Entry<String, List<String>>> additionalDatatypeMapping) {
		this.additionalDatatypeMapping = additionalDatatypeMapping;
	}	
	
	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

}
