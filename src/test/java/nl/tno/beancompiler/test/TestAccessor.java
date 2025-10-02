package nl.tno.beancompiler.test;

import java.lang.reflect.Field;
import java.net.URL;
import nl.tno.beancompiler.BeanCompiler;
import nl.tno.beangenerator.BeanGenerator;
import nl.tno.beangenerator.BeanGeneratorProperties;
import nl.tno.oorti.AccessorType;
import nl.tno.oorti.accessor.Accessor;
import nl.tno.oorti.accessor.AccessorFactory;
import nl.tno.oorti.accessor.AccessorFactoryFactory;
import nl.tno.oorti.accessor.ClassUtils;
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
public class TestAccessor {

	// ensure uniqueness of the generated code by using a new group id
	static final String TEST_GROUP_ID = "nl.tno.test";

	static URL[] modules;

	private String getFqObjectClassName(String packageName, String name) {
		return packageName + BeanGenerator.PKG_SEPARATOR + BeanGenerator.OBJECTS_PACKAGENAME + BeanGenerator.PKG_SEPARATOR + name;
	}

	private String getFqDatatypeName(String packageName, String name) {
		return packageName + BeanGenerator.PKG_SEPARATOR + BeanGenerator.DATATYPES_PACKAGENAME + BeanGenerator.PKG_SEPARATOR + name;
	}

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

	/**
	 * Test field access on dynamic classes, using the different accessor types
	 * for Class property access, except for FIELD accessor type.
	 *
	 * @throws Exception
	 */
	@Test
	public void testFieldAccess1() throws Exception {
		BeanGeneratorProperties properties = new BeanGeneratorProperties();
		properties.setGroupId(TEST_GROUP_ID);

		BeanCompiler bc = new BeanCompiler(properties);
		bc.expand(modules, null, null);

		ClassLoader classLoader = bc.getClassLoader();
		String packageName = bc.getPackageName(modules[0]);

		Class aircraftClass = classLoader.loadClass(getFqObjectClassName(packageName, "Aircraft"));
		Object aircraft = aircraftClass.getDeclaredConstructor().newInstance();

		Class booleanClass = classLoader.loadClass(getFqDatatypeName(packageName, "RPRboolean"));
		Assertions.assertTrue(booleanClass.isEnum());

		// take the first enumerator value
		Object enumValue = booleanClass.getEnumConstants()[0];

		// we know that that class has a RPRboolean field named AfterburnerOn
		Field field = ClassUtils.getField(aircraftClass, "AfterburnerOn");

		// do a set/get for each accessor type
		for (AccessorType type : AccessorType.values()) {
			// skip the FIELD accessor since that only works if fields are public, which is not the case here
			if (type == AccessorType.FIELD) {
				continue;
			}

			// NOTE that we pass in a methodhandle lookup from the compiler
			// so that the lookup object is loaded by the BeanClassLoader, and subsequent loads
			// also use this classloader.
			// Otherwise the LAMBDA accessor.set fails with Class not found error.
			AccessorFactory factory = AccessorFactoryFactory.getAccessorFactory(type, bc.getMethodHandlesLookup());

			Accessor accessor = factory.createAccessor(field);
			accessor.set(aircraft, enumValue);
			Object out = accessor.get(aircraft);
			Assertions.assertEquals(enumValue, out);
		}
	}

	/**
	 * Test field access on dynamic classes, using the different accessor types
	 * for Class property access.
	 *
	 * The public modifier is used for class properties, hence the FIELD
	 * accessor can be used also.
	 *
	 * @throws Exception
	 */
	@Test
	public void testFieldAccess2() throws Exception {
		BeanGeneratorProperties properties = new BeanGeneratorProperties();
		properties.setGroupId(TEST_GROUP_ID);
		properties.setUsePublicModifier(true);

		BeanCompiler bc = new BeanCompiler(properties);
		bc.expand(modules, null, null);

		ClassLoader classLoader = bc.getClassLoader();
		String packageName = bc.getPackageName(modules[0]);

		Class aircraftClass = classLoader.loadClass(getFqObjectClassName(packageName, "Aircraft"));
		Object aircraft = aircraftClass.getDeclaredConstructor().newInstance();

		Class booleanClass = classLoader.loadClass(getFqDatatypeName(packageName, "RPRboolean"));
		Assertions.assertTrue(booleanClass.isEnum());

		// take the first enumerator value
		Object enumValue = booleanClass.getEnumConstants()[0];

		// we know that that class has a RPRboolean field named AfterburnerOn
		Field field = ClassUtils.getField(aircraftClass, "AfterburnerOn");

		// do a set/get for each accessor type
		for (AccessorType type : AccessorType.values()) {
			// NOTE that we pass in a methodhandle lookup from the compiler
			// so that the lookup object is loaded by the BeanClassLoader, and subsequent loads
			// also use this classloader.
			// Otherwise the LAMBDA accessor.set fails with Class not found error.
			AccessorFactory factory = AccessorFactoryFactory.getAccessorFactory(type, bc.getMethodHandlesLookup());

			Accessor accessor = factory.createAccessor(field);
			accessor.set(aircraft, enumValue);
			Object out = accessor.get(aircraft);
			Assertions.assertEquals(enumValue, out);
		}
	}

	/**
	 * Verify that the lookup context from this class fails.
	 *
	 * @throws Exception
	 */
	@Test
	public void testFieldAccessException1() throws Exception {
		BeanGeneratorProperties properties = new BeanGeneratorProperties();
		properties.setGroupId(TEST_GROUP_ID);

		BeanCompiler bc = new BeanCompiler(properties);
		bc.expand(modules, null, null);

		ClassLoader classLoader = bc.getClassLoader();
		String packageName = bc.getPackageName(modules[0]);

		Class aircraftClass = classLoader.loadClass(getFqObjectClassName(packageName, "Aircraft"));
		Object aircraft = aircraftClass.getDeclaredConstructor().newInstance();

		Class booleanClass = classLoader.loadClass(getFqDatatypeName(packageName, "RPRboolean"));
		Assertions.assertTrue(booleanClass.isEnum());

		// take the first value
		Object enumValue = booleanClass.getEnumConstants()[0];

		// we know that that class has a RPRboolean field named AfterburnerOn
		Field field = ClassUtils.getField(aircraftClass, "AfterburnerOn");

		// NOTE use the lookup from this class's context
		AccessorFactory factory = AccessorFactoryFactory.getAccessorFactory(AccessorType.LAMBDA);

		Accessor accessor = factory.createAccessor(field);

		// this class's class loader has no knowledge about the dynamic classes, so the set method below fails
		// this call must raise an exception because the class aircraftClass is unknown to the application class loader
		NoClassDefFoundError thrown = Assertions.assertThrows(NoClassDefFoundError.class, () -> {
			accessor.set(aircraft, enumValue);
		});

		Assertions.assertNotNull(thrown);
	}

}
