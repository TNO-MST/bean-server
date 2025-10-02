# Java Bean construction rules
This section describes the rules for creating Java Beans from HLA FOM modules, Classes and Datatypes.

## FOM modules
The HLA FOM defines the information exchanged between federate applications at runtime. This information includes object classes, object class attributes, interaction classes, interaction parameters, and other relevant information. The FOM is provided to the HLA RTI using one or more FOM modules. The RTI assembles a FOM using these FOM modules and one Management Object Model (MOM) and Initialization Module (MIM), which is provided automatically by the RTI or, optionally, provided to the RTI when the federation execution is created. A FOM can be constructed as a single self-contained object model, or can be composed from multiple FOM modules. In this first case, the FOM is said to consist of exactly one FOM module.

In general a FOM module corresponds with a Java package, although multiple FOM modules may be mapped to a single Java package. The Java class definitions associated with the Object Classes, Interaction Classes and Datatypes in an HLA FOM module are organized in sub-packages to avoid class-naming conflicts. The package structure is:

`PackageName.objects` for Java classes associated with HLA Object Classes;

`PackageName.interactions` for Java classes associated with HLA Interaction Classes;

`PackageName.datatypes` for Java classes associated with HLA Datatypes (in particular, Variant Record, Fixed Record and Enumerated datatypes).

For Object Classes, Interaction Classes and Datatypes defined in other FOM modules **Java import** statements are defined for the respective Java package. Also scaffolding classes result in Java import statements.

The following example shows a Java class corresponding with an HLA Interaction Class in the NETN4 FOM:

````
package nl.tno.netn4.interactions;

import nl.tno.netn4.datatypes.LocationStruct;

/** Report on an entity's own position, speed, and heading. */
public class PositionStatusReport extends ETR_Report {

  /** Required. Position of the entity at the specified time. */
  LocationStruct Position;

  /** Required. Heading of the entity. [0,360). Default = 0. True North. */
  Float Heading;

  /** Required. Speed of the entity. */
  Float Speed;

  public LocationStruct getPosition() {
    return this.Position;
  }

  public void setPosition(LocationStruct Position) {
    this.Position = Position;
  }

  public Float getHeading() {
    return this.Heading;
  }

  public void setHeading(Float Heading) {
    this.Heading = Heading;
  }

  public Float getSpeed() {
    return this.Speed;
  }

  public void setSpeed(Float Speed) {
    this.Speed = Speed;
  }
}
````

## HLA Object and Interaction Classes
The HLA FOM module defines HLA Object Classes and HLA Interaction Classes, as well as the class hierarchy. The Java class name corresponds with the name of an HLA FOM Object or Interaction Class. Subclasses in the HLA FOM module correspond to Java class extensions.

The example below is for an HLA Object Class, but applies equally to an HLA Interaction Class. For an HLA Object Class named ``ObjectClass`` the Java class definition is generally:

```
class ObjectClass {
    DataType1	Attribute1
    ...
    DataTypeX	AttributeX
}
```

Let's assume that this class has a subclass called ``SubObjectClass``. Then the Java class definition for the ``SubObjectClass`` is:

```
class SubObjectClass extends ObjectClass {
    DataType11	Attribute11
    ...
    DataTypeXX	AttributeXX
}
```

The listed Java class attributes correspond to the HLA FOM Object Class attributes. The Java attribute name must be identical to the Object Class attribute or Interaction Class parameter name. The definition of datatypes is explained in the next section.

Note that the above examples do not show the use of modifiers (such as ``public``) and the getter and setter methods for Java class attribute access. Modifiers and getters/setters need to be added to the class definition.

## HLA FOM Datatypes
The HLA OMT standard defines several datatypes that may be used in an HLA FOM or HLA FOM module:

- Simple datatypes
- Enumerated datatypes
- Array datatypes
- Fixed record datatypes
- Variant record datatypes

The Fixed and Variant record datatypes are so called `constructed` datatypes, as these are assembled from the other datatypes.

The HLA MIM defines several standard basic data representations and datatypes. The basic data representations correspond directly to Java datatypes. The standard representations and datatypes are discussed in following subsections.

### Basic data representations
The following table defines the mapping between `MIM` basic data representations and Java datatypes:

| Name           | Java datatype   |
| -------------- | --------------- |
| HLAinteger16BE | Short, short    |
| HLAinteger32BE | Integer, int    |
| HLAinteger64BE | Long, long      |
| HLAfloat32BE   | Float, float    |
| HLAfloat64BE   | Double, double  |
| HLAoctetPairBE | Character, char |
| HLAoctet       | Byte, byte      |

### Simple datatype
A simple datatype has by definition a basic data representation, thus a simple datatype can be mapped directly to a Java datatype when its representation matches with one in the table listed above. If a simple datatype is used for an HLA FOM Object Class attribute or Interaction Class parameter, then the related Java datatype must be used in the Java class definition.

