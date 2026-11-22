package dev.historicaltextures.mixin;

import dev.historicaltextures.pack.OverlayResourcePackProvider;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Arrays;

@Mixin(PackRepository.class)
public abstract class ResourcePackManagerMixin {
	@ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
	private static RepositorySource[] historicalTextures$registerOverlaySource(RepositorySource[] repositorySources) {
		RepositorySource[] modified = Arrays.copyOf(repositorySources, repositorySources.length + 1);
		modified[repositorySources.length] = new OverlayResourcePackProvider();
		return modified;
	}
}
