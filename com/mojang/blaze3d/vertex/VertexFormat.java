package com.mojang.blaze3d.vertex;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.annotation.DeobfuscateClass;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public class VertexFormat {
	public static final int UNKNOWN_ELEMENT = -1;
	private final List<VertexFormatElement> elements;
	private final List<String> names;
	private final int vertexSize;
	private final int elementsMask;
	private final int[] offsetsByElement = new int[32];
	@Nullable
	private GpuBuffer immediateDrawVertexBuffer;
	@Nullable
	private GpuBuffer immediateDrawIndexBuffer;

	VertexFormat(List<VertexFormatElement> elements, List<String> names, IntList offsets, int vertexSize) {
		this.elements = elements;
		this.names = names;
		this.vertexSize = vertexSize;
		this.elementsMask = elements.stream().mapToInt(VertexFormatElement::mask).reduce(0, (a, b) -> a | b);

		for (int i = 0; i < this.offsetsByElement.length; i++) {
			VertexFormatElement vertexFormatElement = VertexFormatElement.byId(i);
			int j = vertexFormatElement != null ? elements.indexOf(vertexFormatElement) : -1;
			this.offsetsByElement[i] = j != -1 ? offsets.getInt(j) : -1;
		}
	}

	public static VertexFormat.Builder builder() {
		return new VertexFormat.Builder();
	}

	public String toString() {
		return "VertexFormat" + this.names;
	}

	public int getVertexSize() {
		return this.vertexSize;
	}

	public List<VertexFormatElement> getElements() {
		return this.elements;
	}

	public List<String> getElementAttributeNames() {
		return this.names;
	}

	public int[] getOffsetsByElement() {
		return this.offsetsByElement;
	}

	public int getOffset(VertexFormatElement element) {
		return this.offsetsByElement[element.id()];
	}

	public boolean contains(VertexFormatElement element) {
		return (this.elementsMask & element.mask()) != 0;
	}

	public int getElementsMask() {
		return this.elementsMask;
	}

	public String getElementName(VertexFormatElement element) {
		int i = this.elements.indexOf(element);
		if (i == -1) {
			throw new IllegalArgumentException(element + " is not contained in format");
		} else {
			return (String)this.names.get(i);
		}
	}

	public boolean equals(Object o) {
		return this == o
			? true
			: o instanceof VertexFormat vertexFormat
				&& this.elementsMask == vertexFormat.elementsMask
				&& this.vertexSize == vertexFormat.vertexSize
				&& this.names.equals(vertexFormat.names)
				&& Arrays.equals(this.offsetsByElement, vertexFormat.offsetsByElement);
	}

	public int hashCode() {
		return this.elementsMask * 31 + Arrays.hashCode(this.offsetsByElement);
	}

	public GpuBuffer uploadImmediateVertexBuffer(ByteBuffer vertexBuffer) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		if (this.immediateDrawVertexBuffer == null) {
			this.immediateDrawVertexBuffer = gpuDevice.createBuffer(
				() -> "Immediate vertex buffer for " + this, BufferType.VERTICES, BufferUsage.DYNAMIC_WRITE, vertexBuffer
			);
		} else {
			CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
			if (this.immediateDrawVertexBuffer.size() < vertexBuffer.remaining()) {
				this.immediateDrawVertexBuffer.close();
				this.immediateDrawVertexBuffer = gpuDevice.createBuffer(
					() -> "Immediate vertex buffer for " + this, BufferType.VERTICES, BufferUsage.DYNAMIC_WRITE, vertexBuffer
				);
			} else {
				commandEncoder.writeToBuffer(this.immediateDrawVertexBuffer, vertexBuffer, 0);
			}
		}

		return this.immediateDrawVertexBuffer;
	}

	public GpuBuffer uploadImmediateIndexBuffer(ByteBuffer indexBuffer) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		if (this.immediateDrawIndexBuffer == null) {
			this.immediateDrawIndexBuffer = RenderSystem.getDevice()
				.createBuffer(() -> "Immediate index buffer for " + this, BufferType.INDICES, BufferUsage.DYNAMIC_WRITE, indexBuffer);
		} else {
			CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
			if (this.immediateDrawIndexBuffer.size() < indexBuffer.remaining()) {
				this.immediateDrawIndexBuffer.close();
				this.immediateDrawIndexBuffer = RenderSystem.getDevice()
					.createBuffer(() -> "Immediate index buffer for " + this, BufferType.INDICES, BufferUsage.DYNAMIC_WRITE, indexBuffer);
			} else {
				commandEncoder.writeToBuffer(this.immediateDrawIndexBuffer, indexBuffer, 0);
			}
		}

		return this.immediateDrawIndexBuffer;
	}

	@Environment(EnvType.CLIENT)
	@DeobfuscateClass
	public static class Builder {
		private final ImmutableMap.Builder<String, VertexFormatElement> elements = ImmutableMap.builder();
		private final IntList offsets = new IntArrayList();
		private int offset;

		Builder() {
		}

		public VertexFormat.Builder add(String name, VertexFormatElement element) {
			this.elements.put(name, element);
			this.offsets.add(this.offset);
			this.offset = this.offset + element.byteSize();
			return this;
		}

		public VertexFormat.Builder padding(int padding) {
			this.offset += padding;
			return this;
		}

		public VertexFormat build() {
			ImmutableMap<String, VertexFormatElement> immutableMap = this.elements.buildOrThrow();
			ImmutableList<VertexFormatElement> immutableList = immutableMap.values().asList();
			ImmutableList<String> immutableList2 = immutableMap.keySet().asList();
			return new VertexFormat(immutableList, immutableList2, this.offsets, this.offset);
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum DrawMode {
		LINES(2, 2, false),
		LINE_STRIP(2, 1, true),
		DEBUG_LINES(2, 2, false),
		DEBUG_LINE_STRIP(2, 1, true),
		TRIANGLES(3, 3, false),
		TRIANGLE_STRIP(3, 1, true),
		TRIANGLE_FAN(3, 1, true),
		QUADS(4, 4, false);

		/**
		 * The number of vertices needed to form a first shape.
		 */
		public final int firstVertexCount;
		/**
		 * The number of vertices needed to form an additional shape. In other words, it's
		 * firstVertexCount - s where s is the number of vertices shared with the previous shape.
		 */
		public final int additionalVertexCount;
		/**
		 * Whether there are shared vertices in consecutive shapes.
		 */
		public final boolean shareVertices;

		private DrawMode(final int firstVertexCount, final int additionalVertexCount, final boolean shareVertices) {
			this.firstVertexCount = firstVertexCount;
			this.additionalVertexCount = additionalVertexCount;
			this.shareVertices = shareVertices;
		}

		public int getIndexCount(int vertexCount) {
			return switch (this) {
				case LINES, QUADS -> vertexCount / 4 * 6;
				case LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP, TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN -> vertexCount;
				default -> 0;
			};
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum IndexType {
		SHORT(2),
		INT(4);

		public final int size;

		private IndexType(final int size) {
			this.size = size;
		}

		public static VertexFormat.IndexType smallestFor(int i) {
			return (i & -65536) != 0 ? INT : SHORT;
		}
	}
}
