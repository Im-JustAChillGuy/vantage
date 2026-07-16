package com.chillguy.vantage.gpu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Computes the 6 view-frustum planes from the camera's own public data
 * (position, rotation, FOV) rather than reaching into Minecraft's Frustum
 * class, whose plane fields are private and obfuscation-mapped — reading
 * them would need reflection or a mixin accessor, either of which breaks
 * again on the next Yarn remap. This is self-contained standard graphics
 * math (Gribb-Hartmann plane extraction from a view-projection matrix) and
 * has no dependency on Minecraft's internal render pipeline at all, so it
 * should survive future mapping churn untouched.
 *
 * UNTESTED: the math follows the standard method, but hasn't been verified
 * against real gameplay — in particular the FOV/aspect-ratio values pulled
 * from client options are an approximation (Minecraft's effective FOV can
 * shift slightly per-frame from things like sprint FOV-kick, which this
 * does not account for). Good enough for LOD/cull purposes where being a
 * frame or two stale on FOV doesn't visibly matter; not precise enough if
 * you ever need pixel-accurate culling.
 */
final class FrustumPlaneExtractor {

	private FrustumPlaneExtractor() {}

	private static final float NEAR_PLANE = 0.05f;

	/**
	 * @return 6 planes packed as 24 floats (xyz = normal, w = distance),
	 *         normalized, ready to upload as the shader's frustumPlanes uniform.
	 */
	static float[] extract(MinecraftClient client, Camera camera) {
		float fovDegrees = (float) client.options.getFov().getValue();
		int width = client.getWindow().getFramebufferWidth();
		int height = client.getWindow().getFramebufferHeight();
		float aspect = height == 0 ? 1f : (float) width / height;
		float farPlane = (float) (client.options.getViewDistance().getValue() * 16 * 2); // chunks -> blocks, generous margin

		Matrix4f projection = new Matrix4f().perspective(
			(float) Math.toRadians(fovDegrees), aspect, NEAR_PLANE, farPlane);

		// View matrix: camera-space rotation only — planes are computed
		// relative to the camera, matching the relative entity positions
		// GpuLodDispatcher already uploads.
		Quaternionf rotation = camera.getRotation();
		Matrix4f view = new Matrix4f().rotate(rotation.conjugate(new Quaternionf()));

		Matrix4f viewProjection = projection.mul(view, new Matrix4f());

		float[] planes = new float[24];
		writePlane(viewProjection, planes, 0, 0, +1);  // left
		writePlane(viewProjection, planes, 4, 0, -1);  // right
		writePlane(viewProjection, planes, 8, 1, +1);  // bottom
		writePlane(viewProjection, planes, 12, 1, -1); // top
		writePlane(viewProjection, planes, 16, 2, +1); // near
		writePlane(viewProjection, planes, 20, 2, -1); // far
		return planes;
	}

	/** Gribb-Hartmann: plane = row4 +/- rowN of the view-projection matrix. */
	private static void writePlane(Matrix4f m, float[] out, int offset, int rowXYZ, int sign) {
		Vector4f row4 = new Vector4f(m.m03(), m.m13(), m.m23(), m.m33());
		Vector4f rowN = switch (rowXYZ) {
			case 0 -> new Vector4f(m.m00(), m.m10(), m.m20(), m.m30());
			case 1 -> new Vector4f(m.m01(), m.m11(), m.m21(), m.m31());
			default -> new Vector4f(m.m02(), m.m12(), m.m22(), m.m32());
		};

		Vector4f plane = new Vector4f(
			row4.x + sign * rowN.x,
			row4.y + sign * rowN.y,
			row4.z + sign * rowN.z,
			row4.w + sign * rowN.w
		);

		float len = new Vector3f(plane.x, plane.y, plane.z).length();
		if (len > 1e-6f) {
			plane.mul(1f / len);
		}

		out[offset] = plane.x;
		out[offset + 1] = plane.y;
		out[offset + 2] = plane.z;
		out[offset + 3] = plane.w;
	}
}
