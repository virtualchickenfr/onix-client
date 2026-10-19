package net.minecraft.client.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class RenderPassImpl implements RenderPass {
	protected static final int field_57866 = 1;
	public static final boolean IS_DEVELOPMENT = SharedConstants.isDevelopment;
	private final GlResourceManager resourceManager;
	private final boolean hasDepth;
	private boolean closed;
	@Nullable
	protected CompiledShaderPipeline pipeline;
	protected final GpuBuffer[] vertexBuffers = new GpuBuffer[1];
	@Nullable
	protected GpuBuffer indexBuffer;
	protected VertexFormat.IndexType indexType = VertexFormat.IndexType.INT;
	protected final ScissorState scissorState = new ScissorState();
	protected final HashMap<String, Object> simpleUniforms = new HashMap();
	protected final HashMap<String, GpuTexture> samplerUniforms = new HashMap();
	protected final Set<String> setSimpleUniforms = new HashSet();
	protected final Set<String> setSamplers = new HashSet();

	public RenderPassImpl(GlResourceManager resourceManager, boolean hasDepth) {
		this.resourceManager = resourceManager;
		this.hasDepth = hasDepth;
	}

	public boolean hasDepth() {
		return this.hasDepth;
	}

	@Override
	public void setPipeline(RenderPipeline renderPipeline) {
		if (this.pipeline == null || this.pipeline.info() != renderPipeline) {
			this.setSimpleUniforms.addAll(this.simpleUniforms.keySet());
			this.setSamplers.addAll(this.samplerUniforms.keySet());
		}

		this.pipeline = this.resourceManager.getBackend().compilePipelineCached(renderPipeline);
	}

	@Override
	public void bindSampler(String string, GpuTexture gpuTexture) {
		this.samplerUniforms.put(string, gpuTexture);
		this.setSamplers.add(string);
	}

	@Override
	public void setUniform(String string, int... is) {
		this.simpleUniforms.put(string, is);
		this.setSimpleUniforms.add(string);
	}

	@Override
	public void setUniform(String string, float... fs) {
		this.simpleUniforms.put(string, fs);
		this.setSimpleUniforms.add(string);
	}

	@Override
	public void setUniform(String string, Matrix4f matrix4f) {
		this.simpleUniforms.put(string, matrix4f.get(new float[16]));
		this.setSimpleUniforms.add(string);
	}

	@Override
	public void enableScissor(ScissorState scissorState) {
		this.scissorState.copyFrom(scissorState);
	}

	@Override
	public void enableScissor(int i, int j, int k, int l) {
		this.scissorState.enable(i, j, k, l);
	}

	@Override
	public void disableScissor() {
		this.scissorState.disable();
	}

	@Override
	public void setVertexBuffer(int i, GpuBuffer gpuBuffer) {
		if (i >= 0 && i < 1) {
			this.vertexBuffers[i] = gpuBuffer;
		} else {
			throw new IllegalArgumentException("Vertex buffer slot is out of range: " + i);
		}
	}

	@Override
	public void setIndexBuffer(@Nullable GpuBuffer gpuBuffer, VertexFormat.IndexType indexType) {
		this.indexBuffer = gpuBuffer;
		this.indexType = indexType;
	}

	@Override
	public void drawIndexed(int i, int j) {
		if (this.closed) {
			throw new IllegalStateException("Can't use a closed render pass");
		} else {
			this.resourceManager.drawBoundObjectWithRenderPass(this, i, j, this.indexType);
		}
	}

	@Override
	public void drawMultipleIndexed(Collection<RenderPass.RenderObject> collection, @Nullable GpuBuffer gpuBuffer, @Nullable VertexFormat.IndexType indexType) {
		if (this.closed) {
			throw new IllegalStateException("Can't use a closed render pass");
		} else {
			this.resourceManager.drawObjectsWithRenderPass(this, collection, gpuBuffer, indexType);
		}
	}

	@Override
	public void draw(int i, int j) {
		if (this.closed) {
			throw new IllegalStateException("Can't use a closed render pass");
		} else {
			this.resourceManager.drawBoundObjectWithRenderPass(this, i, j, null);
		}
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			this.resourceManager.closePass();
		}
	}
}
