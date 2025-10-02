package nl.tno.beancompiler;

import java.net.URL;
import java.net.URLClassLoader;

/**
 *
 * @author bergtwvd
 */
public class BeanClassLoader extends URLClassLoader {

    final JavaClassObjectFileManager filemanager;

    BeanClassLoader(JavaClassObjectFileManager filemanager) {
        super(new URL[0]);
        this.filemanager = filemanager;
    }

    /**
     * Adds a previously compiled FOM module as Java Bean JAR to the class
     * loader.
     *
     * @param url
     */
    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    /**
     * Returns the Java Class name for the fully qualified OMT class name.
     *
     * @param omtClassName
     * @return name or null when no mapping exists
     */
    public String getJavaClassName(String omtClassName) {
        return this.filemanager.getJavaClassName(omtClassName);
    }

    /**
     * Returns the fully qualified OMT class name for the Java Class name.
     *
     * @param javaClassName
     * @return name or null when no mapping exists
     */
    public String getOmtClassName(String javaClassName) {
        return this.filemanager.getOmtClassName(javaClassName);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        JavaClassObject jClassObject = this.filemanager.jclassObjectMap.get(name);
        if (jClassObject == null) {
            return super.findClass(name);
        } else {
            try {
                super.definePackage(this.getPackageName(name), null, null, null, null, null, null, null);
            } catch (IllegalArgumentException ex) {
            }

            return super.defineClass(name, jClassObject.getBytes(), 0, jClassObject.getBytes().length);
        }
    }

    private String getPackageName(String className) {
        int i = className.lastIndexOf('.');
        if (i > 0) {
            return className.substring(0, i);
        } else {
            // No package name, e.g. LsomeClass;
            return null;
        }
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> c = super.findLoadedClass(name);
        if (c == null) {
            try {
                c = this.getClass().getClassLoader().loadClass(name);
            } catch (ClassNotFoundException ex) {
                c = this.findClass(name);
            }
        }

        if (resolve) {
            super.resolveClass(c);
        }

        return c;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return this.loadClass(name, false);
    }

    @Override
    public Package getPackage(String name) {
        return super.getDefinedPackage(name);
    }

}
