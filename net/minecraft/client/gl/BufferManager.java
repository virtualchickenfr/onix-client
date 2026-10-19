package net.minecraft.client.gl;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.ARBVertexAttribBinding;
import org.lwjgl.opengl.GLCapabilities;

@Environment(EnvType.CLIENT)
public abstract class BufferManager {
	public static BufferManager create(GLCapabilities capabilities, DebugLabelManager labeler, Set<String> usedCapabilities) {
		if (capabilities.GL_ARB_vertex_attrib_binding && GlBackend.allowGlArbVABinding) {
			usedCapabilities.add("GL_ARB_vertex_attrib_binding");
			return new BufferManager.ARBBufferManager(labeler);
		} else {
			return new BufferManager.DefaultBufferManager(labeler);
		}
	}

	public abstract void setupBuffer(VertexFormat format, GlGpuBuffer into);

	@Environment(EnvType.CLIENT)
	static class ARBBufferManager extends BufferManager {
		private final Map<VertexFormat, BufferManager.AllocatedBuffer> cache = new HashMap();
		private final DebugLabelManager labeler;

		public ARBBufferManager(DebugLabelManager labeler) {
			this.labeler = labeler;
		}

		@Override
		public void setupBuffer(VertexFormat format, GlGpuBuffer into) {
			BufferManager.AllocatedBuffer allocatedBuffer = (BufferManager.AllocatedBuffer)this.cache.get(format);
			if (allocatedBuffer == null) {
				int i = GlStateManager._glGenVertexArrays();
				GlStateManager._glBindVertexArray(i);
				ARBVertexAttribBinding.glBindVertexBuffer(0, into.id, 0L, format.getVertexSize());
				List<VertexFormatElement> list = format.getElements();

				for (int j = 0; j < list.size(); j++) {
					VertexFormatElement vertexFormatElement = (VertexFormatElement)list.get(j);
					GlStateManager._enableVertexAttribArray(j);
					switch (vertexFormatElement.usage()) {
						case POSITION:
						case GENERIC:
							ARBVertexAttribBinding.glVertexAttribFormat(
								j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), false, format.getOffset(vertexFormatElement)
							);
							break;
						case NORMAL:
						case COLOR:
							ARBVertexAttribBinding.glVertexAttribFormat(
								j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), true, format.getOffset(vertexFormatElement)
							);
							break;
						case UV:
							if (vertexFormatElement.type() == VertexFormatElement.Type.FLOAT) {
								ARBVertexAttribBinding.glVertexAttribFormat(
									j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), false, format.getOffset(vertexFormatElement)
								);
							} else {
								ARBVertexAttribBinding.glVertexAttribIFormat(
									j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), format.getOffset(vertexFormatElement)
								);
							}
					}

					ARBVertexAttribBinding.glVertexAttribBinding(j, 0);
				}

				BufferManager.AllocatedBuffer allocatedBuffer2 = new BufferManager.AllocatedBuffer(i, format, into);
				this.labeler.labelAllocatedBuffer(allocatedBuffer2);
				this.cache.put(format, allocatedBuffer2);
			} else {
				GlStateManager._glBindVertexArray(allocatedBuffer.glId);
				if (allocatedBuffer.buffer != into) {
					if (allocatedBuffer.buffer != null && allocatedBuffer.buffer.id == into.id) {
						ARBVertexAttribBinding.glBindVertexBuffer(0, 0, 0L, 0);
					}

					ARBVertexAttribBinding.glBindVertexBuffer(0, into.id, 0L, format.getVertexSize());
					allocatedBuffer.buffer = into;
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class AllocatedBuffer {
		final int glId;
		final VertexFormat vertexFormat;
		@Nullable
		GlGpuBuffer buffer;

		AllocatedBuffer(int glId, VertexFormat vertexFormat, @Nullable GlGpuBuffer buffer) {
			this.glId = glId;
			this.vertexFormat = vertexFormat;
			this.buffer = buffer;
		}
	}

	@Environment(EnvType.CLIENT)
	static class DefaultBufferManager extends BufferManager {
		private final Map<VertexFormat, BufferManager.AllocatedBuffer> cache = new HashMap();
		private final DebugLabelManager labeler;

		public DefaultBufferManager(DebugLabelManager labeler) {
			this.labeler = labeler;
		}

		@Override
		public void setupBuffer(VertexFormat format, GlGpuBuffer into) {
			BufferManager.AllocatedBuffer allocatedBuffer = (BufferManager.AllocatedBuffer)this.cache.get(format);
			if (allocatedBuffer == null) {
				int i = GlStateManager._glGenVertexArrays();
				GlStateManager._glBindVertexArray(i);
				GlStateManager._glBindBuffer(GlConst.GL_ARRAY_BUFFER, into.id);
				setupBuffer(format, true);
				BufferManager.AllocatedBuffer allocatedBuffer2 = new BufferManager.AllocatedBuffer(i, format, into);
				this.labeler.labelAllocatedBuffer(allocatedBuffer2);
				this.cache.put(format, allocatedBuffer2);
			} else {
				GlStateManager._glBindVertexArray(allocatedBuffer.glId);
				if (allocatedBuffer.buffer != into) {
					GlStateManager._glBindBuffer(GlConst.GL_ARRAY_BUFFER, into.id);
					allocatedBuffer.buffer = into;
					setupBuffer(format, false);
				}
			}
		}

		private static void setupBuffer(VertexFormat format, boolean vbaIsNew) {
			int i = format.getVertexSize();
			List<VertexFormatElement> list = format.getElements();

			for (int j = 0; j < list.size(); j++) {
				VertexFormatElement vertexFormatElement = (VertexFormatElement)list.get(j);
				if (vbaIsNew) {
					GlStateManager._enableVertexAttribArray(j);
				}

				switch (vertexFormatElement.usage()) {
					case POSITION:
					case GENERIC:
						GlStateManager._vertexAttribPointer(
							j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), false, i, format.getOffset(vertexFormatElement)
						);
						break;
					case NORMAL:
					case COLOR:
						GlStateManager._vertexAttribPointer(
							j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), true, i, format.getOffset(vertexFormatElement)
						);
						break;
					case UV:
						if (vertexFormatElement.type() == VertexFormatElement.Type.FLOAT) {
							GlStateManager._vertexAttribPointer(
								j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), false, i, format.getOffset(vertexFormatElement)
							);
						} else {
							GlStateManager._vertexAttribIPointer(j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), i, format.getOffset(vertexFormatElement));
						}
				}
			}
		}
	}
}
