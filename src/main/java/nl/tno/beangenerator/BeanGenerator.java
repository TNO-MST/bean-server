package nl.tno.beangenerator;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.tno.omt.ArrayDataTypesType.ArrayData;
import nl.tno.omt.Attribute;
import nl.tno.omt.EnumeratedDataType.Enumerator;
import nl.tno.omt.EnumeratedDataTypesType;
import nl.tno.omt.EnumeratedDataTypesType.EnumeratedData;
import nl.tno.omt.FixedRecordDataType.Field;
import nl.tno.omt.FixedRecordDataTypesType;
import nl.tno.omt.FixedRecordDataTypesType.FixedRecordData;
import nl.tno.omt.HLAString;
import nl.tno.omt.InteractionClass;
import nl.tno.omt.ObjectClass;
import nl.tno.omt.ObjectModelType;
import nl.tno.omt.Parameter;
import nl.tno.omt.VariantRecordDataType.Alternative;
import nl.tno.omt.VariantRecordDataTypesType;
import nl.tno.omt.VariantRecordDataTypesType.VariantRecordData;
import nl.tno.omt.helpers.OmtFunctions;
import nl.tno.omt.helpers.OmtJavaMapping;
import nl.tno.omt.helpers.OmtJavaMapping.JavaDataType;
import nl.tno.omt.helpers.OmtMimConstants;

/**
 * This class contains the methods to generate Java Beans from FOM modules.
 *
 * <p>The package structure for the expanded Java classes is as follows. For each FOM module there
 * is a Java package name. Each Java package has a fixed structure with three sub-packages: objects,
 * interactions, and data types. These sub-packages contain the source files.
 *
 * <p>The root package name is a concatenation of the group identifier and a name derived from the
 * OMT module name.
 *
 * @author bergtwvd
 */
public class BeanGenerator {

  ////////////////////////////////////////////////////////////////////////////
  // Public properties
  ////////////////////////////////////////////////////////////////////////////
  public static final String OBJECTS_PACKAGENAME = "objects";
  public static final String INTERACTIONS_PACKAGENAME = "interactions";
  public static final String DATATYPES_PACKAGENAME = "datatypes";
  public static final String PKG_SEPARATOR = ".";
  public static final String DIR_SEPARATOR = "/";
  public static final String JAVA_FILE_SUFFIX = ".java";
  public static final String HLA_UNKNOWN_ENUM = "HLAunknown";

  ////////////////////////////////////////////////////////////////////////////
  // Private properties
  ////////////////////////////////////////////////////////////////////////////
  // Configuration properties
  private final BeanGeneratorProperties properties;

  // Mapping of URL module to package name.
  private final Map<URL, String> module2packageName = new HashMap();

  // Mapping of OMT module to package name.
  private final Map<ObjectModelType, String> omtModule2packageName = new HashMap();

  // Maps to keep track of OMT class name to Java class name mapping
  private final Map<String, String> objectClassMap = new HashMap();
  private final Map<String, String> interactionClassMap = new HashMap();
  private final Set<String> objectClassNames = new HashSet();
  private final Set<String> interactionClassNames = new HashSet();

  // Set of OMT Modules.
  private ObjectModelType[] omtModules;

  // Current package and module being expanded; these are set induring the expansion cycle.
  private String currentPackageName;
  private ObjectModelType currentModule;

  // Date of expansion.
  private String date;

  // Set of array datatypes with a padding encoding, not to be expanded.
  private Set<String> paddingDataTypes;

  ////////////////////////////////////////////////////////////////////////////
  // Public constructors
  ////////////////////////////////////////////////////////////////////////////
  public BeanGenerator() {
    this(new BeanGeneratorProperties());
  }

