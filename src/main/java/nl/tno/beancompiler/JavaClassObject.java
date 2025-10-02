package nl.tno.beancompiler;

/**
 *
 * @author bergtwvd
 */
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import javax.tools.SimpleJavaFileObject;

class JavaClassObject extends SimpleJavaFileObject {

    /**
     * Byte code created by the compiler will be stored in this
     * ByteArrayOutputStream so that we can later get the byte array out of it
     * and put it in the memory as an instance of our class.
     */
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();

    /**
     * The name of the Java class object.
     */
    final String className;

    /**
     * Registers the compiled class object under URI containing the class full
     * name
     *
     * @param className Full name of the compiled class
     * @param kind Kind of the data. It will be CLASS in our case
     */
    JavaClassObject(String className, Kind kind) {
        super(URI.create("string:///" + className.replace('.', '/') + kind.extension), kind);
        this.className = className;
    }

    @Override
    public String getName() {
        return this.className;
    }

    /**
     * Return the byte code of the Java class object.
     *
     * @return compiled byte code
     */
    byte[] getBytes() {
        return bos.toByteArray();
    }

    /**
     * Provide the compiler with an output stream that leads to our byte array.
     * This way the compiler will write everything into the byte array that we
     * will instantiate later
     *
     * @return
     * @throws java.io.IOException
     */
    @Override
    public OutputStream openOutputStream() throws IOException {
        return bos;
    }

    /**
     * Provide the compiler with an input stream to read previously compiled
     * classes.
     *
     * @return
     * @throws IOException
     */
    @Override
    public InputStream openInputStream() throws IOException {
        return new ByteArrayInputStream(bos.toByteArray());
    }    
}
