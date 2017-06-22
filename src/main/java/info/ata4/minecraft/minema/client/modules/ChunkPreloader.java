package info.ata4.minecraft.minema.client.modules;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

import info.ata4.minecraft.minema.client.config.MinemaConfig;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

public class ChunkPreloader extends CaptureModule {

	private Field renderInfosField;
	private Field renderDispatcherField;
	private Field renderChunkField;
	private Field renderViewFrustum;

	public ChunkPreloader(MinemaConfig cfg) {
		super(cfg);
	}

	@SuppressWarnings("rawtypes")
	@SubscribeEvent
	public void onTick(RenderTickEvent evt) {
		if (evt.phase != Phase.START) {
			return;
		}

		try {
			Iterator iterator = ((List) renderInfosField.get(MC.renderGlobal)).iterator();
			ChunkRenderDispatcher renderDispatcher = (ChunkRenderDispatcher) renderDispatcherField.get(MC.renderGlobal);

			// 250ms timeout
			renderDispatcher.runChunkUploads(System.nanoTime() + 250000000L);

			while (iterator.hasNext()) {
				Object renderInfo = iterator.next();
				RenderChunk rc = (RenderChunk) renderChunkField.get(renderInfo);

				if (rc.needsUpdate()) {
					renderDispatcher.updateChunkNow(rc);
					rc.clearNeedsUpdate();
				}
			}
		} catch (Exception e) {
			handleWarning(e, "Could not force chunk updates, disable the module and report the error!");
		}
	}

	@Override
	protected void doEnable() throws Exception {
		renderInfosField = RenderGlobal.class.getDeclaredField("field_72755_R");
		renderInfosField.setAccessible(true);
		renderDispatcherField = RenderGlobal.class.getDeclaredField("field_174995_M");
		renderDispatcherField.setAccessible(true);
		renderViewFrustum = RenderGlobal.class.getDeclaredField("field_175008_n");
		renderViewFrustum.setAccessible(true);

		for (Class<?> innerClass : RenderGlobal.class.getDeclaredClasses()) {
			if (innerClass.getName().endsWith("ContainerLocalRenderInformation")) {
				renderChunkField = innerClass.getDeclaredField("field_178036_a");
				renderChunkField.setAccessible(true);
				break;
			}
		}

		if (renderInfosField == null || renderDispatcherField == null || renderChunkField == null)
			return;

		if (cfg.forcePreloadChunks.get() && renderViewFrustum != null) {
			try {
				ChunkRenderDispatcher chunks = (ChunkRenderDispatcher) renderDispatcherField.get(MC.renderGlobal);
				ViewFrustum frustum = (ViewFrustum) renderViewFrustum.get(MC.renderGlobal);

				for (RenderChunk chunk : frustum.renderChunks) {
					if (chunk.getCompiledChunk() == CompiledChunk.DUMMY) {
						chunks.updateChunkNow(chunk);
					}
				}
			} catch (Exception e) {
				handleWarning(e,
						"Could not preload all chunks in render distance on enable, disable the config option and report the error!");
			}
		}

		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	protected void doDisable() {
		MinecraftForge.EVENT_BUS.unregister(this);
	}

}