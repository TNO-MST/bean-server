# Bean Compiler
Functionality for Bean compilation is included in the `BeanCompiler` class.

## Constructors
The class has two constructors:
- No-argument default constructor
- Constructor with a properties argument, providing Bean compilation options

## Compilation
The Bean compilation is initiated by invoking the expansion method. The compiled classes are dynamic classes and can be loaded with the `BeanClassLoader`.

## Bean loading
The compiled classes can be loaded with the `BeanClassLoader`. The class loader also provides methods to map a fully qualified OMT class name to a Java class name and vice versa.
