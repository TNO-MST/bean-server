package nl.tno.beancompiler;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import nl.tno.beangenerator.BeanGenerator;
import nl.tno.beangenerator.BeanGeneratorProperties;

/**
 * The BeanCompiler extends the BeanGenerator by compiling the source code that the Bean Generator
 * outputs. The compilation is in-memory and the resulting class code is added to a custom Bean
 * ClassLoader. This Bean ClassLoader must be used to instantiate any of the dynamic classes.
 *
 * <p>References:
 *
 * <p>http://javapracs.blogspot.com/2011/06/dynamic-in-memory-compilation-using.html
 *
 * <p>https://www.logicbig.com/tutorials/core-java-tutorial/java-se-compiler-api/compiler-api-memory-loader.html
 *
 * <p>>https://github.com/OpenHFT/Java-Runtime-Compiler https://github.com/dvare/dynamic-loader
 *
 * @author bergtwvd
 */
public class BeanCompiler extends BeanGenerator {

  // Constants for dynamic getting the lookup, in the context of the Bean ClassLoader
  static final String LOOKUP_CLASS = "GetLookup";
  static final String LOOKUP_PACKAGE = "nl.tno.beancompiler";
  static final String LOOKUP_FIELD = "lookup";

  // Source code objects, to be compiled.
  private final Set<JavaSourceObject> javaSourceObjects = new HashSet();

  // Set of processed classes, plus the mapping to the associated Java Class name.
  private final Map<String, String> compiledClassNames = new HashMap();
  private final Map<String, String> referencedClassNames = new HashMap();

  private final JavaCompiler compiler;
  private final JavaClassObjectFileManager fileManager;
  private final StandardJavaFileManager standardFileManager;
  private final DiagnosticCollector<JavaFileObject> diagnostics;
  private final BeanClassLoader classLoader;

  private final Set<URL> jars = new HashSet();

  ////////////////////////////////////////////////////////////////////////////
  // Public constructors
  ////////////////////////////////////////////////////////////////////////////
  public BeanCompiler() throws Exception {
    this(new BeanGeneratorProperties());
  }

  public BeanCompiler(BeanGeneratorProperties properties) throws Exception {
    super(properties);
    this.compiler = ToolProvider.getSystemJavaCompiler();
    this.standardFileManager = this.compiler.getStandardFileManager(null, null, null);
    this.fileManager = new JavaClassObjectFileManager(standardFileManager);
    this.diagnostics = new DiagnosticCollector<>();
    this.classLoader = new BeanClassLoader(this.fileManager);

    Logger.getLogger(BeanCompiler.class.getName())
        .log(
            Level.INFO,
            "Latest supported compiler source version: {0}",
            SourceVersion.latestSupported());
  }

  ////////////////////////////////////////////////////////////////////////////
  // Public methods
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Compiles modules.
   *
   * @param modules FOM modules to expand and compile.
   * @param packageNames: Java package names to use in the expansion.
   * @param selectors: selectors, indicating what modules to expand and compile.
   * @param jars JARs with previously compiled FOM modules that will be added to the class loader.
   * @throws Exception
   */
  public void expand(URL[] modules, String[] packageNames, boolean[] selectors, URL[] jars)
      throws Exception {
    if (jars != null) {
      for (URL jar : jars) {
        this.jars.add(jar);
        this.classLoader.addURL(jar);
      }
    }

    if (modules != null) {
      Logger.getLogger(BeanCompiler.class.getName())
          .log(Level.INFO, "Expanding {0} modules:", modules.length);
      for (URL module : modules) {
        Logger.getLogger(BeanCompiler.class.getName()).log(Level.INFO, " {0}", module.toString());
      }
    }

    super.expand(modules, packageNames, selectors);
  }

