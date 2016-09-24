# NativesProcessor

Quick&dirty JNI glue generator based on Java 1.6 annotation processors.

Only handles Java/Native conversion of basic types:
* `byte`, `char`, `short`, `int`, `float`, `double`
* arrays of these types
* `String` (mapped to `std::string`)
* direct `ByteBuffer`s (mapped to byte arrays)

In the case of arrays, if the parameter name ends with "In", the modifications to the array are not committed to the Java side (i.e. read-only).

Generally, these basic translations are enough to build clunky-but-efficient Java wrappers using a long to store the pointer to the native object
(in my experience, complex bi-directional native wrappers based on JVM upcalls are generally error-prone and less efficient).

Example:

