package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.nbt.scanner.NbtScanner;
import net.minecraft.nbt.visitor.NbtElementVisitor;
import net.minecraft.nbt.visitor.StringNbtWriter;

/**
 * Represents an NBT string. Its type is {@value NbtElement#STRING_TYPE}.
 * Instances are immutable.
 */
public record NbtString(String value) implements NbtPrimitive {
	private static final int SIZE = 36;
	public static final NbtType<NbtString> TYPE = new NbtType.OfVariableSize<NbtString>() {
		public NbtString read(DataInput dataInput, NbtSizeTracker nbtSizeTracker) throws IOException {
			return NbtString.of(readString(dataInput, nbtSizeTracker));
		}

		@Override
		public NbtScanner.Result doAccept(DataInput input, NbtScanner visitor, NbtSizeTracker tracker) throws IOException {
			return visitor.visitString(readString(input, tracker));
		}

		private static String readString(DataInput input, NbtSizeTracker tracker) throws IOException {
			tracker.add(36L);
			String string = input.readUTF();
			tracker.add(2L, string.length());
			return string;
		}

		@Override
		public void skip(DataInput input, NbtSizeTracker tracker) throws IOException {
			NbtString.skip(input);
		}

		@Override
		public String getCrashReportName() {
			return "STRING";
		}

		@Override
		public String getCommandFeedbackName() {
			return "TAG_String";
		}
	};
	private static final NbtString EMPTY = new NbtString("");
	private static final char DOUBLE_QUOTE = '"';
	private static final char SINGLE_QUOTE = '\'';
	private static final char BACKSLASH = '\\';
	private static final char NULL = '\u0000';

	@Deprecated(
		forRemoval = true
	)
	public NbtString(String value) {
		this.value = value;
	}

	public static void skip(DataInput input) throws IOException {
		input.skipBytes(input.readUnsignedShort());
	}

	/**
	 * {@return the NBT string from {@code value}}
	 */
	public static NbtString of(String value) {
		return value.isEmpty() ? EMPTY : new NbtString(value);
	}

	@Override
	public void write(DataOutput output) throws IOException {
		output.writeUTF(this.value);
	}

	@Override
	public int getSizeInBytes() {
		return 36 + 2 * this.value.length();
	}

	@Override
	public byte getType() {
		return NbtElement.STRING_TYPE;
	}

	@Override
	public NbtType<NbtString> getNbtType() {
		return TYPE;
	}

	@Override
	public String toString() {
		StringNbtWriter stringNbtWriter = new StringNbtWriter();
		stringNbtWriter.visitString(this);
		return stringNbtWriter.getString();
	}

	public NbtString copy() {
		return this;
	}

	@Override
	public Optional<String> asString() {
		return Optional.of(this.value);
	}

	@Override
	public void accept(NbtElementVisitor visitor) {
		visitor.visitString(this);
	}

	/**
	 * {@return the string quoted with quotes and backslashes escaped}
	 * 
	 * @implNote If {@code value} contains one of the singlequote or the double quote,
	 * it tries to use the other quotes to quote the string. If both appear, then the quote
	 * that appeared later will be used to quote the string. If neither of them appears, this
	 * uses a double quote. For example, the string {@code It's a "Tiny Potato"!} will be
	 * escaped as {@code "It's a \"Tiny Potato\"!"}, while the string
	 * {@code It is a "Tiny Potato"!} will be escaped as {@code 'It is a "Tiny Potato"!'}.
	 */
	public static String escape(String value) {
		StringBuilder stringBuilder = new StringBuilder();
		appendEscaped(value, stringBuilder);
		return stringBuilder.toString();
	}

	public static void appendEscaped(String value, StringBuilder builder) {
		int i = builder.length();
		builder.append(' ');
		char c = 0;

		for (int j = 0; j < value.length(); j++) {
			char d = value.charAt(j);
			if (d == '\\') {
				builder.append("\\\\");
			} else if (d != '"' && d != '\'') {
				String string = SnbtParsing.escapeSpecialChar(d);
				if (string != null) {
					builder.append('\\');
					builder.append(string);
				} else {
					builder.append(d);
				}
			} else {
				if (c == 0) {
					c = (char)(d == '"' ? 39 : 34);
				}

				if (c == d) {
					builder.append('\\');
				}

				builder.append(d);
			}
		}

		if (c == 0) {
			c = '"';
		}

		builder.setCharAt(i, c);
		builder.append(c);
	}

	@Override
	public NbtScanner.Result doAccept(NbtScanner visitor) {
		return visitor.visitString(this.value);
	}
}
