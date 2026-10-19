package net.minecraft.nbt;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import net.minecraft.nbt.scanner.NbtScanner;
import net.minecraft.nbt.visitor.NbtElementVisitor;
import net.minecraft.nbt.visitor.StringNbtWriter;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Represents an NBT compound object. This mutable object holds unordered key-value pairs
 * with distinct case-sensitive string keys. This can effectively be used like a
 * {@code HashMap<String, NbtElement>}. Note that this <strong>does not</strong> implement
 * {@link java.util.Map}. Its type is {@value NbtElement#COMPOUND_TYPE}.
 * 
 * <p>There are two ways to use this compound; one is to create NBT instances yourself and use
 * {@link #get(String)} or {@link #put(String, NbtElement)}. Manual casting is required in
 * this case. The other, easier way is to use methods with type names, such as
 * {@link #getInt(String)} or {@link #putInt(String, int)}. Where applicable, these methods
 * return and accept Java types (e.g. {@code int}, {@code long[]}) instead of {@link NbtElement}
 * subclasses. Note that there is no {@code putCompound} method, since you can just use the
 * put method. These getters also have the advantage of providing type safety, because if
 * type mismatch occurs or there is no such element in the compound, it returns the default
 * value for that type instead of throwing or returning {@code null}.
 */
public final class NbtCompound implements NbtElement {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Codec<NbtCompound> CODEC = Codec.PASSTHROUGH
		.comapFlatMap(
			dynamic -> {
				NbtElement nbtElement = dynamic.convert(NbtOps.INSTANCE).getValue();
				return nbtElement instanceof NbtCompound nbtCompound
					? DataResult.success(nbtCompound == dynamic.getValue() ? nbtCompound.copy() : nbtCompound)
					: DataResult.error(() -> "Not a compound tag: " + nbtElement);
			},
			nbt -> new Dynamic<>(NbtOps.INSTANCE, nbt.copy())
		);
	private static final int SIZE = 48;
	private static final int field_41719 = 32;
	public static final NbtType<NbtCompound> TYPE = new NbtType.OfVariableSize<NbtCompound>() {
		public NbtCompound read(DataInput dataInput, NbtSizeTracker nbtSizeTracker) throws IOException {
			nbtSizeTracker.pushStack();

			NbtCompound var3;
			try {
				var3 = readCompound(dataInput, nbtSizeTracker);
			} finally {
				nbtSizeTracker.popStack();
			}

			return var3;
		}

		private static NbtCompound readCompound(DataInput input, NbtSizeTracker tracker) throws IOException {
			tracker.add(48L);
			Map<String, NbtElement> map = Maps.<String, NbtElement>newHashMap();

			byte b;
			while ((b = input.readByte()) != 0) {
				String string = readString(input, tracker);
				NbtElement nbtElement = NbtCompound.read(NbtTypes.byId(b), string, input, tracker);
				if (map.put(string, nbtElement) == null) {
					tracker.add(36L);
				}
			}

			return new NbtCompound(map);
		}

		@Override
		public NbtScanner.Result doAccept(DataInput input, NbtScanner visitor, NbtSizeTracker tracker) throws IOException {
			tracker.pushStack();

			NbtScanner.Result var4;
			try {
				var4 = scanCompound(input, visitor, tracker);
			} finally {
				tracker.popStack();
			}

			return var4;
		}

		private static NbtScanner.Result scanCompound(DataInput input, NbtScanner visitor, NbtSizeTracker tracker) throws IOException {
			tracker.add(48L);

			byte b;
			label35:
			while ((b = input.readByte()) != 0) {
				NbtType<?> nbtType = NbtTypes.byId(b);
				switch (visitor.visitSubNbtType(nbtType)) {
					case HALT:
						return NbtScanner.Result.HALT;
					case BREAK:
						NbtString.skip(input);
						nbtType.skip(input, tracker);
						break label35;
					case SKIP:
						NbtString.skip(input);
						nbtType.skip(input, tracker);
						break;
					default:
						String string = readString(input, tracker);
						switch (visitor.startSubNbt(nbtType, string)) {
							case HALT:
								return NbtScanner.Result.HALT;
							case BREAK:
								nbtType.skip(input, tracker);
								break label35;
							case SKIP:
								nbtType.skip(input, tracker);
								break;
							default:
								tracker.add(36L);
								switch (nbtType.doAccept(input, visitor, tracker)) {
									case HALT:
										return NbtScanner.Result.HALT;
									case BREAK:
								}
						}
				}
			}

			if (b != 0) {
				while ((b = input.readByte()) != 0) {
					NbtString.skip(input);
					NbtTypes.byId(b).skip(input, tracker);
				}
			}

			return visitor.endNested();
		}

		private static String readString(DataInput input, NbtSizeTracker tracker) throws IOException {
			String string = input.readUTF();
			tracker.add(28L);
			tracker.add(2L, string.length());
			return string;
		}

		@Override
		public void skip(DataInput input, NbtSizeTracker tracker) throws IOException {
			tracker.pushStack();

			byte b;
			try {
				while ((b = input.readByte()) != 0) {
					NbtString.skip(input);
					NbtTypes.byId(b).skip(input, tracker);
				}
			} finally {
				tracker.popStack();
			}
		}

		@Override
		public String getCrashReportName() {
			return "COMPOUND";
		}

		@Override
		public String getCommandFeedbackName() {
			return "TAG_Compound";
		}
	};
	private final Map<String, NbtElement> entries;

	NbtCompound(Map<String, NbtElement> entries) {
		this.entries = entries;
	}

	public NbtCompound() {
		this(new HashMap());
	}

	@Override
	public void write(DataOutput output) throws IOException {
		for (String string : this.entries.keySet()) {
			NbtElement nbtElement = (NbtElement)this.entries.get(string);
			write(string, nbtElement, output);
		}

		output.writeByte(0);
	}

	@Override
	public int getSizeInBytes() {
		int i = 48;

		for (Entry<String, NbtElement> entry : this.entries.entrySet()) {
			i += 28 + 2 * ((String)entry.getKey()).length();
			i += 36;
			i += ((NbtElement)entry.getValue()).getSizeInBytes();
		}

		return i;
	}

	/**
	 * {@return the set of keys in this compound}
	 */
	public Set<String> getKeys() {
		return this.entries.keySet();
	}

	public Set<Entry<String, NbtElement>> entrySet() {
		return this.entries.entrySet();
	}

	public Collection<NbtElement> values() {
		return this.entries.values();
	}

	public void forEach(BiConsumer<String, NbtElement> entryConsumer) {
		this.entries.forEach(entryConsumer);
	}

	@Override
	public byte getType() {
		return NbtElement.COMPOUND_TYPE;
	}

	@Override
	public NbtType<NbtCompound> getNbtType() {
		return TYPE;
	}

	/**
	 * {@return the size of this compound}
	 */
	public int getSize() {
		return this.entries.size();
	}

	/**
	 * Puts an element to this compound.
	 * 
	 * @return the previous value, or {@code null} if there was none
	 * @see #get(String)
	 */
	@Nullable
	public NbtElement put(String key, NbtElement element) {
		return (NbtElement)this.entries.put(key, element);
	}

	/**
	 * Puts a {@code byte} to this compound.
	 * 
	 * @see #getByte(String)
	 */
	public void putByte(String key, byte value) {
		this.entries.put(key, NbtByte.of(value));
	}

	/**
	 * Puts a {@code short} to this compound.
	 * 
	 * @see #getShort(String)
	 */
	public void putShort(String key, short value) {
		this.entries.put(key, NbtShort.of(value));
	}

	/**
	 * Puts an {@code int} to this compound.
	 * 
	 * @see #getInt(String)
	 */
	public void putInt(String key, int value) {
		this.entries.put(key, NbtInt.of(value));
	}

	/**
	 * Puts a {@code long} to this compound.
	 * 
	 * @see #getLong(String)
	 */
	public void putLong(String key, long value) {
		this.entries.put(key, NbtLong.of(value));
	}

	/**
	 * Puts a {@code float} to this compound.
	 * 
	 * @see #getFloat(String)
	 */
	public void putFloat(String key, float value) {
		this.entries.put(key, NbtFloat.of(value));
	}

	/**
	 * Puts a {@code double} to this compound.
	 * 
	 * @see #getDouble(String)
	 */
	public void putDouble(String key, double value) {
		this.entries.put(key, NbtDouble.of(value));
	}

	/**
	 * Puts a {@link String} to this compound.
	 * 
	 * @see #getString(String)
	 */
	public void putString(String key, String value) {
		this.entries.put(key, NbtString.of(value));
	}

	/**
	 * Puts a byte array to this compound. This does not copy the array.
	 * 
	 * @see #getByteArray(String)
	 * @see #putByteArray(String, List)
	 */
	public void putByteArray(String key, byte[] value) {
		this.entries.put(key, new NbtByteArray(value));
	}

	/**
	 * Puts an int array to this compound. This does not copy the array.
	 * 
	 * @see #getIntArray(String)
	 * @see #putIntArray(String, List)
	 */
	public void putIntArray(String key, int[] value) {
		this.entries.put(key, new NbtIntArray(value));
	}

	/**
	 * Puts a long array to this compound. This does not copy the array.
	 * 
	 * @see #getLongArray(String)
	 * @see #putLongArray(String, List)
	 */
	public void putLongArray(String key, long[] value) {
		this.entries.put(key, new NbtLongArray(value));
	}

	/**
	 * Puts a {@code boolean} to this compound. The value is stored as {@link NbtByte}.
	 * 
	 * @see #getBoolean(String)
	 */
	public void putBoolean(String key, boolean value) {
		this.entries.put(key, NbtByte.of(value));
	}

	/**
	 * {@return the element associated with the key from this compound, or
	 * {@code null} if there is none}
	 * 
	 * @apiNote This method does not provide type safety; if the type is known, it is
	 * recommended to use other type-specific methods instead.
	 * 
	 * @see #put(String, NbtElement)
	 */
	@Nullable
	public NbtElement get(String key) {
		return (NbtElement)this.entries.get(key);
	}

	/**
	 * Determines whether the NBT compound object contains the specified key.
	 * 
	 * @return {@code true} if the key exists, else {@code false}
	 */
	public boolean contains(String key) {
		return this.entries.containsKey(key);
	}

	private Optional<NbtElement> getOptional(String key) {
		return Optional.ofNullable((NbtElement)this.entries.get(key));
	}

	/**
	 * {@return the {@code byte} associated with {@code key}, or {@code 0} if there is no number
	 * stored with the key}
	 * 
	 * <p>If a non-byte numeric value is stored, this will cast the value.
	 * 
	 * @see #putByte(String, byte)
	 * @see AbstractNbtNumber#byteValue()
	 */
	public Optional<Byte> getByte(String key) {
		return this.getOptional(key).flatMap(NbtElement::asByte);
	}

	/**
	 * {@return the {@code byte} associated with {@code key}, or {@code fallback} if there is
	 * no number stored with the key}
	 * 
	 * <p>If a non-byte numeric value is stored, this will cast the value.
	 * 
	 * @see #putByte(String, byte)
	 * @see AbstractNbtNumber#byteValue()
	 */
	public byte getByte(String key, byte fallback) {
		return this.entries.get(key) instanceof AbstractNbtNumber abstractNbtNumber ? abstractNbtNumber.byteValue() : fallback;
	}

	/**
	 * {@return the {@code short} associated with {@code key}, or {@code 0} if there is no number
	 * stored with the key}
	 * 
	 * <p>If a non-short numeric value is stored, this will cast the value.
	 * 
	 * @see #putShort(String, short)
	 * @see AbstractNbtNumber#shortValue()
	 */
	public Optional<Short> getShort(String key) {
		return this.getOptional(key).flatMap(NbtElement::asShort);
	}

	/**
	 * {@return the {@code short} associated with {@code key}, or {@code fallback} if there is
	 * no number stored with the key}
	 * 
	 * <p>If a non-short numeric value is stored, this will cast the value.
	 * 
	 * @see #putShort(String, short)
	 * @see AbstractNbtNumber#shortValue()
	 */
	public short getShort(String key, short fallback) {
		return this.entries.get(key) instanceof AbstractNbtNumber abstractNbtNumber ? abstractNbtNumber.shortValue() : fallback;
	}

	/**
	 * {@return the {@code int} associated with {@code key}, or {@code 0} if there is no number
	 * stored with the key}
	 * 
	 * <p>If a non-integer numeric value is stored, this will cast the value.
	 * 
	 * @see #putInt(String, int)
	 * @see AbstractNbtNumber#intValue()
	 */
	public Optional<Integer> getInt(String key) {
		return this.getOptional(key).flatMap(NbtElement::asInt);
	}

	/**
	 * {@return the {@code int} associated with {@code key}, or {@code fallback} if there is
	 * no number stored with the key}
	 * 
	 * <p>If a non-integer numeric value is stored, this will cast the value.
	 * 
	 * @see #putInt(String, int)
	 * @see AbstractNbtNumber#intValue()
	 */
	public int getInt(String key, int fallback) {
		return this.entries.get(key) instanceof AbstractNbtNumber abstractNbtNumber ? abstractNbtNumber.intValue() : fallback;
	}

	/**
	 * {@return the {@code long} associated with {@code key}, or {@code 0L} if there is no number
	 * stored with the key}
	 * 
	 * <p>If a non-long numeric value is stored, this will cast the value.
	 * 
	 * @see #putLong(String, long)
	 * @see AbstractNbtNumber#longValue()
	 */
	public Optional<Long> getLong(String key) {
		return this.getOptional(key).flatMap(NbtElement::asLong);
	}

	/**
	 * {@return the {@code long} associated with {@code key}, or {@code fallback} if there is
	 * no number stored with the key}
	 * 
	 * <p>If a non-long numeric value is stored, this will cast the value.
	 * 
	 * @see #putLong(String, long)
	 * @see AbstractNbtNumber#longValue()
	 */
	public long getLong(String key, long fallback) {
		return this.entries.get(key) instanceof AbstractNbtNumber abstractNbtNumber ? abstractNbtNumber.longValue() : fallback;
	}

	/**
	 * {@return the {@code float} associated with {@code key}, or {@code 0.0f} if there is
	 * no number stored with the key}
	 * 
	 * <p>If a non-float numeric value is stored, this will cast the value.
	 * 
	 * @see #putFloat(String, float)
	 * @see AbstractNbtNumber#floatValue()
	 */
	public Optional<Float> getFloat(String key) {
		return this.getOptional(key).flatMap(NbtElement::asFloat);
	}

	/**
	 * {@return the {@code float} associated with {@code key}, or {@code fallback} if there is
	 * no number stored with the key}
	 * 
	 * <p>If a non-float numeric value is stored, this will cast the value.
	 * 
	 * @see #putFloat(String, float)
	 * @see AbstractNbtNumber#floatValue()
	 */
	public float getFloat(String key, float fallback) {
		return this.entries.get(key) instanceof AbstractNbtNumber abstractNbtNumber ? abstractNbtNumber.floatValue() : fallback;
	}

	/**
	 * {@return the {@code double} associated with {@code key}, or {@code 0.0} if there is
	 * no number stored with the key}
	 * 
	 * <p>If a non-double numeric value is stored, this will cast the value.
	 * 
	 * @see #putDouble(String, double)
	 * @see AbstractNbtNumber#doubleValue()
	 */
	public Optional<Double> getDouble(String key) {
		return this.getOptional(key).flatMap(NbtElement::asDouble);
	}

	/**
	 * {@return the {@code double} associated with {@code key}, or {@code fallback} if there
	 * is no number stored with the key}
	 * 
	 * <p>If a non-double numeric value is stored, this will cast the value.
	 * 
	 * @see #putDouble(String, double)
	 * @see AbstractNbtNumber#doubleValue()
	 */
	public double getDouble(String key, double fallback) {
		return this.entries.get(key) instanceof AbstractNbtNumber abstractNbtNumber ? abstractNbtNumber.doubleValue() : fallback;
	}

	/**
	 * {@return the {@link String} associated with {@code key}, or an empty string if there
	 * is no string stored with the key}
	 * 
	 * @see #putString(String, String)
	 * @see NbtElement#asString()
	 */
	public Optional<String> getString(String key) {
		return this.getOptional(key).flatMap(NbtElement::asString);
	}

	/**
	 * {@return the {@link String} associated with {@code key}, or {@code fallback} if there
	 * is no string stored with the key}
	 * 
	 * @see #putString(String, String)
	 * @see NbtElement#asString()
	 */
	public String getString(String key, String fallback) {
		return this.entries.get(key) instanceof NbtString(String var8) ? var8 : fallback;
	}

	/**
	 * {@return the byte array associated with {@code key}, or an empty byte array if there is no
	 * byte array stored with the key}
	 * 
	 * @apiNote Modifying the returned array also modifies the NBT byte array.
	 * 
	 * @see #putByteArray(String, byte[])
	 * @see NbtByteArray#getByteArray()
	 */
	public Optional<byte[]> getByteArray(String key) {
		return this.entries.get(key) instanceof NbtByteArray nbtByteArray ? Optional.of(nbtByteArray.getByteArray()) : Optional.empty();
	}

	/**
	 * {@return the int array associated with {@code key}, or an empty int array if there is no
	 * int array stored with the key}
	 * 
	 * @apiNote Modifying the returned array also modifies the NBT int array.
	 * 
	 * @see #putIntArray(String, int[])
	 * @see NbtIntArray#getIntArray()
	 */
	public Optional<int[]> getIntArray(String key) {
		return this.entries.get(key) instanceof NbtIntArray nbtIntArray ? Optional.of(nbtIntArray.getIntArray()) : Optional.empty();
	}

	/**
	 * {@return the long array associated with {@code key}, or an empty long array if there is no
	 * long array stored with the key}
	 * 
	 * @apiNote Modifying the returned array also modifies the NBT long array.
	 * 
	 * @see #putLongArray(String, long[])
	 * @see NbtLongArray#getLongArray()
	 */
	public Optional<long[]> getLongArray(String key) {
		return this.entries.get(key) instanceof NbtLongArray nbtLongArray ? Optional.of(nbtLongArray.getLongArray()) : Optional.empty();
	}

	/**
	 * {@return the compound associated with {@code key}, or an empty compound if there is no
	 * compound stored with the key}
	 * 
	 * @see #put(String, NbtElement)
	 */
	public Optional<NbtCompound> getCompound(String key) {
		return this.entries.get(key) instanceof NbtCompound nbtCompound ? Optional.of(nbtCompound) : Optional.empty();
	}

	public NbtCompound getCompoundOrEmpty(String key) {
		return (NbtCompound)this.getCompound(key).orElseGet(NbtCompound::new);
	}

	/**
	 * {@return the list associated with {@code key}, or an empty list if there is no
	 * list stored with the key and the type}
	 * 
	 * @see #put(String, NbtElement)
	 */
	public Optional<NbtList> getList(String key) {
		return this.entries.get(key) instanceof NbtList nbtList ? Optional.of(nbtList) : Optional.empty();
	}

	public NbtList getListOrEmpty(String key) {
		return (NbtList)this.getList(key).orElseGet(NbtList::new);
	}

	/**
	 * {@return the boolean value stored with the {@code key}}
	 * 
	 * @implNote Since NBT does not have a boolean type, {@link NbtByte} is used instead. This
	 * method returns {@code true} for any values which, after casting to {@code byte} as
	 * described at {@link #getByte(String)}, is not {@code 0}. Since all non-numeric values
	 * become {@code 0} during casting to bytes, this method returns {@code false} for those
	 * as well. This includes values often considered truthy in other languages, such as a
	 * non-empty string or list.
	 */
	public Optional<Boolean> getBoolean(String key) {
		return this.getOptional(key).flatMap(NbtElement::asBoolean);
	}

	public boolean getBoolean(String key, boolean fallback) {
		return this.getByte(key, (byte)(fallback ? 1 : 0)) != 0;
	}

	/**
	 * Removes the entry with the specified {@code key}. Does nothing if there is none.
	 */
	public void remove(String key) {
		this.entries.remove(key);
	}

	@Override
	public String toString() {
		StringNbtWriter stringNbtWriter = new StringNbtWriter();
		stringNbtWriter.visitCompound(this);
		return stringNbtWriter.getString();
	}

	/**
	 * {@return whether the compound has no entries}
	 */
	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

	protected NbtCompound shallowCopy() {
		return new NbtCompound(new HashMap(this.entries));
	}

	public NbtCompound copy() {
		HashMap<String, NbtElement> hashMap = new HashMap();
		this.entries.forEach((key, value) -> hashMap.put(key, value.copy()));
		return new NbtCompound(hashMap);
	}

	@Override
	public Optional<NbtCompound> asCompound() {
		return Optional.of(this);
	}

	public boolean equals(Object o) {
		return this == o ? true : o instanceof NbtCompound && Objects.equals(this.entries, ((NbtCompound)o).entries);
	}

	public int hashCode() {
		return this.entries.hashCode();
	}

	private static void write(String key, NbtElement element, DataOutput output) throws IOException {
		output.writeByte(element.getType());
		if (element.getType() != 0) {
			output.writeUTF(key);
			element.write(output);
		}
	}

	static NbtElement read(NbtType<?> reader, String key, DataInput input, NbtSizeTracker tracker) {
		try {
			return reader.read(input, tracker);
		} catch (IOException var7) {
			CrashReport crashReport = CrashReport.create(var7, "Loading NBT data");
			CrashReportSection crashReportSection = crashReport.addElement("NBT Tag");
			crashReportSection.add("Tag name", key);
			crashReportSection.add("Tag type", reader.getCrashReportName());
			throw new NbtCrashException(crashReport);
		}
	}

	/**
	 * Merges the entries of {@code source} to this compound. The passed compound will not
	 * be modified. If both compounds contain a compound with the same key, they will be
	 * merged; otherwise the values of this compound will be overwritten.
	 * 
	 * @return this compound with entries merged
	 */
	public NbtCompound copyFrom(NbtCompound source) {
		for (String string : source.entries.keySet()) {
			NbtElement nbtElement = (NbtElement)source.entries.get(string);
			if (nbtElement instanceof NbtCompound nbtCompound && this.entries.get(string) instanceof NbtCompound nbtCompound2) {
				nbtCompound2.copyFrom(nbtCompound);
			} else {
				this.put(string, nbtElement.copy());
			}
		}

		return this;
	}

	@Override
	public void accept(NbtElementVisitor visitor) {
		visitor.visitCompound(this);
	}

	@Override
	public NbtScanner.Result doAccept(NbtScanner visitor) {
		for (Entry<String, NbtElement> entry : this.entries.entrySet()) {
			NbtElement nbtElement = (NbtElement)entry.getValue();
			NbtType<?> nbtType = nbtElement.getNbtType();
			NbtScanner.NestedResult nestedResult = visitor.visitSubNbtType(nbtType);
			switch (nestedResult) {
				case HALT:
					return NbtScanner.Result.HALT;
				case BREAK:
					return visitor.endNested();
				case SKIP:
					break;
				default:
					nestedResult = visitor.startSubNbt(nbtType, (String)entry.getKey());
					switch (nestedResult) {
						case HALT:
							return NbtScanner.Result.HALT;
						case BREAK:
							return visitor.endNested();
						case SKIP:
							break;
						default:
							NbtScanner.Result result = nbtElement.doAccept(visitor);
							switch (result) {
								case HALT:
									return NbtScanner.Result.HALT;
								case BREAK:
									return visitor.endNested();
							}
					}
			}
		}

		return visitor.endNested();
	}

	public <T> void put(String key, Codec<T> codec, T value) {
		this.put(key, codec, NbtOps.INSTANCE, value);
	}

	public <T> void putNullable(String key, Codec<T> codec, @Nullable T value) {
		if (value != null) {
			this.put(key, codec, value);
		}
	}

	public <T> void put(String key, Codec<T> codec, DynamicOps<NbtElement> ops, T value) {
		this.put(key, codec.encodeStart(ops, value).getOrThrow());
	}

	public <T> void putNullable(String key, Codec<T> codec, DynamicOps<NbtElement> ops, @Nullable T value) {
		if (value != null) {
			this.put(key, codec, ops, value);
		}
	}

	public <T> void copyFromCodec(MapCodec<T> codec, T value) {
		this.copyFromCodec(codec, NbtOps.INSTANCE, value);
	}

	public <T> void copyFromCodec(MapCodec<T> codec, DynamicOps<NbtElement> ops, T value) {
		this.copyFrom((NbtCompound)codec.encoder().encodeStart(ops, value).getOrThrow());
	}

	public <T> Optional<T> get(String key, Codec<T> codec) {
		return this.get(key, codec, NbtOps.INSTANCE);
	}

	public <T> Optional<T> get(String key, Codec<T> codec, DynamicOps<NbtElement> ops) {
		NbtElement nbtElement = this.get(key);
		return nbtElement == null
			? Optional.empty()
			: codec.parse(ops, nbtElement).resultOrPartial(error -> LOGGER.error("Failed to read field ({}={}): {}", key, nbtElement, error));
	}

	public <T> Optional<T> decode(MapCodec<T> codec) {
		return this.decode(codec, NbtOps.INSTANCE);
	}

	public <T> Optional<T> decode(MapCodec<T> codec, DynamicOps<NbtElement> ops) {
		return codec.decode(ops, ops.getMap(this).getOrThrow()).resultOrPartial(error -> LOGGER.error("Failed to read value ({}): {}", this, error));
	}
}
