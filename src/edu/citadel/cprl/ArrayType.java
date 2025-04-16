package edu.citadel.cprl;

/**
 * This class encapsulates the language concept of an array type
 * in the programming language CPRL.
 */
public class ArrayType extends Type {
    private int numElements;
    private Type elementType;

    /**
     * Construct an array type with the specified type name, number
     * of elements, and the type of elements contained in the array.
     */
    public ArrayType(String typeName, int numElements, Type elementType) 
    {
        super(typeName,  elementType.size() * numElements);
        this.numElements = numElements;
        this.elementType = elementType;
    }

    /**
     * Returns the number of elements for this array type.
     */
    public int numElements() {
        return numElements;
    }

    /**
     * Returns the element type for this array type.
     */
    public Type elementType() {
        return elementType;
    }
}
