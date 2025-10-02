package nl.tno.beancompiler;

/**
 * @author bergtwvd
 */
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

class JavaClassObjectFileManager extends ForwardingJavaFileManager {

  /** Several handy constants */
  static final String CLASS_FILE_SUFFIX = ".class";

  static final String PKG_SEPARATOR = ".";
  static final String DIR_SEPARATOR = "/";

  /** This map stores the compiled byte code of our classes. */
  final Map<String, JavaClassObject> jclassObjectMap = new HashMap();

  /** This map stores the mapping from OMT class name to Java class name. */
  private final Map<String, String> omtClassName2javaClassNameMap = new HashMap();

  /** This map stores the mapping from Java class name to OMT class name. */
  private final Map<String, String> javaClassName2omtClassNameMap = new HashMap();

  /**
   * This constructor initializes the manager with the specified standard java file manager
   *
   * @param standardManager
   */
  JavaClassObjectFileManager(StandardJavaFileManager standardManager) {
    super(standardManager);
  }

  /**
   * Clear to the internal data store.
   */
  void clear() {
    jclassObjectMap.clear();
    omtClassName2javaClassNameMap.clear();
    javaClassName2omtClassNameMap.clear();
  }

  /**
   * Update the maps with the provided reference between OMT and Java class names. The FQ OMT class
   * name is used to ensure uniqueness of the reference.
   *
   * @param omtClassName
   * @param javaClassName
   * @return False if the reference already exists; true otherwise.
   */
  boolean addReference(String omtClassName, String javaClassName) {
    if (this.omtClassName2javaClassNameMap.putIfAbsent(omtClassName, javaClassName) == null) {
      this.javaClassName2omtClassNameMap.put(javaClassName, omtClassName);
      return true;
    } else {
      return false;
    }
  }

  String getOmtClassName(String javaClassName) {
    return this.javaClassName2omtClassNameMap.get(javaClassName);
  }

  String getJavaClassName(String omtClassName) {
    return this.omtClassName2javaClassNameMap.get(omtClassName);
  }

  JavaClassObject getJavaClassObject(String javaClassName) {
    return this.jclassObjectMap.get(javaClassName);
  }

  /**
   * Save all object code contained in this file manager to Java class files, under the provided
   * directory.
   *
   * @param classDir: filesystem directory under which the files are created
   * @throws IOException
   */
  void outputToFile(String classDir) throws IOException {
    Logger.getLogger(JavaClassObjectFileManager.class.getName())
        .log(Level.INFO, "Writing {0} class files...", this.jclassObjectMap.size());

    Set<String> visitedDirNames = new HashSet();
    for (Entry<String, JavaClassObject> entry : this.jclassObjectMap.entrySet()) {
      String name = entry.getKey().replace(PKG_SEPARATOR, DIR_SEPARATOR);

      int lastIndex = name.lastIndexOf(DIR_SEPARATOR);
      String dirName =
          (lastIndex < 0) ? classDir : classDir + name.substring(0, lastIndex) + DIR_SEPARATOR;

      if (visitedDirNames.add(dirName)) {
        new File(dirName).mkdirs();
      }

      File classFile = new File(classDir + name + CLASS_FILE_SUFFIX);
      classFile.createNewFile();
      FileOutputStream outputStream = new FileOutputStream(classFile);
      outputStream.write(entry.getValue().getBytes());
      outputStream.close();
    }
  }

  /**
   * Create a JAR with the Java Class objects indicated by the provided set of names.
   *
   * @param outputFile
   * @param classNames
   * @throws FileNotFoundException
   * @throws IOException
   */
  void outputClassesToJar(File outputFile, Collection<String> classNames)
      throws FileNotFoundException, IOException {
    Set<JavaClassObject> javaClassObjects = new HashSet();

    for (String className : classNames) {
      JavaClassObject javaClassObject = this.jclassObjectMap.get(className);
      if (javaClassObject != null) {
        javaClassObjects.add(javaClassObject);
      }
    }

    this.outputToJar(outputFile, javaClassObjects);
  }

  /**
   * Create a JAR with all Java Class Objects in this file manager.
   *
   * @param outputFile
   * @throws FileNotFoundException
   * @throws IOException
   */
  void outputAllClassesToJar(File outputFile) throws FileNotFoundException, IOException {
    this.outputToJar(outputFile, this.jclassObjectMap.values());
  }