  public BeanGenerator(BeanGeneratorProperties properties) {
    this.properties = properties;

    // add additional representations mappings
    OmtJavaMapping.addRepresentations(this.properties.getAdditionalRepresentationMapping());

    // add additional datatype mappings
    for (Entry<String, Entry<String, List<String>>> entry :
        this.properties.getAdditionalDatatypeMapping().entrySet()) {
      OmtJavaMapping.addDatatype(
          entry.getKey(), entry.getValue().getKey(), new HashSet<>(entry.getValue().getValue()));
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  // Public expansion methods
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Expand an array of FOM modules to Java packages with Java source files. The array of FOM
   * modules must form a complete set, i.e. all referenced classes and data types must be present.
   *
   * <p>Each FOM module is mapped to a Java package name. If no array of package names is provided
   * (null) then random package names will be generated. If the array contains one package name then
   * all classes are mapped into this package. If more than one package name is provided then the
   * size of the array must match the number of FOM modules.
   *
   * <p>The selectors array indicates which FOM modules will be expanded. If no array is provided
   * (null) then all modules will be expanded. If the array contains one selector value then this
   * value is used for all modules. If more than one selector value is provided then the size of the
   * array must match the number of FOM modules.
   *
   * @param modules: array of FOM modules. Nullable.
   * @param packageNames: array of Java package names. Nullable.
   * @param selectors: array of selectors, indicating what modules to expand. Nullable.
   * @throws Exception
   */
  public void expand(URL[] modules, String[] packageNames, boolean[] selectors) throws Exception {
    if (modules == null) {
      modules = new URL[0];
    }

    if (packageNames == null) {
      packageNames = this.generatePackageNames(modules);
    }

    if (selectors == null) {
      selectors = new boolean[modules.length];
      Arrays.fill(selectors, true);
    }

    if (packageNames.length == 1) {
      // assume the same for all
      String packageName = packageNames[0];
      packageNames = new String[modules.length];
      Arrays.fill(packageNames, packageName);
    }

    if (selectors.length == 1) {
      // assume the same for all
      boolean selector = selectors[0];
      selectors = new boolean[modules.length];
      Arrays.fill(selectors, selector);
    }

    if (modules.length != packageNames.length || modules.length != selectors.length) {
      throw new Exception("Different number of modules and packages");
    }

    // And do the expansion.
    this.expand2(modules, packageNames, selectors);
  }

  /**
   * Return the Java package name for the provided module. The module must be part of the set of
   * modules provided in the expansion method.
   *
   * @param module
   * @return Java package name or null if module is unknown
   */
  public String getPackageName(URL module) {
    return this.module2packageName.get(module);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Callback methods for subclasses
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Initial call in the expansion.
   *
   * @throws Exception
   */
  protected void beforeOutput() throws Exception {}

  /**
   * Callback for each package for which code is generated.
   *
   * @param packageName: Java package name
   * @param infoName: package info name
   * @param sourceCode: package info source code
   * @throws Exception
   */
  protected void outputPackage(String packageName, String infoName, StringBuilder sourceCode)
      throws Exception {}

  /**
   * Callback for class, with source code. This method is called when code is generated for the
   * module the class is a member of. I.e. the selector value for the module in the expansion call
   * is true.
   *
   * @param fqOmtName: Fully qualified OMT class or datatype name
   * @param packageName: Java package name
   * @param className: Java class name
   * @param sourceCode: Java class source code
   * @throws Exception
   */
  protected void outputClass(
      String fqOmtName, String packageName, String className, StringBuilder sourceCode)
      throws Exception {}

  /**
   * Callback for class, without source code. This method is called when no code is generated for
   * the module the class is a member of. I.e. the selector value for the module in the expansion
   * call is false.
   *
   * @param fqOmtName: Fully qualified OMT class or datatype name
   * @param packageName: Java package name
   * @param className: Java class name
   * @throws Exception
   */
  protected void outputClass(String fqOmtName, String packageName, String className)
      throws Exception {}

  /**
   * Final callback in the expansion.
   *
   * @throws Exception
   */
  protected void afterOutput() throws Exception {}

  ////////////////////////////////////////////////////////////////////////////
  // Main expansion method
  ////////////////////////////////////////////////////////////////////////////
  protected void expand2(URL[] modules, String[] packageNames, boolean[] selectors)
      throws IOException, Exception {

    // Reset.
    module2packageName.clear();
    omtModule2packageName.clear();
    objectClassMap.clear();
    interactionClassMap.clear();
    objectClassNames.clear();
    interactionClassNames.clear();

    // Set the date of expansion.
    this.date = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());

    // Read the OMT modules.
    this.omtModules = new ObjectModelType[modules.length];
    for (int i = 0; i < modules.length; i++) {
      omtModules[i] = OmtFunctions.readOmt(modules[i]);
    }

    // Update the module maps with the new package names
    for (int i = 0; i < modules.length; i++) {
      this.module2packageName.put(modules[i], packageNames[i]);
      this.omtModule2packageName.put(omtModules[i], packageNames[i]);
    }

    // Get the OMT array datatypes that have the given padding encoding.
    // No code will be generated for fixed record fields that have a datatype that is in this set.
    this.paddingDataTypes =
        OmtFunctions.getArrayDataTypesWithEncoding(
            omtModules, this.properties.getPaddingEncodings());

    // Signal the start.
    this.beforeOutput();

    // Expand modules.
    for (int i = 0; i < omtModules.length; i++) {
      this.currentModule = omtModules[i];
      this.currentPackageName = packageNames[i];

      if (selectors[i]) {
        this.expandModule();
      } else {
        this.declareModule();
      }
    }

    // Signal the end.
    this.afterOutput();
  }

  ////////////////////////////////////////////////////////////////////////////
  // Declare classes of module, without expanding to code
  ////////////////////////////////////////////////////////////////////////////
  private void declareModule() throws Exception {
    this.declareObjectClasses(this.toJavaObjectPackageName(this.currentPackageName));
    this.declareInteractionClasses(this.toJavaInteractionPackageName(this.currentPackageName));
    this.declareDataTypes(this.toDatatypePackageName(this.currentPackageName));
  }

  private void declareObjectClasses(String objectPackageName) throws Exception {
    if (this.currentModule.getObjects() != null
        && this.currentModule.getObjects().getObjectClass() != null) {
      ObjectClass root = this.currentModule.getObjects().getObjectClass();
      this.declareObjectClass(null, root, objectPackageName);
    }
  }

  private void declareObjectClass(String fqParentName, ObjectClass oc, String objectPackageName)
      throws Exception {
    String fqObjectClassName =
        fqParentName == null
            ? oc.getName().getValue()
            : fqParentName + PKG_SEPARATOR + oc.getName().getValue();

    for (ObjectClass suboc : oc.getObjectClass()) {
      this.declareObjectClass(fqObjectClassName, suboc, objectPackageName);
    }

    if (!OmtFunctions.isScaffoldingClass(oc)) {
      String javaClassName = this.toJavaObjectClassName(objectPackageName, fqObjectClassName);
      this.outputClass(fqObjectClassName, objectPackageName, javaClassName);
    }
  }

  private void declareInteractionClasses(String interactionPackageName) throws Exception {
    if (this.currentModule.getInteractions() != null
        && this.currentModule.getInteractions().getInteractionClass() != null) {
      InteractionClass root = this.currentModule.getInteractions().getInteractionClass();
      this.declareInteractionClass(null, root, interactionPackageName);
    }
  }

  private void declareInteractionClass(
      String fqParentName, InteractionClass ic, String interactionPackageName) throws Exception {
    String fqInteractionClassName =
        fqParentName == null
            ? ic.getName().getValue()
            : fqParentName + PKG_SEPARATOR + ic.getName().getValue();

    for (InteractionClass subic : ic.getInteractionClass()) {
      this.declareInteractionClass(fqInteractionClassName, subic, interactionPackageName);
    }

    if (!OmtFunctions.isScaffoldingClass(ic)) {
      String javaClassName =
          this.toJavaInteractionClassName(interactionPackageName, fqInteractionClassName);
      this.outputClass(fqInteractionClassName, interactionPackageName, javaClassName);
    }
  }

  private void declareDataTypes(String datatypePackageName) throws Exception {
    if (this.currentModule.getDataTypes() != null) {

      FixedRecordDataTypesType fixedRecordDataTypesType =
          this.currentModule.getDataTypes().getFixedRecordDataTypes();
      if (fixedRecordDataTypesType != null) {
        List<FixedRecordData> list = fixedRecordDataTypesType.getFixedRecordData();
        for (FixedRecordData fixedRecordData : list) {
          this.outputClass(
              fixedRecordData.getName().getValue(),
              datatypePackageName,
              fixedRecordData.getName().getValue());
        }
      }

      VariantRecordDataTypesType variantRecordDataTypesType =
          this.currentModule.getDataTypes().getVariantRecordDataTypes();
      if (variantRecordDataTypesType != null) {
        List<VariantRecordData> list = variantRecordDataTypesType.getVariantRecordData();
        for (VariantRecordData variantRecordData : list) {
          this.outputClass(
              variantRecordData.getName().getValue(),
              datatypePackageName,
              variantRecordData.getName().getValue());
        }
      }

      EnumeratedDataTypesType enumeratedDataTypesType =
          this.currentModule.getDataTypes().getEnumeratedDataTypes();
      if (enumeratedDataTypesType != null) {
        List<EnumeratedData> list = enumeratedDataTypesType.getEnumeratedData();
        for (EnumeratedData enumeratedData : list) {
          this.outputClass(
              enumeratedData.getName().getValue(),
              datatypePackageName,
              enumeratedData.getName().getValue());
        }
      }
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  // Expand Module
  ////////////////////////////////////////////////////////////////////////////
  private void expandModule() throws Exception {
    this.expandPackageInfo();
    this.expandObjectClasses(this.toJavaObjectPackageName(this.currentPackageName));
    this.expandInteractionClasses(this.toJavaInteractionPackageName(this.currentPackageName));
    this.expandDataTypes();
  }

  ////////////////////////////////////////////////////////////////////////////
  // Expand Package Info
  ////////////////////////////////////////////////////////////////////////////
  private void expandPackageInfo() throws Exception {
    StringBuilder sb = new StringBuilder();

    // NOTICE
    this.buildNotice(sb);

    // PACKAGE
    sb.append("package ").append(this.currentPackageName).append(";").append("\n").append("\n");

    // PROPERTIES
    sb.append("/* Bean Generator properties:").append("\n");
    BeanGeneratorProperties.write(sb, properties);
    sb.append("\n").append("*/").append("\n");

    this.outputPackage(this.currentPackageName, "package-info", sb);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Expand Object Classes in the current module
  ////////////////////////////////////////////////////////////////////////////
  /** Expand the object classes in the current module. */
  private void expandObjectClasses(String objectPackageName) throws Exception {
    if (this.currentModule.getObjects() != null
        && this.currentModule.getObjects().getObjectClass() != null) {
      ObjectClass root = this.currentModule.getObjects().getObjectClass();
      this.expandObjectClass(null, null, root, objectPackageName);
    }
  }

  /**
   * Expand the provided OMT object class.
   *
   * @param fqParentName: fully qualified name of the parent object class, or null if there is no
   *     parent.
   * @param parent: reference to the parent object class, or null if there is no parent.
   * @param oc: object class to be expanded.
   * @throws Exception
   */
  private void expandObjectClass(
      String fqParentName, ObjectClass parent, ObjectClass oc, String objectPackageName)
      throws Exception {
    String fqObjectClassName =
        fqParentName == null
            ? oc.getName().getValue()
            : fqParentName + PKG_SEPARATOR + oc.getName().getValue();

    // Recursively expand subclasses.
    for (ObjectClass suboc : oc.getObjectClass()) {
      this.expandObjectClass(fqObjectClassName, oc, suboc, objectPackageName);
    }

    // If the class is a scaffolding class then do not expand.
    if (OmtFunctions.isScaffoldingClass(oc)) {
      return;
    }

    StringBuilder sb = new StringBuilder();

    // NOTICE
    this.buildNotice(sb);

    // PACKAGE
    sb.append("package ").append(objectPackageName).append(";").append("\n").append("\n");

    // IMPORTS
    this.buildObjectImports(sb, fqParentName, parent, oc);

    // CLASS COMMENTS
    if (oc.getSemantics() != null) {
      sb.append("/**")
          .append("\n")
          .append(" * ")
          .append(oc.getSemantics().getValue())
          .append("\n")
          .append(" */")
          .append("\n")
          .append("\n");
    }

    // CLASS HEADING
    String javaClassName = this.toJavaObjectClassName(objectPackageName, fqObjectClassName);
    sb.append("public class ").append(javaClassName);
    if (parent != null) {
      String javaParentClassName = this.toJavaObjectClassName(objectPackageName, fqParentName);
      sb.append(" extends ").append(javaParentClassName);
    }
    sb.append(" {").append("\n").append("\n");

    // CLASS MEMBERS
    List<String> memberTypeList = new ArrayList();
    List<String> memberNameList = new ArrayList();

    for (Attribute attribute : oc.getAttribute()) {
      buildClassMember(
          sb,
          attribute.getName().getValue(),
          attribute.getDataType().getValue(),
          attribute.getSemantics(),
          memberTypeList,
          memberNameList,
          properties.isUseUnboxedType());
      sb.append("\n");
    }

    // GETTERS and SETTERS
    this.buildGettersAndSetters(sb, memberTypeList, memberNameList);

    // END
    sb.append("}").append("\n");

    // Output the data
    this.outputClass(fqObjectClassName, objectPackageName, javaClassName, sb);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Expand Interaction Classes in the current module
  ////////////////////////////////////////////////////////////////////////////
  /** Expand the interaction classes in the current module. */
  private void expandInteractionClasses(String interactionPackageName) throws Exception {
    if (this.currentModule.getInteractions() != null
        && this.currentModule.getInteractions().getInteractionClass() != null) {
      InteractionClass root = this.currentModule.getInteractions().getInteractionClass();
      this.expandInteractionClass(null, null, root, interactionPackageName);
    }
  }

  /**
   * Expand the provided OMT interaction class.
   *
   * @param fqParentName: fully qualified name of the parent interaction, or null if there is no
   *     parent.
   * @param parent: reference to the parent interaction class, or null if there is no parent.
   * @param oc: interaction class to be expanded.
   * @throws Exception
   */
  private void expandInteractionClass(
      String fqParentName,
      InteractionClass parent,
      InteractionClass ic,
      String interactionPackageName)
      throws Exception {
    String fqInteractionClassName =
        fqParentName == null
            ? ic.getName().getValue()
            : fqParentName + PKG_SEPARATOR + ic.getName().getValue();

    // Recursively expand subclasses.
    for (InteractionClass subic : ic.getInteractionClass()) {
      this.expandInteractionClass(fqInteractionClassName, ic, subic, interactionPackageName);
    }

    // If the class is a scaffolding class then do not expand.
    if (OmtFunctions.isScaffoldingClass(ic)) {
      return;
    }

    StringBuilder sb = new StringBuilder();

    // NOTICE
    this.buildNotice(sb);

    // PACKAGE
    sb.append("package ").append(interactionPackageName).append(";").append("\n").append("\n");

    // IMPORTS
    this.buildInteractionImports(sb, fqParentName, parent, ic);

    // COMMENTS
    if (ic.getSemantics() != null) {
      sb.append("/**")
          .append("\n")
          .append(" * ")
          .append(ic.getSemantics().getValue())
          .append("\n")
          .append(" */")
          .append("\n")
          .append("\n");
    }

    // CLASS HEADING
    String javaClassName =
        this.toJavaInteractionClassName(interactionPackageName, fqInteractionClassName);
    sb.append("public class ").append(javaClassName);
    if (parent != null) {
      String javaParentClassName =
          this.toJavaInteractionClassName(interactionPackageName, fqParentName);
      sb.append(" extends ").append(javaParentClassName);
    }
    sb.append(" {").append("\n").append("\n");

    // MEMBERS
    List<String> memberTypeList = new ArrayList();
    List<String> memberNameList = new ArrayList();

    for (Parameter p : ic.getParameter()) {
      buildClassMember(
          sb,
          p.getName().getValue(),
          p.getDataType().getValue(),
          p.getSemantics(),
          memberTypeList,
          memberNameList,
          properties.isUseUnboxedType());
      sb.append("\n");
    }

    // GETTERS and SETTERS
    this.buildGettersAndSetters(sb, memberTypeList, memberNameList);

    // END
    sb.append("}").append("\n");

    // Output the data
    this.outputClass(fqInteractionClassName, interactionPackageName, javaClassName, sb);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Expand Datatypes
  ////////////////////////////////////////////////////////////////////////////
  /** Expand the data types in the current OMT module. */
  private void expandDataTypes() throws Exception {
    if (this.currentModule.getDataTypes() != null) {

      FixedRecordDataTypesType fixedRecordDataTypesType =
          this.currentModule.getDataTypes().getFixedRecordDataTypes();
      if (fixedRecordDataTypesType != null) {
        List<FixedRecordData> list = fixedRecordDataTypesType.getFixedRecordData();
        for (FixedRecordData fixedRecordData : list) {
          this.expandFixedRecordDataType(fixedRecordData);
        }
      }

      VariantRecordDataTypesType variantRecordDataTypesType =
          this.currentModule.getDataTypes().getVariantRecordDataTypes();
      if (variantRecordDataTypesType != null) {
        List<VariantRecordData> list = variantRecordDataTypesType.getVariantRecordData();
        for (VariantRecordData variantRecordData : list) {
          this.expandVariantRecordDataType(variantRecordData);
        }
      }

      EnumeratedDataTypesType enumeratedDataTypesType =
          this.currentModule.getDataTypes().getEnumeratedDataTypes();
      if (enumeratedDataTypesType != null) {
        List<EnumeratedData> list = enumeratedDataTypesType.getEnumeratedData();
        for (EnumeratedData enumeratedData : list) {
          this.expandEnumeratedDataType(enumeratedData);
        }
      }
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  // Expand Fixed Record Datatype
  ////////////////////////////////////////////////////////////////////////////
  private void expandFixedRecordDataType(FixedRecordData fixedRecordData) throws Exception {
    StringBuilder sb = new StringBuilder();

    this.buildNotice(sb);

    sb.append("package ")
        .append(this.toDatatypePackageName(this.currentPackageName))
        .append(";")
        .append("\n")
        .append("\n");

    this.buildFixedRecordImports(sb, fixedRecordData);

    sb.append("/**").append("\n");
    sb.append(" * ").append(fixedRecordData.getSemantics().getValue()).append("\n");
    sb.append(" */").append("\n");
    sb.append("\n");

    sb.append("public class ")
        .append(fixedRecordData.getName().getValue())
        .append(" {")
        .append("\n")
        .append("\n");

    List<String> memberTypeList = new ArrayList();
    List<String> memberNameList = new ArrayList();

    for (Field f : fixedRecordData.getField()) {
      // Ignore this field if it has a padding type.
      if (!this.paddingDataTypes.contains(f.getDataType().getValue())) {
        buildClassMember(
            sb,
            f.getName().getValue(),
            f.getDataType().getValue(),
            f.getSemantics(),
            memberTypeList,
            memberNameList,
            true);
        sb.append("\n");
      }
    }

    // Print getters and setters.
    this.buildGettersAndSetters(sb, memberTypeList, memberNameList);

    sb.append("}").append("\n");

    // Output the data.
    this.outputClass(
        fixedRecordData.getName().getValue(),
        this.toDatatypePackageName(this.currentPackageName),
        fixedRecordData.getName().getValue(),
        sb);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Expand Variant Record Datatype
  ////////////////////////////////////////////////////////////////////////////
  private void expandVariantRecordDataType(VariantRecordData variantRecordData) throws Exception {
    StringBuilder sb = new StringBuilder();

    this.buildNotice(sb);

    sb.append("package ")
        .append(this.toDatatypePackageName(this.currentPackageName))
        .append(";")
        .append("\n")
        .append("\n");

    this.buildVariantRecordImports(sb, variantRecordData);

    sb.append("/**").append("\n");
    sb.append(" * ").append(variantRecordData.getSemantics().getValue()).append("\n");
    sb.append(" */").append("\n").append("\n");

    sb.append("public class ")
        .append(variantRecordData.getName().getValue())
        .append(" {")
        .append("\n")
        .append("\n");

    // Map the field name and datatype name to Java
    String javaDiscriminantName =
        OmtJavaMapping.toJavaName(variantRecordData.getDiscriminant().getValue());
    String javaDatatypeName =
        OmtJavaMapping.getJavaDatatypeNameForEnumerationType(
            this.omtModules, variantRecordData.getDataType().getValue(), false);

    sb.append("\t/** Discriminant */").append("\n");
    sb.append("\t")
        .append(this.properties.isUsePublicModifier() ? "public " : "")
        .append(javaDatatypeName)
        .append(" ")
        .append(javaDiscriminantName)
        .append(";")
        .append("\n")
        .append("\n");

    List<String> memberTypeList = new ArrayList();
    List<String> memberNameList = new ArrayList();

    memberTypeList.add(javaDatatypeName);
    memberNameList.add(javaDiscriminantName);

    for (Alternative f : variantRecordData.getAlternative()) {
      this.buildClassMember(
          sb,
          f.getName().getValue(),
          f.getDataType().getValue(),
          f.getSemantics(),
          memberTypeList,
          memberNameList,
          true);
      sb.append("\n");
    }

    // Print getters and setters.
    this.buildGettersAndSetters(sb, memberTypeList, memberNameList);

    sb.append("}").append("\n");

    // Output the data.
    this.outputClass(
        variantRecordData.getName().getValue(),
        this.toDatatypePackageName(this.currentPackageName),
        variantRecordData.getName().getValue(),
        sb);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Expand Enumerated Datatype
  ////////////////////////////////////////////////////////////////////////////
  /**
   * This method constructs a comma-separated list of enumerator values. The OMT template allows a
   * list of values per enumerator and this method concatenate them in a single string. Each value
   * is preceded by a type cast, which is an empty string if no type cast is needed.
   */
  private String getEnumeratorValues(Enumerator enumerator, String typeCast) {
    StringBuilder sb = new StringBuilder();
    for (HLAString s : enumerator.getValue()) {
      if (sb.isEmpty()) {
        sb.append(typeCast).append(s.getValue());
      } else {
        sb.append(",").append(typeCast).append(s.getValue());
      }
    }

    return sb.toString();
  }

  /** This method expands and outputs an enumerated datatype. */
  private void expandEnumeratedDataType(EnumeratedData enumeratedData) throws Exception {
    StringBuilder sb = new StringBuilder();

    this.buildNotice(sb);

    sb.append("package ")
        .append(this.toDatatypePackageName(this.currentPackageName))
        .append(";")
        .append("\n")
        .append("\n");

    sb.append("/**").append("\n");
    sb.append(" * ").append(enumeratedData.getSemantics().getValue()).append("\n");
    sb.append(" */").append("\n").append("\n");

    // Generate Class heading
    String enumClassName = enumeratedData.getName().getValue();

    sb.append("public enum ").append(enumClassName).append(" {").append("\n").append("\n");

    // Generate Enumerators
    String javaDatatypeName =
        OmtJavaMapping.getJavaDatatypeNameForRepresentation(
            enumeratedData.getRepresentation().getValue(), false);
    String typeCast =
        javaDatatypeName.equals(OmtJavaMapping.JavaPrimitiveType.INTEGER.getUnboxedType())
            ? ""
            : "(" + javaDatatypeName + ")";

    Iterator<Enumerator> iter = enumeratedData.getEnumerator().iterator();

    // add HLA_UNKNOWN_ENUM(0);
    sb.append("\t")
        .append(OmtJavaMapping.toJavaName(HLA_UNKNOWN_ENUM))
        .append("(")
        .append(typeCast)
        .append(0)
        .append(")")
        .append(iter.hasNext() ? "," : ";")
        .append("\n");

    while (iter.hasNext()) {
      Enumerator e = iter.next();

      if (e.getValue().isEmpty()) {
        throw new Exception(
            "Enumerator "
                + enumClassName
                + PKG_SEPARATOR
                + e.getName().getValue()
                + " does not have a value");
      }

      String javaEnumeratorName = OmtJavaMapping.toJavaName(e.getName().getValue());

      // <javaEnumeratorName>(<values>);
      sb.append("\t")
          .append(javaEnumeratorName)
          .append("(")
          .append(getEnumeratorValues(e, typeCast))
          .append(")")
          .append(iter.hasNext() ? "," : ";")
          .append("\n");
    }

    sb.append("\n");

    // Generate Private value
    sb.append("\tprivate final ")
        .append(javaDatatypeName)
        .append(" value;")
        .append("\n")
        .append("\n");
    sb.append("\tprivate final ")
        .append(javaDatatypeName)
        .append("[] values;")
        .append("\n")
        .append("\n");

    // Generate Constructor using array of value and hence a type signature for ARRAY of
    // javaDatatypeName
    sb.append("\tprivate ")
        .append(enumClassName)
        .append("(")
        .append(javaDatatypeName)
        .append("... values) {")
        .append("\n");

    sb.append("\t\tthis.value = values[0];").append("\n");
    sb.append("\t\tthis.values = values;").append("\n");
    sb.append("\t}").append("\n").append("\n");

    // Generate Getter getValue()
    sb.append("\tpublic ").append(javaDatatypeName).append(" getValue() {").append("\n");
    sb.append("\t\treturn value;").append("\n");
    sb.append("\t}").append("\n");

    // Generate Getter getValues()
    sb.append("\tpublic ").append(javaDatatypeName).append("[] getValues() {").append("\n");
    sb.append("\t\treturn values;").append("\n");
    sb.append("\t}").append("\n");

    sb.append("}").append("\n");

    // Output the data
    this.outputClass(
        enumeratedData.getName().getValue(),
        this.toDatatypePackageName(this.currentPackageName),
        enumeratedData.getName().getValue(),
        sb);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Create the file
  ////////////////////////////////////////////////////////////////////////////
  /**
   * This method creates a source code file. The source code is formatted before being output to the
   * file.
   */
  protected void createFile(String fileName, StringBuilder sb) throws IOException {
    // format the code
    String sourceCode;
    try {
      sourceCode = new Formatter().formatSource(sb.toString());
    } catch (FormatterException ex) {
      Logger.getLogger(BeanGenerator.class.getName()).log(Level.SEVERE, null, ex);
      sourceCode = sb.toString();
    }

    File outputFile = new File(fileName);
    outputFile.getParentFile().mkdirs();
    outputFile.createNewFile();
    try (PrintWriter pw = new PrintWriter(outputFile)) {
      pw.append(sourceCode);
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  // Build methods
  ////////////////////////////////////////////////////////////////////////////
  private void buildNotice(StringBuilder sb) {
    if (!this.properties.isUseNoNotice()) {
      sb.append("// This file was generated by the TNO Bean Generator.").append("\n");
      sb.append("// Any modifications to this file will be lost upon re-generation.").append("\n");
      sb.append("// Generated on: ").append(this.date).append("\n").append("\n");
    }
  }

  private void buildImports(StringBuilder sb, Set<String> imports) {
    if (!imports.isEmpty()) {
      List<String> sortedList = new ArrayList(imports);
      java.util.Collections.sort(sortedList);
      for (String importItem : sortedList) {
        sb.append("import ").append(importItem).append(";\n");
      }
      sb.append("\n");
    }
  }

  private void buildClassMember(
      StringBuilder sb,
      String fieldName,
      String datatypeName,
      HLAString semantics,
      List<String> memberTypeList,
      List<String> memberNameList,
      boolean unboxed)
      throws Exception {
    if (semantics != null) {
      sb.append("\t/** ").append(semantics.getValue()).append(" */").append("\n");
    }

    int dim = 0;
    ArrayData arrayData = OmtFunctions.getArrayDataByName(this.omtModules, datatypeName);

    // Drill down the array and count the dimensions.
    while (arrayData != null) {
      if (OmtJavaMapping.getJavaDatatypeName(datatypeName) != null) {
        // If a specific mapping is specified, then break.
        break;
      }

      // If a String datatype is met, then break. A String datatype is an array of charachters.
      if (arrayData.getDataType().getValue().equals(OmtMimConstants.HLAUNICODECHAR)
          || arrayData.getDataType().getValue().equals(OmtMimConstants.HLAASCIICHAR)) {
        break;
      }

      dim++;
      datatypeName = arrayData.getDataType().getValue();
      arrayData = OmtFunctions.getArrayDataByName(this.omtModules, datatypeName);
    }

    // Get the Java data type name, taking into account the expansion properties (list, box type)
    String javaDatatypeName =
        OmtJavaMapping.getJavaDatatypeName(
            this.omtModules, datatypeName, dim, this.properties.isUseList(), !unboxed);
    String javaVariableName = OmtJavaMapping.toJavaName(fieldName);

    sb.append("\t")
        .append(this.properties.isUsePublicModifier() ? "public " : "")
        .append(javaDatatypeName)
        .append(" ")
        .append(javaVariableName)
        .append(";")
        .append("\n");

    memberTypeList.add(javaDatatypeName);
    memberNameList.add(javaVariableName);
  }

  /**
   * This method determines the set of Java import statements for the provided object class. An
   * import statement is needed for each referenced class that is not in the current package. This
   * concerns: a parent that is a scaffolding class and data types.
   *
   * @param sb
   * @param fqParentName: FQ name of the parent class, or null if there is no parent.
   * @param parent: reference to the parent class, or null if there is no parent.
   * @param oc: the object class to determine the import statements for.
   * @throws Exception
   */
  private void buildObjectImports(
      StringBuilder sb, String fqParentName, ObjectClass parent, ObjectClass oc) throws Exception {
    Set<String> processedNames = new HashSet();
    Set<String> imports = new HashSet();

    // If the parent is a scaffolding class then generate an import for that class.
    if (parent != null && OmtFunctions.isScaffoldingClass(parent)) {

      // get the module where the class is defined in
      ObjectModelType module = this.getObjectClassModule(fqParentName);

      if (module != null) {
        String otherPackageName = this.omtModule2packageName.get(module);
        if (!otherPackageName.equals(this.currentPackageName)) {
          // The class is in another package, hence we need an import
          String otherObjectPackageName = this.toJavaObjectPackageName(otherPackageName);
          imports.add(
              otherObjectPackageName
                  + PKG_SEPARATOR
                  + this.toJavaObjectClassName(otherObjectPackageName, fqParentName));
        }
      } else {
        throw new Exception("Class " + parent.getName().getValue() + " not defined in any module");
      }
    }

    // Get datatype imports.
    String currentObjectPackageName = this.toJavaObjectPackageName(this.currentPackageName);
    for (Attribute a : oc.getAttribute()) {
      String dataTypeName = a.getDataType().getValue();
      this.getDatatypeImport(currentObjectPackageName, dataTypeName, imports, processedNames);
    }

    // Build results.
    this.buildImports(sb, imports);
  }

  private void buildInteractionImports(
      StringBuilder sb, String fqParentName, InteractionClass parent, InteractionClass ic)
      throws Exception {
    Set<String> processedNames = new HashSet();
    Set<String> imports = new HashSet();

    // If the parent is a scaffolding class then generate an import for that class.
    if (parent != null && OmtFunctions.isScaffoldingClass(parent)) {

      // get the module where the class is defined in
      ObjectModelType module = this.getInteractionClassModule(fqParentName);

      if (module != null) {
        String otherPackageName = this.omtModule2packageName.get(module);
        if (!otherPackageName.equals(this.currentPackageName)) {
          // The class is in another package, hence we need an import
          String otherInteractionPackageName = this.toJavaInteractionPackageName(otherPackageName);
          imports.add(
              otherInteractionPackageName
                  + PKG_SEPARATOR
                  + this.toJavaInteractionClassName(otherInteractionPackageName, fqParentName));
        }
      } else {
        throw new Exception("Class " + parent.getName().getValue() + " not defined in any module");
      }
    }

    // Get datatype imports.
    String currentInteractionPackageName =
        this.toJavaInteractionPackageName(this.currentPackageName);
    for (Parameter p : ic.getParameter()) {
      String dataTypeName = p.getDataType().getValue();
      this.getDatatypeImport(currentInteractionPackageName, dataTypeName, imports, processedNames);
    }

    // Build results.
    this.buildImports(sb, imports);
  }

  private ObjectModelType getObjectClassModule(String fqName) {
    if (OmtFunctions.getObjectClass(this.currentModule, fqName) != null) {
      return this.currentModule;
    } else {
      return OmtFunctions.getObjectClassModule(this.omtModules, fqName);
    }
  }

  private ObjectModelType getInteractionClassModule(String fqName) {
    if (OmtFunctions.getInteractionClass(this.currentModule, fqName) != null) {
      return this.currentModule;
    } else {
      return OmtFunctions.getInteractionClassModule(this.omtModules, fqName);
    }
  }

  private void buildFixedRecordImports(StringBuilder sb, FixedRecordData rec) throws Exception {
    Set<String> processedNames = new HashSet();
    Set<String> imports = new HashSet<>();

    // Get field datatype imports.
    for (Field f : rec.getField()) {
      String dataTypeName = f.getDataType().getValue();
      this.getDatatypeImport(
          this.toDatatypePackageName(this.currentPackageName),
          dataTypeName,
          imports,
          processedNames);
    }

    // Build results.
    this.buildImports(sb, imports);
  }

  private void buildVariantRecordImports(StringBuilder sb, VariantRecordData rec) throws Exception {
    Set<String> processedNames = new HashSet();
    Set<String> imports = new HashSet<>();

    getDatatypeImport(
        this.toDatatypePackageName(this.currentPackageName),
        rec.getDataType().getValue(),
        imports,
        processedNames);

    // Get alternative datatype imports.
    for (Alternative a : rec.getAlternative()) {
      String dataTypeName = a.getDataType().getValue();
      this.getDatatypeImport(
          this.toDatatypePackageName(this.currentPackageName),
          dataTypeName,
          imports,
          processedNames);
    }

    // Build results.
    this.buildImports(sb, imports);
  }

  /**
   * Determine the imports for the provided data type name.
   *
   * @param packageName: package that the data type is imported in.
   * @param datatypeName: the imported data type name.
   * @param imports: imports are added to this set.
   * @param processedNames: data type names already processed.
   * @throws Exception
   */
  private void getDatatypeImport(
      String packageName, String datatypeName, Set<String> imports, Set<String> processedNames)
      throws Exception {
    // Check if the datatypName is mapped to a specific Java type
    if (OmtJavaMapping.getJavaDatatypeName(datatypeName) == null) {
      ArrayData arrayData = OmtFunctions.getArrayDataByName(this.omtModules, datatypeName);
      // if the datatypeName is an array datatype then drill down
      if (arrayData != null) {
        int dim = 0;

        // Drill down the array.
        while (arrayData != null) {
          // If a specific data type is specified, break.
          if (OmtJavaMapping.getJavaDatatypeName(datatypeName) != null) {
            break;
          }

          // If a String data type is specified, break.
          if (arrayData.getDataType().getValue().equals(OmtMimConstants.HLAUNICODECHAR)
              || arrayData.getDataType().getValue().equals(OmtMimConstants.HLAASCIICHAR)) {
            break;
          }

          dim++;
          datatypeName = arrayData.getDataType().getValue();
          arrayData = OmtFunctions.getArrayDataByName(this.omtModules, datatypeName);
        }

        if (dim > 0) {
          // Use "[]" to indicate that we only import the List class once.
          if (!processedNames.contains("[]")) {
            processedNames.add("[]");
            if (this.properties.isUseList()) {
              imports.add("java.util.List");
            }
          }
        }
      }
    }

    if (processedNames.add(datatypeName)) {

      JavaDataType javaDataType = OmtJavaMapping.getJavaDatatypeName(datatypeName);
      if (javaDataType != null) {
        // Import for specific mapping.
        imports.addAll(javaDataType.getImports());
        return;
      }

      ObjectModelType module = this.getSimpleDataModule(datatypeName);
      if (module != null) {
        // No import for Java simple type.
        return;
      }

      module = this.getArrayDataModule(datatypeName);
      if (module != null) {
        // We only will get here when breaked out of the arrayData loop above.
        // I.e. datatype is String (no import).
        return;
      }

      module = this.getEnumeratedDataModule(datatypeName);
      if (module != null) {
        String otherPackageName =
            this.toDatatypePackageName(this.omtModule2packageName.get(module));
        if (!otherPackageName.equals(packageName)) {
          // No import for HLAboolean, which is mapped to Java boolean type.
          if (!datatypeName.equals(OmtMimConstants.HLABOOLEAN)) {
            imports.add(otherPackageName + PKG_SEPARATOR + datatypeName);
          }
        }
        return;
      }

      module = this.getFixedRecordDataModule(datatypeName);
      if (module != null) {
        String datatypePackageName =
            this.toDatatypePackageName(this.omtModule2packageName.get(module));
        if (!datatypePackageName.equals(packageName)) {
          imports.add(datatypePackageName + PKG_SEPARATOR + datatypeName);
        }
        return;
      }

      module = this.getVariantRecordDataModule(datatypeName);
      if (module != null) {
        String datatypePackageName =
            this.toDatatypePackageName(this.omtModule2packageName.get(module));
        if (!datatypePackageName.equals(packageName)) {
          imports.add(datatypePackageName + PKG_SEPARATOR + datatypeName);
        }
        return;
      }

      // Unknown datatype.
      throw new Exception("Datatype " + datatypeName + " not found in any module");
    }
  }

  private ObjectModelType getSimpleDataModule(String datatypeName) {
    if (OmtFunctions.getSimpleDataByName(this.currentModule, datatypeName) != null) {
      return this.currentModule;
    } else {
      return OmtFunctions.getSimpleDataModule(this.omtModules, datatypeName);
    }
  }

  private ObjectModelType getEnumeratedDataModule(String datatypeName) {
    if (OmtFunctions.getEnumeratedDataByName(currentModule, datatypeName) != null) {
      return currentModule;
    } else {
      return OmtFunctions.getEnumeratedDataModule(this.omtModules, datatypeName);
    }
  }

  private ObjectModelType getArrayDataModule(String datatypeName) {
    if (OmtFunctions.getArrayDataByName(currentModule, datatypeName) != null) {
      return currentModule;
    } else {
      return OmtFunctions.getArrayDataModule(this.omtModules, datatypeName);
    }
  }

  private ObjectModelType getFixedRecordDataModule(String datatypeName) {
    if (OmtFunctions.getFixedRecordDataByName(this.currentModule, datatypeName) != null) {
      return this.currentModule;
    } else {
      return OmtFunctions.getFixedRecordDataModule(this.omtModules, datatypeName);
    }
  }

  private ObjectModelType getVariantRecordDataModule(String datatypeName) {
    if (OmtFunctions.getVariantRecordDataByName(currentModule, datatypeName) != null) {
      return currentModule;
    } else {
      return OmtFunctions.getVariantRecordDataModule(this.omtModules, datatypeName);
    }
  }

  private void buildGettersAndSetters(
      StringBuilder sb, List<String> memberTypeList, List<String> memberNameList) {
    for (int i = 0; i < memberTypeList.size(); i++) {
      String name = OmtJavaMapping.toJavaName(memberNameList.get(i));

      sb.append("\tpublic ")
          .append(memberTypeList.get(i))
          .append(" ")
          .append(OmtJavaMapping.toJavaGetterName(name))
          .append("() {")
          .append("\n");
      sb.append("\t\treturn this.").append(memberNameList.get(i)).append(";").append("\n");
      sb.append("\t}").append("\n");
      sb.append("\n");

      sb.append("\tpublic void ")
          .append(OmtJavaMapping.toJavaSetterName(name))
          .append("(")
          .append(memberTypeList.get(i))
          .append(" ")
          .append(memberNameList.get(i))
          .append(") {")
          .append("\n");
      sb.append("\t\tthis.")
          .append(memberNameList.get(i))
          .append(" = ")
          .append(memberNameList.get(i))
          .append(";")
          .append("\n");
      sb.append("\t}").append("\n");
      sb.append("\n");
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  // Support methods
  ////////////////////////////////////////////////////////////////////////////
  private String toJavaObjectClassName1(String fqClassName, boolean useFQclassName) {
    if (useFQclassName) {
      return fqClassName
          .replaceAll("\\.", "_")
          .replaceFirst(OmtMimConstants.HLAOBJECTROOT + ".", "");
    } else {
      // note that lastIndexOf return -1 if there is no last index, which correctly yields zero in
      // substring
      return fqClassName.substring(fqClassName.lastIndexOf(".") + 1);
    }
  }

  /**
   * Return the Java class name for the provided FQ OMT class name. The mapping is preserved so that
   * the next call will return the previously provided value. This is important as the expansion
   * property to use FQ class names may chance between expansion calls. A next expansion call must
   * use the previously returned value.
   *
   * @param fqClassName
   * @return
   */
  private String toJavaObjectClassName(String javaPackageName, String fqClassName) {
    String javaClassName = this.objectClassMap.get(fqClassName);
    if (javaClassName == null) {
      javaClassName = this.toJavaObjectClassName1(fqClassName, this.properties.isUseFQclassName());
      if (this.objectClassNames.add(javaPackageName + PKG_SEPARATOR + javaClassName)) {
        this.objectClassMap.put(fqClassName, javaClassName);
      } else {
        Logger.getLogger(BeanGenerator.class.getName())
            .log(
                Level.WARNING,
                "Java class {0} for OMT class name {1} was already used before (FQ name used instead)",
                new Object[] {javaPackageName + PKG_SEPARATOR + javaClassName, fqClassName});

        javaClassName = this.toJavaObjectClassName1(fqClassName, true);
        this.objectClassNames.add(javaPackageName + PKG_SEPARATOR + javaClassName);
        this.objectClassMap.put(fqClassName, javaClassName);
      }
    }

    return javaClassName;
  }

  private String toJavaInteractionClassName1(String fqClassName, boolean useFQclassName) {
    if (useFQclassName) {
      return fqClassName
          .replaceAll("\\.", "_")
          .replaceFirst(OmtMimConstants.HLAINTERACTIONROOT + ".", "");
    } else {
      // note that lastIndexOf return -1 if there is no last index, which correctly yields zero in
      // substring
      return fqClassName.substring(fqClassName.lastIndexOf(".") + 1);
    }
  }

  /** See toJavaObjectClassName. */
  private String toJavaInteractionClassName(String javaPackageName, String fqClassName) {
    String javaClassName = this.interactionClassMap.get(fqClassName);
    if (javaClassName == null) {
      javaClassName =
          this.toJavaInteractionClassName1(fqClassName, this.properties.isUseFQclassName());
      if (this.interactionClassNames.add(javaPackageName + PKG_SEPARATOR + javaClassName)) {
        this.interactionClassMap.put(fqClassName, javaClassName);
      } else {
        Logger.getLogger(BeanGenerator.class.getName())
            .log(
                Level.WARNING,
                "Java class {0} for OMT class name {1} was already used before (FQ name used instead)",
                new Object[] {javaPackageName + PKG_SEPARATOR + javaClassName, fqClassName});

        javaClassName = this.toJavaInteractionClassName1(fqClassName, true);
        this.interactionClassNames.add(javaPackageName + PKG_SEPARATOR + javaClassName);
        this.interactionClassMap.put(fqClassName, javaClassName);
      }
    }

    return javaClassName;
  }

  /** Determine object package name based on inputs. */
  private String toJavaObjectPackageName(String rootPackageName) {
    return rootPackageName + PKG_SEPARATOR + OBJECTS_PACKAGENAME;
  }

  /** Determine interaction package name based on inputs. */
  private String toJavaInteractionPackageName(String rootPackageName) {
    return rootPackageName + PKG_SEPARATOR + INTERACTIONS_PACKAGENAME;
  }

  /** Determine datatype package name based on inputs. */
  private String toDatatypePackageName(String rootPackageName) {
    return rootPackageName + PKG_SEPARATOR + DATATYPES_PACKAGENAME;
  }

  /** Generate a random Java package name for each module. */
  private String[] generatePackageNames(URL[] modules) {
    String groupId =
        this.properties.getGroupId() == null
            ? ""
            : this.properties.getGroupId().endsWith(PKG_SEPARATOR)
                ? this.properties.getGroupId()
                : this.properties.getGroupId() + PKG_SEPARATOR;
    String[] packageNames = new String[modules.length];

    for (int i = 0; i < modules.length; i++) {
      URL url = modules[i];
      String moduleName = new File(url.getPath()).getName();

      boolean match = false;
      for (String regex : this.properties.getDefaultPackageNames().keySet()) {
        if (moduleName.toLowerCase().matches(regex)) {
          match = true;
          packageNames[i] = groupId + this.properties.getDefaultPackageNames().get(regex);
          break;
        }
      }

      if (!match) {
        // remove any .xml extension
        moduleName =
            moduleName.endsWith(".xml")
                ? moduleName.substring(0, moduleName.indexOf(".xml"))
                : moduleName;

        // remove chars we do not want
        moduleName = moduleName.toLowerCase().replace('-', '_').replaceAll("[^a-z0-9_]", "");

        // generate a name
        moduleName = "mod" + new Random().nextInt((1 << 31) - 1) + "_" + moduleName;

        packageNames[i] = groupId + moduleName;
      }
    }

    return packageNames;
  }
}
