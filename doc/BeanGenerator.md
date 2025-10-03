# Bean Generator
Functionality for Bean generation is included in the `BeanGenerator` class.

The generation of Java Beans from HLA FOM modues is relatively straight forward and the design approach is:
- use straightforward functions for code expansion, rather then additional tools such as code templates;
- keep all the functionality together in a single `BeanGenerator` class;
- allow the class to be extended with additional functionality.

## Constructors
The class has two constructors:
- No-argument default constructor
- Constructor with a properties argument, providing Bean generation options

## Expansion
The Bean generation is initiated by invoking the expansion method.

The `BeanGenerator` provides several overridable callback methods that are called by the generator during the expansion:

- `beforeOutput`: initial callback
- `outputPackage`: called for each package for which code is generated
- `outputClass`: called for each class
- `afterOutput`: final callback

## Bean generation properties
Several properties can be provided to the `BeanGenerator` to control the generation of source code. These are described in the class `BeanGeneratorProperties`.
