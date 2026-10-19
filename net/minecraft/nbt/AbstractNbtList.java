package net.minecraft.nbt;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represents an abstraction of a mutable NBT list which holds elements of the same type.
 */
public sealed interface AbstractNbtList extends Iterable<NbtElement>, NbtElement permits NbtList, NbtByteArray, NbtIntArray, NbtLongArray {
	void clear();

	/**
	 * Sets the element at {@code index} to {@code element}. Does nothing if
	 * the types were incompatible.
	 * 
	 * @return whether the element was actually set
	 */
	boolean setElement(int index, NbtElement element);

	/**
	 * Inserts {@code element} at {@code index}. Does nothing if the
	 * types were incompatible.
	 * 
	 * @return whether the element was actually added
	 */
	boolean addElement(int index, NbtElement element);

	NbtElement method_10536(int i);

	NbtElement method_10534(int i);

	int size();

	default boolean isEmpty() {
		return this.size() == 0;
	}

	default Iterator<NbtElement> iterator() {
		return new Iterator<NbtElement>() {
			private int current;

			public boolean hasNext() {
				return this.current < AbstractNbtList.this.size();
			}

			public NbtElement next() {
				if (!this.hasNext()) {
					throw new NoSuchElementException();
				} else {
					return AbstractNbtList.this.method_10534(this.current++);
				}
			}
		};
	}

	default Stream<NbtElement> stream() {
		return StreamSupport.stream(this.spliterator(), false);
	}
}