### Enumerated datatype
An enumerated datatype describes a data element that can take on a finite discrete set of possible values. An enumerated datatype has:

- a datatype name
- a basic data representation
- a list of names of all enumerators associated with this datatype
- per enumerator a set of values that correspond to the enumerator

The information is summarized in the table below.

| Name           | Representation   | Enumerator  | Value    |
| -------------- | ---------------- | ----------- | -------- |
| *EnumDataType* | *representation* | *constant1* | *value1* |
| -              | -                | ...         | ...      |
| -              | -                | *constantX* | *valueX*,*valueY*,*valueZ* |

The corresponding Java class definition for the enumerated datatype is:

```
enum EnumDataType {
    constant1(value1),
    ...
    constantX(valueX, ValueY, valueZ);
    
    private representation value;
    private representation[] values;
    
    private EnumDatatype(representation... values) {
        this.value = values[0];
        this.values = values;
    }

    public representation getValue() {
        return value;
    }
	
    public representation[] getValues() {
        return values;
    }
}
```

The ``representation`` in the definition above must be replaced by the Java class related to the basic data representation. The enumeration class must also define a public:
- ``getValue`` method to retrieve the first value of the enumerator, and
- ``getValues`` method to retrieve all values of the enumerator.
Typically an enumerator has a single value, but the OMT standard allows for multiple values.

A special enumerated datatype is ``HLAboolean``. When this datatype is used for an FOM Object Class attribute or Interaction Class parameter, the Java datatype ``boolean`` or ``Boolean`` can be used for the corresponding Java class attribute. For ``HLAboolean`` there is no need to define a Java enumeration class.

Below is an example for the `RPR-FOM` enumerated datatype `DamageStatusEnum32`, using `HLAinteger32BE` as basic data representation:

```
package rprfom.datatypes;

public enum DamageStatusEnum32 {
  HLAunknown(0),
  NoDamage(0),
  SlightDamage(1),
  ModerateDamage(2),
  Destroyed(3);

  private final int value;

  private final int[] values;

  private DamageStatusEnum32(int... values) {
    this.value = values[0];
    this.values = values;
  }

  public int getValue() {
    return value;
  }

  public int[] getValues() {
    return values;
  }
}
```

#### HLAother enumerator
The `HLAother` enumerator in a FOM HLA variant record definition) represents all other enumerators not listed in the definition.

#### HLAunknown enumerator
HLA-4 allows the merging of new enumerators in an existing enumeration datatype. Therefore - at run time - enumerator values may be received from other federates that were unknown at federate build time. 
 
The special enumerator `HLAunknown` is added by the Bean Generator to each enumeration to represent enumerators that are not known to the recipient. This special enumerator is not defined in the FOM and cannot be used by federates in exchanging data.

### Fixed record datatype
The fixed record datatype is used to describe heterogeneous collections of types; these constructs
are also known as records or structures. A fixed record may contain fields that are of other types, such as simple datatypes, fixed records, arrays, enumerations, or variant records. This allows users to build “structures of data structures” .

A fixed record datatype has:
- a datatype name
- the names of the fields in the fixed record datatype
- per field, the field datatype

The information is summarized in the table below.

| Record name  | Field Name | Field Type  |
| ------------ | ---------- | ----------- |
| *RecordType* | *field1*   | *DataType1* |
| -            | ...        | ...         |
| -            | *fieldX*   | *DataTypeX* |

The corresponding Java class definition for the fixed record datatype is:

````
class RecordType {
 	DataType1      field1;
	...
	DataTypeX      fieldX;  
}
````

It is not required that the Java class attributes are in the same order as the fields in the table. If a type is a simple datatype, then it must be replaced by the related Java class.

Below an example for the `RPR-FOM` fixed record datatype ``EntityIdentifierStruct``:

```
package nl.tno.netn4.datatypes;

/**
 * Unique, exercise-wide identification of the entity, or a symbolic group address referencing
 * multiple entities or a simulation application. Based on the Entity Identifier record as specified
 * in IEEE 1278.1-1995 section 5.2.14.
 */
public class EntityIdentifierStruct {

  /** Simulation application (federate) identifier. */
  FederateIdentifierStruct FederateIdentifier;

  /**
   * Each entity in a given simulation application shall be given an entity identifier number unique
   * to all other entities in that application. This identifier number is valid for the duration of
   * the exercise; however, entity identifier numbers may be reused when all possible numbers have
   * been exhausted. No entity shall have an entity identifier number of NO_ENTITY (0), ALL_ENTITIES
   * (0xFFFF), or RQST_ASSIGN_ID (0xFFFE). The entity identifier number need not be registered or
   * retained for future exercises. An entity identifier number equal to zero with valid site and
   * application identification shall address a simulation application. An entity identifier number
   * equal to ALL_ENTITIES shall mean all entities within the specified site and application. An
   * entity identifier number equal to RQST_ASSIGN_ID allows the receiver of the CreateEntity
   * interaction to define the entity identifier number of the new entity. The new entity will
   * specify its entity identifier number in the Acknowledge interaction.
   */
  short EntityNumber;

  public FederateIdentifierStruct getFederateIdentifier() {
    return this.FederateIdentifier;
  }

  public void setFederateIdentifier(FederateIdentifierStruct FederateIdentifier) {
    this.FederateIdentifier = FederateIdentifier;
  }

  public short getEntityNumber() {
    return this.EntityNumber;
  }

  public void setEntityNumber(short EntityNumber) {
    this.EntityNumber = EntityNumber;
  }
}

```

