package nl.tno.beancompiler.test;

import java.net.URL;
import nl.tno.beancompiler.BeanCompiler;
import nl.tno.beangenerator.BeanGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author bergtwvd
 */
public class TestBeanClassLoader {

	static URL[] modules;

	@BeforeAll
	public static void setUpClass() throws Exception {
		modules = new URL[]{
			TestBeanClassLoader.class.getResource("/foms/RPR_FOM_v2.0_1516-2010.xml"),
			TestBeanClassLoader.class.getResource("/foms/HLAstandardMIM.xml")
		};
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
	public void testBeanClassLoader() throws Exception {
		BeanCompiler bc = new BeanCompiler();
		bc.expand(modules, null, null);

		ClassLoader classLoader = bc.getClassLoader();

		String packageName = bc.getPackageName(modules[0]);

		Class c0 = classLoader.loadClass(packageName + BeanGenerator.PKG_SEPARATOR + BeanGenerator.OBJECTS_PACKAGENAME + BeanGenerator.PKG_SEPARATOR + "Aircraft");
		Object o0 = c0.getDeclaredConstructor().newInstance();

		Class c1 = Class.forName(packageName + BeanGenerator.PKG_SEPARATOR + BeanGenerator.OBJECTS_PACKAGENAME + BeanGenerator.PKG_SEPARATOR + "SurfaceVessel", true, classLoader);
		Object o1 = c1.getDeclaredConstructor().newInstance();
	}

}
