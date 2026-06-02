package dev.historicaltextures.mixin;

import dev.historicaltextures.pack.OverlayResourcePackProvider;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(PackRepository.class)
public abstract class ResourcePackManagerMixin {
	@Shadow
	@Final
	private Set<RepositorySource> sources;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void historicalTextures$registerOverlaySource(RepositorySource[] repositorySources, CallbackInfo callbackInfo) {
		sources.add(new OverlayResourcePackProvider());
	}
}