### Variant record datatype
The variant record datatype describes discriminated unions of types; these constructs are also known as variant or choice records. Similar to the fixed record datatype, a variant record may contain fields that are of other types.

A variant record datatype has:
- a datatype name
- the name of the discriminant that is used to identify the alternative
- the datatype of the discriminant; this must be an enumerated datatype
- a set of enumerators that determines the alternatives; the enumerators shall be from the enumerated datatype specified for the discriminant type
- the name for the alternative
- the datatype of the alternative

The information is summarized in the table below.

| Record name  | Discriminant Name | Discriminant Type | Discriminant Enumerator | Alternative Name | Alternative Type |
| ------------ | ----------------- | ----------------- | ----------------------- | ---------------- | ---------------- |
| *RecordType* | *discriminant*    | *EnumDataType*    | *Constant1*             | *field1*         | *DataType1*      |
| -            | -                 | -                 | ...                     | ...              | ...              |
| -            | -                 | -                 | *ConstantX*             | *fieldX*         | *DataTypeX*      |

The corresponding Java class definition for the variant record datatype is:

````
class RecordType {
	EnumDataType   discriminant;
	DataType1      field1;
	...
	DataTypeX      fieldX;
}
````

The ``EnumDataType`` must refer to an enumerated datatype. It is not required that the Java class attributes are in the same order as the alternatives in the variant record datatype.

Below is an example for the `RPR-FOM` variant record datatype ``SpatialVariantStruct``. The discriminator in this example is `DeadReckoningAlgorithm`.

```
package nl.tno.netn4.datatypes;

/** Variant Record for a single spatial attribute. */
public class SpatialVariantStruct {

  /** Discriminant */
  DeadReckoningAlgorithmEnum8 DeadReckoningAlgorithm;

  /** Variant for representing a static object. */
  SpatialStaticStruct SpatialStatic;

  /**
   * Variant for representing an object with a constant velocity (or low acceleration) linear motion
   * in world coordinates.
   */
  SpatialFPStruct SpatialFPW;

  /**
   * Variant for representing an object with a constant velocity (or low acceleration) linear
   * motion, including rotation information, in world coordinates.
   */
  SpatialRPStruct SpatialRPW;

  /**
   * Variant for representing an object with high speed or maneuvering at any speed, including
   * rotation information, in world coordinates.
   */
  SpatialRVStruct SpatialRVW;

  /**
   * Variant for representing an object with high speed or maneuvering at any speed in world
   * coordinates.
   */
  SpatialFVStruct SpatialFVW;

  /**
   * Variant for representing an object with a constant velocity (or low acceleration) linear motion
   * in body axis coordinates.
   */
  SpatialFPStruct SpatialFPB;

  /**
   * Variant for representing an object with a constant velocity (or low acceleration) linear
   * motion, including rotation information, in body axis coordinates.
   */
  SpatialRPStruct SpatialRPB;

  /**
   * Variant for representing an object with high speed or maneuvering at any speed, including
   * rotation information, in body axis coordinates.
   */
  SpatialRVStruct SpatialRVB;

  /**
   * Variant for representing an object with high speed or maneuvering at any speed in body axis
   * coordinates.
   */
  SpatialFVStruct SpatialFVB;

  public DeadReckoningAlgorithmEnum8 getDeadReckoningAlgorithm() {
    return this.DeadReckoningAlgorithm;
  }

  public void setDeadReckoningAlgorithm(DeadReckoningAlgorithmEnum8 DeadReckoningAlgorithm) {
    this.DeadReckoningAlgorithm = DeadReckoningAlgorithm;
  }

  public SpatialStaticStruct getSpatialStatic() {
    return this.SpatialStatic;
  }

  public void setSpatialStatic(SpatialStaticStruct SpatialStatic) {
    this.SpatialStatic = SpatialStatic;
  }

  public SpatialFPStruct getSpatialFPW() {
    return this.SpatialFPW;
  }

  public void setSpatialFPW(SpatialFPStruct SpatialFPW) {
    this.SpatialFPW = SpatialFPW;
  }

  public SpatialRPStruct getSpatialRPW() {
    return this.SpatialRPW;
  }

  public void setSpatialRPW(SpatialRPStruct SpatialRPW) {
    this.SpatialRPW = SpatialRPW;
  }

  public SpatialRVStruct getSpatialRVW() {
    return this.SpatialRVW;
  }

  public void setSpatialRVW(SpatialRVStruct SpatialRVW) {
    this.SpatialRVW = SpatialRVW;
  }

  public SpatialFVStruct getSpatialFVW() {
    return this.SpatialFVW;
  }

  public void setSpatialFVW(SpatialFVStruct SpatialFVW) {
    this.SpatialFVW = SpatialFVW;
  }

  public SpatialFPStruct getSpatialFPB() {
    return this.SpatialFPB;
  }

  public void setSpatialFPB(SpatialFPStruct SpatialFPB) {
    this.SpatialFPB = SpatialFPB;
  }

  public SpatialRPStruct getSpatialRPB() {
    return this.SpatialRPB;
  }

  public void setSpatialRPB(SpatialRPStruct SpatialRPB) {
    this.SpatialRPB = SpatialRPB;
  }

  public SpatialRVStruct getSpatialRVB() {
    return this.SpatialRVB;
  }

  public void setSpatialRVB(SpatialRVStruct SpatialRVB) {
    this.SpatialRVB = SpatialRVB;
  }

  public SpatialFVStruct getSpatialFVB() {
    return this.SpatialFVB;
  }

  public void setSpatialFVB(SpatialFVStruct SpatialFVB) {
    this.SpatialFVB = SpatialFVB;
  }
}
```

