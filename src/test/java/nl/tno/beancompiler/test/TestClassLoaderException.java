package nl.tno.beancompiler.test;

import java.net.URL;
import nl.tno.beancompiler.BeanCompiler;
import nl.tno.beangenerator.BeanGenerator;
import nl.tno.beangenerator.BeanGeneratorProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author bergtwvd
 */
public class TestClassLoaderException {

	static URL[] modules;

	@BeforeAll
	public static void setUpClass() throws Exception {

		modules = new URL[]{
			TestClassLoaderException.class.getResource("/foms/Message.xml"),
			TestClassLoaderException.class.getResource("/foms/HLAstandardMIM.xml")};
	}

	@AfterAll
	public static void tearDownClass() {
	}

	@BeforeEach
	public void setUp() {
	}

	@AfterEach
	public void tearDown() {
	}

	@Test
	public void testClassNotFoundException1() throws Exception {
		BeanGeneratorProperties properties = new BeanGeneratorProperties();

		// Use FQ names for the MIM to avoid duplicate classes at compile time
		properties.setUseFQclassName(true);

		BeanCompiler bc = new BeanCompiler(properties);
		bc.expand(modules, null, null);

		String packageName = bc.getPackageName(modules[0]);

		ClassNotFoundException thrown = Assertions.assertThrows(ClassNotFoundException.class, () -> {
			Class clazz = Class.forName(packageName + BeanGenerator.PKG_SEPARATOR + BeanGenerator.INTERACTIONS_PACKAGENAME + BeanGenerator.PKG_SEPARATOR + "Message");

			// following call should fail because the class loader chain is not aware of the dynamic classes
			Object object = clazz.getDeclaredConstructor().newInstance();
		});

		Assertions.assertNotNull(thrown);
	}
}