  /**
   * Create JAR for designated collection of Java Class Objects.
   *
   * @param outputFile: file to return the result in
   * @param javaClassObjects: collection of objects
   * @throws IOException
   */
  private void outputToJar(File outputFile, Collection<JavaClassObject> javaClassObjects)
      throws FileNotFoundException, IOException {
    Logger.getLogger(JavaClassObjectFileManager.class.getName())
        .log(
            Level.INFO,
            "Creating Bean JAR {0} with {1} class files...",
            new Object[] {outputFile.getName(), javaClassObjects.size()});

    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    JarOutputStream target = new JarOutputStream(new FileOutputStream(outputFile), manifest);

    Set<String> visitedDirNames = new HashSet();
    long theTime = System.currentTimeMillis();

    javaClassObjects.stream()
        .sorted(new JavaClassObjectComparator())
        .forEachOrdered(
            javaClassObject -> {
              try {
                // skip the Lookup class
                if (!javaClassObject
                    .getName()
                    .equals(
                        BeanCompiler.LOOKUP_PACKAGE
                            + BeanCompiler.PKG_SEPARATOR
                            + BeanCompiler.LOOKUP_CLASS)) {
                  String name = javaClassObject.getName().replace(PKG_SEPARATOR, DIR_SEPARATOR);

                  int lastIndex = name.lastIndexOf(DIR_SEPARATOR);
                  String dirName =
                      (lastIndex < 0)
                          ? "." + DIR_SEPARATOR
                          : name.substring(0, lastIndex) + DIR_SEPARATOR;

                  if (visitedDirNames.add(dirName)) {
                    JarEntry jarEntry = new JarEntry(dirName);
                    jarEntry.setTime(theTime);
                    target.putNextEntry(jarEntry);
                    target.closeEntry();
                  }

                  JarEntry jarEntry = new JarEntry(name + CLASS_FILE_SUFFIX);
                  jarEntry.setTime(theTime);
                  target.putNextEntry(jarEntry);

                  BufferedInputStream in =
                      new BufferedInputStream(javaClassObject.openInputStream());
                  byte[] buffer = new byte[1024];
                  for (; ; ) {
                    int count = in.read(buffer);
                    if (count == -1) {
                      break;
                    }
                    target.write(buffer, 0, count);
                  }
                  target.closeEntry();
                }
              } catch (IOException ex) {
                Logger.getLogger(JavaClassObjectFileManager.class.getName())
                    .log(Level.SEVERE, ex.getMessage());
              }
            });

    target.close();

    Logger.getLogger(JavaClassObjectFileManager.class.getName())
        .log(Level.INFO, "Created Bean JAR {0}", new Object[] {outputFile.getName()});
  }

  @Override
  public Iterable<JavaFileObject> list(
      Location location, String packageName, Set kinds, boolean recurse) throws IOException {
    if (location == StandardLocation.CLASS_PATH && kinds.contains(Kind.CLASS)) {
      List<JavaFileObject> list = new ArrayList();
      if (recurse) {
        for (Entry<String, JavaClassObject> entry : jclassObjectMap.entrySet()) {
          if (entry.getKey().startsWith(packageName)) {
            list.add(entry.getValue());
          }
        }
      } else {
        for (Entry<String, JavaClassObject> entry : jclassObjectMap.entrySet()) {
          if (entry.getKey().substring(0, entry.getKey().lastIndexOf('.')).equals(packageName)) {
            list.add(entry.getValue());
          }
        }
      }

      if (!list.isEmpty()) {
        // got objects, so return these
        return list;
      }
      // else delegate
    }

    // delegate the list processing to the ForwardingJavaFileManager
    return super.list(location, packageName, kinds, recurse);
  }

  /**
   * Returns binary name of object. For simplicity we use the class name when the object is an
   * instance of JavaClassObject.
   *
   * @param location
   * @param file
   * @return
   */
  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    if (file instanceof JavaClassObject) {
      return file.getName();
      // return ((JavaClassObject) file).getClassName();
    } else {
      // if it's not JavaClassObject, then it's coming from standard file manager - let it handle
      // the file
      return super.inferBinaryName(location, file);
    }
  }

  /**
   * Gives the compiler an instance of the JavaClassObject so that the compiler can write the byte
   * code into it.
   *
   * @param location
   * @param className
   * @param kind
   * @param sibling
   * @return
   * @throws java.io.IOException
   */
  @Override
  public JavaFileObject getJavaFileForOutput(
      Location location, String className, Kind kind, FileObject sibling) throws IOException {
    JavaClassObject jClassObject = new JavaClassObject(className, kind);
    if (this.jclassObjectMap.put(className, jClassObject) != null) {
      Logger.getLogger(JavaClassObjectFileManager.class.getName())
          .log(Level.FINE, "Class already exist: {0}", className);
    }
    return jClassObject;
  }
}