### Array datatype
The array datatype describes an indexed homogenous collection of datatypes; these constructs are also known as arrays or sequences.

An array datatype has:
- a datatype name
- the datatype of the array elements
- a cardinality

The information is summarized in the table below.

| Name        | Element Type | Cardinality                       |
| ----------- | ------------ | --------------------------------- |
| *ArrayType* | *DataType*   | ``<integer value>``or ``Dynamic`` |

The following array datatype encodings are supported:
- the two standard encodings `HLAfixedArray` and ``HLAvariableArray``, and
- the RPR-FOM encodings ``RPRnullTerminatedArray`` and `RPRlengthlessArray`. 

If the encoding is ``HLAfixedArray`` then the cardinality must be a non-negative integer value. If the encoding is ``HLAvariableArray`` then the cardinality must be ``Dynamic``.

`RPRnullTerminatedArray` is supported to allow for the encoding/decoding of null-terminated character strings in the RPR-FOM. If the encoding is ``RPRnullTerminatedArray`` then the cardinality must be `Dynamic` and the element type must be `HLAASCIIChar`.

If the encoding is `RPRlengthlessArray` then the cardinality must also be `Dynamic`.

The array datatype name is not used in the Java class definition, but only serves the internal lookup in the FOM. A FOM array datatype can be represented as either a Java array type or a Java List. The corresponding Java class definition for the array datatype is:

```
DataType[] field

List<DataType> field
```

If ``DataType`` is a simple datatype then it must be replaced by the Java datatype representation. For example, if the ``DataType`` is a simple datatype and its representation is ``HLAinteger32BE`` then the Java array definition is:

```
int[] field

List<Integer> field
```

Note that if `List` is used to represent an array, the type element must be a boxed Java datatype, `Integer` in this example.

The above rule applies recursively to ``DataType``. For example, if ``DataType`` refers to an array datatype with a simple datatype as element (for example with representation ``HLAinteger32BE``), then the Java array definition becomes:

```
int[][] field

List<List<Integer>> field
```

For the two predefined array types ``HLAASCIIstring`` and ``HLAunicodeString`` the Java string type must be used:

```
String field
```

Below follows an example for the `RPR-FOM` array datatype used in `MarkingStruct`. The `MarkingData` is a fixed array of byte. The cardinality is defined in the `RPR-FOM` and is 11. Note that in a Java class definition we cannot express the cardinality of an array; this can only be checked at run-time.

```
package nl.tno.netn4.datatypes;

/** Character set used in the marking and the string of characters to be interpreted for display. */
public class MarkingStruct {

  /** Character set representation. */
  MarkingEncodingEnum8 MarkingEncodingType;

  /** 11 element character string */
  byte[] MarkingData;

  public MarkingEncodingEnum8 getMarkingEncodingType() {
    return this.MarkingEncodingType;
  }

  public void setMarkingEncodingType(MarkingEncodingEnum8 MarkingEncodingType) {
    this.MarkingEncodingType = MarkingEncodingType;
  }

  public byte[] getMarkingData() {
    return this.MarkingData;
  }

  public void setMarkingData(byte[] MarkingData) {
    this.MarkingData = MarkingData;
  }
}
```