  @Override
  protected void expand2(URL[] modules, String[] packageNames, boolean[] selectors)
      throws Exception {

    // Clear the file manager's data store before each new expansion so that we do not get the data
    // from previous expansions.
    this.fileManager.clear();

    // Compile the lookup class.
    this.compileLookupClass();

    // Create a list of referenced modules in order to compile these first.
    int nrReferences = 0;
    for (boolean selector : selectors) {
      nrReferences += (selector ? 0 : 1);
    }

    URL refModules[] = new URL[nrReferences];
    String refPackageNames[] = new String[nrReferences];
    boolean refSelectors[] = new boolean[nrReferences];

    int j = 0;
    for (int i = 0; i < modules.length; i++) {
      if (!selectors[i]) {
        refModules[j] = modules[i];
        refPackageNames[j] = packageNames[i];
        refSelectors[j] = true;
        j++;
      }
    }

    // 
    super.expand2(refModules, refPackageNames, refSelectors);

    // NOTE: the file manager keeps the compiled objects between successive expansion calls.
    // That is why the references get compiled first so that the Java compiler knows about these
    // when the rest is compiled.
    super.expand2(modules, packageNames, selectors);
  }

  /**
   * Gets the Bean ClassLoader.
   *
   * @return Bean ClassLoader
   */
  public BeanClassLoader getClassLoader() {
    return this.classLoader;
  }

  /**
   * Gets the MethodHandles.Lookup from the context of the Bean ClassLoader.
   *
   * <p>The returned lookup context may be used by the Lambda Accessor factory in the accessor
   * package of the OORTI to obtain handles of dynamic classes.
   *
   * <p>This method gets the value of the LOOKUP_FIELD via Reflection. The field provides the lookup
   * context of the LOOKUP_CLASS, loaded in the context of the Bean ClassLoader. The LOOKUP_CLASS
   * itself is a dynamic class, compiled along with the other Bean classes.
   *
   * @return MethodHandles.Lookup
   * @throws Exception
   */
  public MethodHandles.Lookup getMethodHandlesLookup() throws Exception {
    return (MethodHandles.Lookup)
        this.classLoader
            .loadClass(LOOKUP_PACKAGE + PKG_SEPARATOR + LOOKUP_CLASS)
            .getField(LOOKUP_FIELD)
            .get(null);
  }

  /**
   * Creates a JAR of the last batch of expanded and compiled Java classes.
   *
   * @param outputFile The JAR is stored in this file.
   * @throws IOException
   */
  public void createJar(File outputFile) throws IOException {
    this.fileManager.outputClassesToJar(outputFile, this.compiledClassNames.values());
  }

