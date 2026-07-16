package com.chillguy.vantage.gpu;

import com.chillguy.vantage.VantageClient;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

/**
 * UNTESTED SCAFFOLD — this compiles conceptually and follows the correct
 * LWJGL/OpenGL 4.3 compute shader pattern, but has NOT been run against the
 * real Minecraft render thread/GL context. Expect to debug:
 *   - GL context thread affinity (must run on the render thread)
 *   - buffer layout mismatches (std430 packing is picky — verify offsets
 *     with a GL debug callback if results look wrong)
 *   - synchronization: glMemoryBarrier is required before reading results,
 *     included below, but timing against the frame's draw calls still needs
 *     real profiling
 *   - GL_COMPUTE_SHADER requires OpenGL 4.3+; add a capability check before
 *     enabling this path, and keep EntityCullingManager (CPU path) as the
 *     fallback for anyone below that
 *
 * Data flow: upload entity positions/radii relative to camera -> dispatch
 * compute -> barrier -> read back (visible, lodTier) per entity -> feed into
 * render mixin decisions. Read-back is the expensive/risky part (GPU->CPU
 * stall); the real long-term fix is to avoid reading back at all and instead
 * drive an indirect draw buffer entirely GPU-side. That's a follow-up, not
 * in this scaffold.
 */
public final class GpuLodManager {

	public static final GpuLodManager INSTANCE = new GpuLodManager();

	private static final int MAX_ENTITIES = 4096;
	private static final int ENTITY_STRIDE_FLOATS = 8; // vec4 + vec4

	private int programId = -1;
	private int inputSsbo = -1;
	private int outputSsbo = -1;
	private boolean initialized = false;
	private boolean supported = false;

	private GpuLodManager() {}

	public boolean isSupported() {
		return supported;
	}

	/** Call once, on the render thread, after the GL context exists. */
	public void init() {
		if (initialized) return;
		initialized = true;

		long capabilitiesFlag = GL43C.glGetInteger(GL43C.GL_MAJOR_VERSION);
		if (capabilitiesFlag < 4) {
			supported = false;
			return; // caller should fall back to EntityCullingManager (CPU path)
		}

		try {
			String source = readShaderSource(Identifier.of(VantageClient.MOD_ID, "shaders/compute/entity_lod.comp"));
			int shader = GL43C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
			GL43C.glShaderSource(shader, source);
			GL43C.glCompileShader(shader);
			if (GL43C.glGetShaderi(shader, GL43C.GL_COMPILE_STATUS) == GL43C.GL_FALSE) {
				throw new RuntimeException("Compute shader compile failed: " + GL43C.glGetShaderInfoLog(shader));
			}

			programId = GL43C.glCreateProgram();
			GL43C.glAttachShader(programId, shader);
			GL43C.glLinkProgram(programId);
			if (GL43C.glGetProgrami(programId, GL43C.GL_LINK_STATUS) == GL43C.GL_FALSE) {
				throw new RuntimeException("Compute program link failed: " + GL43C.glGetProgramInfoLog(programId));
			}
			GL43C.glDeleteShader(shader);

			inputSsbo = GL43C.glGenBuffers();
			outputSsbo = GL43C.glGenBuffers();

			GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, inputSsbo);
			GL43C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER,
				(long) MAX_ENTITIES * ENTITY_STRIDE_FLOATS * Float.BYTES, GL43C.GL_DYNAMIC_DRAW);

			GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, outputSsbo);
			GL43C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER,
				(long) MAX_ENTITIES * 2 * Float.BYTES, GL43C.GL_DYNAMIC_DRAW);

			GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);

			supported = true;
		} catch (Exception e) {
			supported = false;
			System.err.println("[Vantage] GPU LOD pass unavailable, falling back to CPU culling: " + e);
		}
	}

	/**
	 * @param entityFloats packed per-entity data, ENTITY_STRIDE_FLOATS floats each
	 *                      (relPos.xyz, radius, threatening, pad, pad, pad)
	 * @param count number of entities actually packed
	 * @return array of [visible, lodTier] pairs, length == count*2, or null if unsupported
	 */
	public float[] dispatch(float[] entityFloats, int count, float[] frustumPlanes,
			float fullDetailDistSq, float simplifiedDistSq, float cullDistSq) {
		if (!supported || count == 0) return null;

		GL43C.glUseProgram(programId);

		GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, inputSsbo);
		FloatBuffer inBuf = MemoryUtil.memAllocFloat(count * ENTITY_STRIDE_FLOATS);
		inBuf.put(entityFloats, 0, count * ENTITY_STRIDE_FLOATS).flip();
		GL43C.glBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0, inBuf);
		MemoryUtil.memFree(inBuf);

		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, inputSsbo);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1, outputSsbo);

		int loc;
		loc = GL43C.glGetUniformLocation(programId, "frustumPlanes");
		GL43C.glUniform4fv(loc, frustumPlanes);
		GL43C.glUniform1f(GL43C.glGetUniformLocation(programId, "fullDetailDistSq"), fullDetailDistSq);
		GL43C.glUniform1f(GL43C.glGetUniformLocation(programId, "simplifiedDistSq"), simplifiedDistSq);
		GL43C.glUniform1f(GL43C.glGetUniformLocation(programId, "cullDistSq"), cullDistSq);

		int groups = (count + 63) / 64;
		GL43C.glDispatchCompute(groups, 1, 1);
		GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);

		GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, outputSsbo);
		ByteBuffer resultBytes = GL43C.glMapBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, 0,
			(long) count * 2 * Float.BYTES, GL43C.GL_MAP_READ_BIT);

		float[] result = new float[count * 2];
		if (resultBytes != null) {
			resultBytes.asFloatBuffer().get(result);
		}
		GL43C.glUnmapBuffer(GL43C.GL_SHADER_STORAGE_BUFFER);
		GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);

		return result;
	}

	public void close() {
		if (programId != -1) GL43C.glDeleteProgram(programId);
		if (inputSsbo != -1) GL43C.glDeleteBuffers(inputSsbo);
		if (outputSsbo != -1) GL43C.glDeleteBuffers(outputSsbo);
	}

	private String readShaderSource(Identifier id) throws IOException {
		String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
		try (InputStream in = GpuLodManager.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("Shader resource not found: " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
