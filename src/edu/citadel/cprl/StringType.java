package edu.citadel.cprl;

/**
 * This class encapsulates the language concept of a string type in
 * the programming language CPRL.  A string is essentially a record
 * with two components as follows:
 * <code>record { length : Integer, data : array[capacity] of Char }</code>
 */
public class StringType extends Type
  {
    private int capacity;

    /**
     * Construct a string type with the specified type name and capacity.
     */
    public StringType(String typeName, int capacity)
      {
        super(typeName, Integer.size() + capacity*Char.size());
        this.capacity = capacity;
      }

    /**
     * Construct a string type with the specified capacity.  The type name
     * is "string[capacity]".  This constructor is used for string literals.
     */
    public StringType(int capacity)
      {
        this("string[" + capacity + "]", capacity);
      }

    public int capacity()
      {
        return capacity;
      }
  }