  public void createClasses(String outputDir) throws IOException {
    this.fileManager.outputToFile(outputDir);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Inherited methods
  ////////////////////////////////////////////////////////////////////////////
  @Override
  protected void beforeOutput() {
    Logger.getLogger(BeanCompiler.class.getName()).log(Level.INFO, "Generating source files...");
    this.javaSourceObjects.clear();
    this.compiledClassNames.clear();
    this.referencedClassNames.clear();
  }

  @Override
  protected void outputClass(
      String fqOmtName, String packageName, String className, StringBuilder sourceCode)
      throws IOException {
    if (this.compiledClassNames.putIfAbsent(fqOmtName, packageName + PKG_SEPARATOR + className)
        == null) {
      JavaSourceObject jSourceObject =
          new JavaSourceObject(packageName + PKG_SEPARATOR + className, sourceCode.toString());
      this.javaSourceObjects.add(jSourceObject);
    }
  }

  @Override
  protected void outputClass(String fqOmtName, String packageName, String className)
      throws IOException {
    this.referencedClassNames.put(fqOmtName, packageName + PKG_SEPARATOR + className);
  }

  @Override
  protected void afterOutput() throws Exception {
    /** Compile the collected Java source files. */
    this.compileClasses(true);

    /**
     * Compilation has been successful. Now add references between OMT and Java class names to the
     * file manager
     */
    for (Entry<String, String> entry : this.compiledClassNames.entrySet()) {
      this.fileManager.addReference(entry.getKey(), entry.getValue());
    }

    for (Entry<String, String> entry : this.referencedClassNames.entrySet()) {
      this.fileManager.addReference(entry.getKey(), entry.getValue());
    }

    Logger.getLogger(BeanCompiler.class.getName()).log(Level.INFO, "Class compilation finished");
  }

  ////////////////////////////////////////////////////////////////////////////
  // Private methods
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Injects the following dynamic class to support the retrieval of the lookup context context from
   * the context of the Bean ClassLoader.
   */
  private void compileLookupClass() throws Exception {
    JavaSourceObject jSourceObject =
        new JavaSourceObject(
            LOOKUP_PACKAGE + PKG_SEPARATOR + LOOKUP_CLASS,
            "package "
                + LOOKUP_PACKAGE
                + ";"
                + "import java.lang.invoke.MethodHandles;\n"
                + "public interface "
                + LOOKUP_CLASS
                + " {\n"
                + "    MethodHandles.Lookup "
                + LOOKUP_FIELD
                + " = MethodHandles.lookup();\n"
                + "}");

    this.javaSourceObjects.add(jSourceObject);

    this.compileClasses(false);
  }

  private void compileClasses(boolean outputInfo) throws Exception {

    if (this.javaSourceObjects.isEmpty()) {
      // nothing to compile; return to prevent compiler error about this
      return;
    }

    if (outputInfo) {
      Logger.getLogger(BeanCompiler.class.getName())
          .log(Level.INFO, "Compiling {0} source files...", this.javaSourceObjects.size());
    }

    // Prepare the classpath.
    //
    // The class path can be set with:
    // (1) StandardJavaFileManager.setLocation(StandardLocation.CLASS_PATH, "YOUR_CLASS_PATH")
    // (2) Compiler.getTask(ARG_0, ARG_1, ARG_2, CLASS_PATH_OPTIONS, ...)
    //
    // It turns out that when using Compiler.getTask the class path seems to
    // be evaluated only on the initial call and that subsequent settings
    // are ignored. So we revert to using setLocation.
    // The Compiler.getTask code is kept for reference.
    boolean useGetTask = false;

    // compiler options, is any
    List<String> optionList = new ArrayList<>();

    // in case to compile for a specific version (e.g. "11"),
    // we have the following code for reference.
    String javaReleaseVersion = null;
    if (javaReleaseVersion != null) {
      optionList.addAll(Arrays.asList("--release", javaReleaseVersion));
    }

    if (useGetTask) {
      if (!this.jars.isEmpty()) {
        String path = "";
        String separator = System.getProperty("path.separator");

        for (URL jar : this.jars) {
          if (outputInfo) {
            Logger.getLogger(BeanCompiler.class.getName()).log(Level.INFO, "Add JAR: {0}", jar);
          }
          path += (path.isEmpty() ? jar.getPath() : separator + jar.getPath());
        }

        optionList.addAll(Arrays.asList("-classpath", path));
      }
    } else {
      if (!this.jars.isEmpty()) {
        List<File> fileList = new ArrayList();
        for (URL jar : this.jars) {
          if (outputInfo) {
            Logger.getLogger(BeanCompiler.class.getName()).log(Level.INFO, "Add JAR: {0}", jar);
          }
          fileList.add(new File(jar.getPath()));
        }
        this.standardFileManager.setLocation(StandardLocation.CLASS_PATH, fileList);
      }
    }

    Iterable<String> compilerOptions = optionList.isEmpty() ? null : optionList;
    CompilationTask task =
        this.compiler.getTask(
            null,
            this.fileManager,
            this.diagnostics,
            compilerOptions,
            null,
            this.javaSourceObjects);
    boolean success = task.call();

    if (success) {
      if (outputInfo) {
        Logger.getLogger(BeanCompiler.class.getName())
            .log(Level.INFO, "Compiler task was successful");
      }
    } else {
      Logger.getLogger(BeanCompiler.class.getName()).log(Level.SEVERE, "Compiler task failed");
    }

    if (!this.diagnostics.getDiagnostics().isEmpty()) {
      Logger.getLogger(BeanCompiler.class.getName()).log(Level.SEVERE, "Compiler diagnostics:");
      for (Diagnostic diagnostic : this.diagnostics.getDiagnostics()) {
        Logger.getLogger(BeanCompiler.class.getName())
            .log(
                Level.SEVERE,
                "Code={0}, Kind={1}, Position={2}, StartPosition={3}, EndPosition={4}, Source={5}, Message={6}",
                new Object[] {
                  diagnostic.getCode(),
                  diagnostic.getKind(),
                  diagnostic.getPosition(),
                  diagnostic.getStartPosition(),
                  diagnostic.getEndPosition(),
                  diagnostic.getSource(),
                  diagnostic.getMessage(null)
                });
      }
    }

    if (!success) {
      throw new Exception(
          "Compilation task failed. See diagnostics diagnostics messages for further information.");
    }
  }
}
